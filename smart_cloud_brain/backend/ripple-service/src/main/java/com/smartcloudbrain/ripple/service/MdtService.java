package com.smartcloudbrain.ripple.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcloudbrain.ripple.dto.DrugItem;
import com.smartcloudbrain.ripple.dto.MdtConsultRequest;
import com.smartcloudbrain.ripple.entity.MdtConsultation;
import com.smartcloudbrain.ripple.event.RippleEventPublisher;
import com.smartcloudbrain.ripple.repository.MdtConsultationRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多智能体 MDT 会诊服务（核心创新3：模拟真实医疗多学科会诊文化）。
 *
 * 五 Agent 各自从专业视角主动发言（区别于简单"交班"式上下文传递）：
 * - 分诊Agent：紧急度/科室归属/危险症状排除
 * - 处方Agent：药物冲突/肝肾功剂量调整
 * - 病历Agent：既往史结构化要点
 * - 随访Agent：出院后监测要点
 * - 涟漪守护Agent：新处方与既往用药的涟漪影响
 */
@Service
public class MdtService {

  private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final KnowledgeBaseService knowledgeBase;
  private final CounterfactualService counterfactualService;
  private final CounterfactualGuardrail counterfactualGuardrail;
  private final EvidenceChainService evidenceChainService;
  private final MdtConsultationRepository consultationRepository;
  private final RippleEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public MdtService(
      KnowledgeBaseService knowledgeBase,
      CounterfactualService counterfactualService,
      CounterfactualGuardrail counterfactualGuardrail,
      EvidenceChainService evidenceChainService,
      MdtConsultationRepository consultationRepository,
      RippleEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.knowledgeBase = knowledgeBase;
    this.counterfactualService = counterfactualService;
    this.counterfactualGuardrail = counterfactualGuardrail;
    this.evidenceChainService = evidenceChainService;
    this.consultationRepository = consultationRepository;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Map<String, Object> consult(MdtConsultRequest request) {
    List<String> pastList = RippleDeriveService.splitHistory(request.pastHistory());
    List<String> drugNames = RippleDeriveService.normalizeDrugs(request.drugs());
    String complaint = request.chiefComplaint() == null ? "" : request.chiefComplaint().trim();
    LocalDateTime now = LocalDateTime.now();
    String mdtId = "MDT-" + now.format(ID_FORMAT);

    // 五 Agent 视角
    Map<String, Object> triageView = triageAgentView(complaint);
    Map<String, Object> prescriptionView = prescriptionAgentView(pastList, drugNames);
    Map<String, Object> recordView = recordAgentView(pastList);
    Map<String, Object> followupView = followupAgentView(pastList);
    Map<String, Object> rippleView = rippleAgentView(drugNames);

    Map<String, Object> consultation = new LinkedHashMap<>();
    consultation.put("triageView", triageView);
    consultation.put("prescriptionView", prescriptionView);
    consultation.put("recordView", recordView);
    consultation.put("followupView", followupView);
    consultation.put("rippleView", rippleView);

    List<String> consensusNotes = buildConsensusNotes(triageView, prescriptionView, rippleView);

    // 反事实决策树 + 护栏安全审计
    Map<String, Object> counterfactualTree = counterfactualGuardrail.audit(
        counterfactualService.buildForMdt(complaint, pastList));

    // 落库
    MdtConsultation entity = new MdtConsultation();
    entity.setMdtId(mdtId);
    entity.setPatientId(request.patientId());
    entity.setChiefComplaint(complaint);
    entity.setPastHistoryJson(toJson(pastList));
    entity.setDiagnosis(request.diagnosis() == null ? "" : request.diagnosis());
    entity.setDrugsJson(toJson(drugNames));
    entity.setConsultationJson(toJson(consultation));
    entity.setConsensusJson(toJson(consensusNotes));
    entity.setAgentCount(5);
    MdtConsultation saved = consultationRepository.save(entity);

    // 哈希链证据
    Map<String, Object> evidence = evidenceChainService.append(
        "MDT_CONSULTATION",
        request.patientId(),
        Map.of(
            "chiefComplaint", complaint,
            "pastHistory", String.join(",", pastList),
            "trigger", "mdt_consultation_request"),
        List.of("五Agent多视角发言", "聚合形成MDT会诊纪要"),
        Map.of("mdtId", mdtId, "agentCount", 5),
        counterfactualTree,
        0.90,
        "MDT_CONSULTED",
        "health-ripple-agent");

    // 领域事件（通知服务触达）
    eventPublisher.publishMdtConsulted(mdtId, request.patientId(), complaint, 5);

    // 响应（契约：consultation 顶层键 + mdtId/agentCount/consensusNotes）
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("mdtId", saved.getMdtId());
    result.put("timestamp", now.toString());
    result.put("caseSummary", Map.of(
        "chiefComplaint", complaint,
        "pastHistory", pastList,
        "diagnosis", request.diagnosis() == null ? "" : request.diagnosis(),
        "drugs", drugNames));
    result.put("consultation", consultation);
    result.put("consensusNotes", consensusNotes);
    result.put("counterfactualTree", counterfactualTree);
    result.put("agentCount", 5);
    result.put("evidenceChain", evidence);
    return result;
  }

  public List<Map<String, Object>> history(Long patientId) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (MdtConsultation entity : consultationRepository.findByPatientIdOrderByCreatedAtDesc(patientId)) {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("mdtId", entity.getMdtId());
      view.put("patientId", entity.getPatientId());
      view.put("chiefComplaint", entity.getChiefComplaint());
      view.put("consultation", fromJson(entity.getConsultationJson()));
      view.put("consensusNotes", fromJson(entity.getConsensusJson()));
      view.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
      result.add(view);
    }
    return result;
  }

