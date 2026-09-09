package com.smartcloudbrain.ripple.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.ripple.dto.DrugItem;
import com.smartcloudbrain.ripple.dto.RippleDeriveRequest;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.entity.RippleEvent;
import com.smartcloudbrain.ripple.event.RippleEventPublisher;
import com.smartcloudbrain.ripple.repository.RippleEventRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 健康事件涟漪推演服务（核心创新1：Health Event Ripple Effect）。
 *
 * 一个健康事件（新诊断/新处方/异常指标）触发多维度连锁影响推演：
 * 1. 药物-生活冲突（服二甲双胍禁酒/华法林禁柚子/四环素避晒）
 * 2. 复查窗口（何时查什么，医疗时间学·周期性）
 * 3. 并发症早期信号（出现什么症状立即就医）
 * 4. 家属注意事项
 * 5. 时间学触达（窗口期/节律/周期/季节 → ChronoEngine 计算下次触达时间）
 *
 * 每次推演：图谱落库 + 哈希链证据（含反事实决策树）+ 时间学计划注册 + ripple.derived 事件发布。
 */
@Service
public class RippleDeriveService {

  private final KnowledgeBaseService knowledgeBase;
  private final CounterfactualService counterfactualService;
  private final CounterfactualGuardrail counterfactualGuardrail;
  private final ChronoEngine chronoEngine;
  private final EvidenceChainService evidenceChainService;
  private final RippleEventRepository rippleEventRepository;
  private final RippleEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public RippleDeriveService(
      KnowledgeBaseService knowledgeBase,
      CounterfactualService counterfactualService,
      CounterfactualGuardrail counterfactualGuardrail,
      ChronoEngine chronoEngine,
      EvidenceChainService evidenceChainService,
      RippleEventRepository rippleEventRepository,
      RippleEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.knowledgeBase = knowledgeBase;
    this.counterfactualService = counterfactualService;
    this.counterfactualGuardrail = counterfactualGuardrail;
    this.chronoEngine = chronoEngine;
    this.evidenceChainService = evidenceChainService;
    this.rippleEventRepository = rippleEventRepository;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Map<String, Object> derive(RippleDeriveRequest request) {
    String diagnosis = request.diagnosis() == null ? "" : request.diagnosis().trim();
    List<String> drugNames = normalizeDrugs(request.drugs());
    if (diagnosis.isEmpty() && drugNames.isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    List<String> pastList = splitHistory(request.pastHistory());
    LocalDateTime now = LocalDateTime.now();

    // Step1: 五维涟漪图谱
    Map<String, Object> graph = buildRippleGraph(diagnosis, drugNames, pastList);

    // Step2: 反事实决策树 + 护栏安全审计（FLAGGED路径禁止作为建议下发）
    Map<String, Object> counterfactualTree = counterfactualGuardrail.audit(
        counterfactualService.buildForRipple(graph));

    // Step3: 涟漪事件落库
    RippleEvent event = new RippleEvent();
    event.setPatientId(request.patientId());
    event.setDiagnosis(diagnosis);
    event.setDrugsJson(toJson(drugNames));
    event.setPastHistory(String.join(",", pastList));
    event.setRippleGraphJson(toJson(graph));
    Map<String, Object> summary = CounterfactualService.castMap(graph.get("summary"));
    event.setTotalNodes(asInt(summary.get("totalNodes")));
    event.setHighRiskCount(asInt(summary.get("highRiskCount")));
    RippleEvent saved = rippleEventRepository.save(event);

    // Step4: 注册时间学触达计划（主动式守护）
    List<Map<String, Object>> chronoTriggerViews = new ArrayList<>();
    for (String dx : diagnosesOf(diagnosis, pastList)) {
      for (var rule : knowledgeBase.findChronoRules(dx)) {
        ChronoTrigger trigger = chronoEngine.schedule(rule, request.patientId(), saved.getId(), now);
        chronoTriggerViews.add(triggerView(trigger));
      }
    }

    // Step5: 主动式判定（高风险节点≥2 → 主动建议MDT会诊）
    int highRiskCount = asInt(summary.get("highRiskCount"));
    boolean proactiveMdt = highRiskCount >= 2;

    // 护栏审计结论（随决策入哈希链，可审计）
    Map<String, Object> guardrailSummary = CounterfactualService.castMap(
        counterfactualTree.get("guardrailSummary"));

    // Step6: 哈希链证据（含反事实决策树）——审计基石
    Map<String, Object> evidence = evidenceChainService.append(
        "RIPPLE_DERIVATION",
        request.patientId(),
        Map.of(
            "diagnosis", diagnosis,
            "drugs", drugNames,
            "pastHistory", String.join(",", pastList),
            "trigger", "health_event"),
        List.of(
            "推演5维度涟漪影响",
            "高风险节点数: " + highRiskCount,
            "主动建议MDT: " + proactiveMdt,
            "反事实护栏: 审计" + guardrailSummary.get("auditedPaths") + "条路径, "
                + guardrailSummary.get("flaggedPaths") + "条FLAGGED禁止下发"),
        Map.of(
            "rippleSummary", summary,
            "dimensions", List.copyOf(CounterfactualService.castMap(graph.get("dimensions")).keySet())),
        counterfactualTree,
        0.88,
        chronoTriggerViews.isEmpty() ? "RIPPLE_DERIVED" : "RIPPLE_DERIVED+CHRONO_TRIGGER_SETUP",
        "health-ripple-agent");

    // Step7: 发布 ripple.derived 领域事件（事件驱动涟漪闭环：通知服务自动触达）
    eventPublisher.publishRippleDerived(saved.getId(), request.patientId(), diagnosis, highRiskCount,
        asInt(summary.get("totalNodes")));

    // Step8: 组装响应（dimensions 顶层字段保证 Skill 端契约兼容）
    Map<String, Object> result = new LinkedHashMap<>(graph);
    result.put("rippleEventId", saved.getId());
    result.put("counterfactualTree", counterfactualTree);
    result.put("chronoTriggers", chronoTriggerViews);
    result.put("proactiveAssessment", Map.of(
        "isProactive", true,
        "proactiveAction", proactiveMdt ? "PROACTIVE_MDT_SUGGEST" : "PROACTIVE_CHRONO_TRIGGER_SETUP",
        "reason", proactiveMdt
            ? "识别" + highRiskCount + "个高风险节点，主动建议MDT会诊"
            : "主动设置医疗时间学触达计划",
        "recommendMdt", proactiveMdt));
    result.put("evidenceChain", evidence);
    return result;
  }

  public List<Map<String, Object>> history(Long patientId) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (RippleEvent event : rippleEventRepository.findByPatientIdOrderByCreatedAtDesc(patientId)) {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("rippleEventId", event.getId());
      view.put("patientId", event.getPatientId());
      view.put("diagnosis", event.getDiagnosis());
      view.put("drugs", fromJson(event.getDrugsJson()));
      view.put("pastHistory", event.getPastHistory());
      view.put("rippleGraph", fromJson(event.getRippleGraphJson()));
      view.put("totalNodes", event.getTotalNodes());
      view.put("highRiskCount", event.getHighRiskCount());
      view.put("createdAt", event.getCreatedAt() == null ? "" : event.getCreatedAt().toString());
      result.add(view);
    }
    return result;
  }

  /* ================= 五维图谱构建 ================= */

  private Map<String, Object> buildRippleGraph(String diagnosis, List<String> drugNames, List<String> pastList) {
    // 维度1：药物-生活冲突
    List<Map<String, Object>> drugConflicts = new ArrayList<>();
    for (String drug : drugNames) {
      for (var rule : knowledgeBase.findDrugConflicts(drug)) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("drug", drug);
        item.put("conflict", rule.getConflictItem());
        item.put("risk", rule.getRiskDescription());
        item.put("severity", rule.getSeverity());
        item.put("advice", rule.getAdvice());
        item.put("counterfactualNote", "若未识别" + rule.getConflictItem() + "冲突，患者可能发生" + rule.getRiskDescription());
        drugConflicts.add(item);
      }
    }

    List<String> diagnoses = diagnosesOf(diagnosis, pastList);

    // 维度2：复查窗口（医疗时间学·周期性）
    List<Map<String, Object>> recheckWindows = new ArrayList<>();
    for (String dx : diagnoses) {
      for (var rule : knowledgeBase.findRecheckWindows(dx)) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("diagnosis", dx);
        item.put("item", rule.getItem());
        item.put("timing", rule.getTiming());
        item.put("chronoType", rule.getChronoType());
        item.put("advice", rule.getAdvice());
        recheckWindows.add(item);
      }
    }