  /* ================= 五 Agent 视角 ================= */

  private Map<String, Object> triageAgentView(String complaint) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("agent", "medical-triage-agent");
    view.put("perspective", "分诊与紧急度");
    boolean dangerous = List.of("胸痛", "胸闷", "呼吸困难", "意识不清", "晕厥", "大汗")
        .stream().anyMatch(complaint::contains);
    view.put("urgencySuggestion", dangerous ? "HIGH-紧急度建议急诊排除心梗/卒中/主动脉夹层" : "MEDIUM-需进一步评估");
    view.put("analysis", dangerous
        ? "主诉含危险症状，紧急度HIGH，需排除急症"
        : "主诉未含明确危险症状，紧急度MEDIUM，需结合既往史评估");
    return view;
  }

  private Map<String, Object> prescriptionAgentView(List<String> pastList, List<String> drugNames) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("agent", "prescription-safety-agent");
    view.put("perspective", "药物安全与肝肾功");
    List<String> notes = new ArrayList<>();
    if (pastList.stream().anyMatch(h -> h.contains("慢性肾病"))) {
      notes.add("NSAIDs（布洛芬等）与慢性肾病冲突，避免使用");
    }
    if (pastList.stream().anyMatch(h -> h.contains("糖尿病"))
        && drugNames.toString().contains("二甲双胍")) {
      notes.add("二甲双胍在CKD患者中需根据eGFR调整剂量");
    }
    if (pastList.stream().anyMatch(h -> h.contains("高血压"))) {
      notes.add("ACEI/ARB初始需监测肾功能与血钾");
    }
    // 结合药物-生活冲突知识库
    for (String drug : drugNames) {
      for (var rule : knowledgeBase.findDrugConflicts(drug)) {
        if ("HIGH".equals(rule.getSeverity())) {
          notes.add(drug + " + " + rule.getConflictItem() + " → " + rule.getRiskDescription());
        }
      }
    }
    view.put("drugRiskNotes", notes);
    view.put("analysis", "基于既往史" + pastList + "与处方" + drugNames + "的药物安全评估");
    return view;
  }

  private Map<String, Object> recordAgentView(List<String> pastList) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("agent", "medical-record-agent");
    view.put("perspective", "既往史结构化");
    view.put("historyNotes", pastList);
    view.put("analysis", pastList.isEmpty()
        ? "既往史要点：无明确既往史"
        : "既往史要点：" + String.join("；", pastList));
    return view;
  }

  private Map<String, Object> followupAgentView(List<String> pastList) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("agent", "followup-plan-agent");
    view.put("perspective", "随访与监测要点");
    List<String> points = new ArrayList<>();
    if (pastList.stream().anyMatch(h -> h.contains("糖尿病"))) {
      points.addAll(List.of("监测血糖", "足部检查", "眼底筛查"));
    }
    if (pastList.stream().anyMatch(h -> h.contains("高血压"))) {
      points.addAll(List.of("监测血压", "心电图"));
    }
    if (pastList.stream().anyMatch(h -> h.contains("慢性肾病"))) {
      points.addAll(List.of("监测出入量", "体重", "电解质"));
    }
    view.put("monitoringPoints", points);
    view.put("analysis", points.isEmpty()
        ? "基于既往史的随访监测要点：常规随访"
        : "基于既往史的随访监测要点：" + String.join("；", points));
    return view;
  }

  private Map<String, Object> rippleAgentView(List<String> drugNames) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("agent", "health-ripple-agent");
    view.put("perspective", "新处方涟漪影响");
    List<String> notes = new ArrayList<>();
    for (String drug : drugNames) {
      for (var rule : knowledgeBase.findDrugConflicts(drug)) {
        if ("HIGH".equals(rule.getSeverity())) {
          notes.add(drug + "+" + rule.getConflictItem() + "→" + rule.getRiskDescription());
        }
      }
    }
    view.put("rippleNotes", notes);
    view.put("analysis", notes.isEmpty()
        ? "新处方涟漪影响：未识别高风险涟漪"
        : "新处方涟漪影响：" + String.join("；", notes));
    return view;
  }

  private List<String> buildConsensusNotes(Map<String, Object> triageView,
                                           Map<String, Object> prescriptionView,
                                           Map<String, Object> rippleView) {
    List<String> notes = new ArrayList<>();
    if (String.valueOf(triageView.get("urgencySuggestion")).startsWith("HIGH")) {
      notes.add("紧急共识：紧急度HIGH，优先排除心梗/卒中/主动脉夹层等急症");
    }
    List<?> drugNotes = (List<?>) prescriptionView.get("drugRiskNotes");
    List<?> rippleNotes = (List<?>) rippleView.get("rippleNotes");
    if (drugNotes != null && !drugNotes.isEmpty()) {
      notes.add("风险预警：重点关注处方Agent识别的" + drugNotes.size() + "项药物风险");
    }
    if (rippleNotes != null && !rippleNotes.isEmpty()) {
      notes.add("涟漪预警：涟漪守护Agent识别" + rippleNotes.size() + "项高风险涟漪，需患者教育");
    }
    notes.add("多学科建议：结合各Agent视角形成综合诊疗方案");
    notes.add("随访要点：按随访Agent监测点执行长期管理");
    return notes;
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