    // 维度3：并发症早期信号
    List<Map<String, Object>> complicationSignals = new ArrayList<>();
    for (String dx : diagnoses) {
      for (var rule : knowledgeBase.findComplicationSignals(dx)) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("diagnosis", dx);
        item.put("signal", rule.getSignalSymptom());
        item.put("complication", rule.getComplication());
        item.put("action", rule.getActionAdvice());
        item.put("urgency", rule.getUrgency());
        complicationSignals.add(item);
      }
    }

    // 维度4：家属注意事项
    List<String> familyAttentions = buildFamilyAttentions(diagnoses);

    // 维度5：时间学触达（窗口期/节律/周期/季节）
    List<Map<String, Object>> chronoTriggers = new ArrayList<>();
    for (String dx : diagnoses) {
      for (var rule : knowledgeBase.findChronoRules(dx)) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("diagnosis", dx);
        item.put("chronoType", rule.getChronoType());
        item.put("event", rule.getEvent());
        item.put("triggerTime", rule.getTriggerTime());
        item.put("action", rule.getAction());
        chronoTriggers.add(item);
      }
    }

    Map<String, Object> graph = new LinkedHashMap<>();
    graph.put("healthEvent", Map.of(
        "diagnosis", diagnosis,
        "drugs", drugNames,
        "pastHistory", String.join(",", pastList)));
    Map<String, Object> dimensions = new LinkedHashMap<>();
    dimensions.put("drugLifestyleConflicts", drugConflicts);
    dimensions.put("recheckWindows", recheckWindows);
    dimensions.put("complicationSignals", complicationSignals);
    dimensions.put("familyAttentions", familyAttentions);
    dimensions.put("chronoTriggers", chronoTriggers);
    graph.put("dimensions", dimensions);
    int total = drugConflicts.size() + recheckWindows.size() + complicationSignals.size()
        + familyAttentions.size() + chronoTriggers.size();
    int highRisk = (int) drugConflicts.stream().filter(c -> "HIGH".equals(c.get("severity"))).count()
        + (int) complicationSignals.stream().filter(s -> "HIGH".equals(s.get("urgency"))).count();
    graph.put("summary", Map.of("totalNodes", total, "highRiskCount", highRisk));
    return graph;
  }

  private List<String> buildFamilyAttentions(List<String> diagnoses) {
    List<String> result = new ArrayList<>();
    boolean anyDiabetes = diagnoses.stream().anyMatch(d -> d.contains("糖尿病"));
    boolean anyCardio = diagnoses.stream().anyMatch(d -> d.contains("高血压") || d.contains("冠心病"));
    boolean anyAsthma = diagnoses.stream().anyMatch(d -> d.contains("哮喘"));
    if (anyDiabetes) {
      result.add("家属需识别低血糖症状（心悸/出汗/手抖）并即时补糖");
      result.add("饮食配合：低GI饮食结构调整");
      result.add("足部护理：每日检查足部皮肤完整性");
    }
    if (anyCardio) {
      result.add("家属需识别卒中症状（肢体麻木/言语不清/面瘫），立即拨打急救");
      result.add("家属需识别心梗症状（持续胸痛>15分钟），立即急诊");
      result.add("低盐低脂饮食配合");
    }
    if (anyAsthma) {
      result.add("家属需识别哮喘持续状态（呼吸困难加重/讲话困难），立即急诊");
      result.add("避免家庭过敏原（尘螨/花粉/宠物毛发）");
    }
    return result;
  }

  /* ================= 工具 ================= */

  private List<String> diagnosesOf(String diagnosis, List<String> pastList) {
    List<String> result = new ArrayList<>();
    if (diagnosis != null && !diagnosis.isBlank()) {
      result.add(diagnosis);
    }
    result.addAll(pastList);
    return result;
  }

  static List<String> normalizeDrugs(List<DrugItem> drugs) {
    List<String> names = new ArrayList<>();
    if (drugs != null) {
      for (DrugItem drug : drugs) {
        if (drug != null && drug.drugName() != null && !drug.drugName().isBlank()) {
          names.add(drug.drugName().trim());
        }
      }
    }
    return names;
  }

  static List<String> splitHistory(String pastHistory) {
    List<String> result = new ArrayList<>();
    if (pastHistory != null && !pastHistory.isBlank()) {
      for (String item : pastHistory.split("[,，;；]")) {
        if (!item.isBlank()) {
          result.add(item.trim());
        }
      }
    }
    return result;
  }

  private Map<String, Object> triggerView(ChronoTrigger trigger) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("triggerId", trigger.getId());
    view.put("patientId", trigger.getPatientId());
    view.put("chronoType", trigger.getChronoType());
    view.put("event", trigger.getEvent());
    view.put("triggerTime", trigger.getTriggerTime());
    view.put("nextTriggerAt", trigger.getNextTriggerAt() == null ? "" : trigger.getNextTriggerAt().toString());
    view.put("status", trigger.getStatus());
    view.put("action", trigger.getAction());
    // Timing Card 循证卡片：让每次主动触达携带可审计的医学依据
    view.put("timingCard", Map.of(
        "evidenceBasis", nullToEmpty(trigger.getEvidenceBasis()),
        "missCost", nullToEmpty(trigger.getMissCost()),
        "evidenceLevel", nullToEmpty(trigger.getEvidenceLevel())));
    return view;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static int asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      return 0;
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? List.of() : value);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Object fromJson(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, Object.class);
    } catch (Exception e) {
      return List.of();
    }
  }
}
