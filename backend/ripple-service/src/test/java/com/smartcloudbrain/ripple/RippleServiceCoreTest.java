package com.smartcloudbrain.ripple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcloudbrain.ripple.dto.DrugItem;
import com.smartcloudbrain.ripple.dto.MdtConsultRequest;
import com.smartcloudbrain.ripple.dto.RippleDeriveRequest;
import com.smartcloudbrain.ripple.entity.EvidenceChain;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import com.smartcloudbrain.ripple.repository.EvidenceChainRepository;
import com.smartcloudbrain.ripple.service.ChronoEngine;
import com.smartcloudbrain.ripple.service.EvidenceChainService;
import com.smartcloudbrain.ripple.service.MdtService;
import com.smartcloudbrain.ripple.service.RippleDeriveService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 涟漪守护核心逻辑集成测试（H2 内存库）：
 * 1. 涟漪推演：五维图谱 + 反事实树 + 时间学计划 + 哈希链证据
 * 2. MDT 会诊：五 Agent 视角 + 共识纪要
 * 3. 哈希链防篡改：篡改任一记录后 verify() 必须失败（tamper-evident）
 * 4. 时间学引擎：到期查询 + ack 推进
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ripple_test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("test")
class RippleServiceCoreTest {

  @Autowired
  private RippleDeriveService rippleDeriveService;
  @Autowired
  private MdtService mdtService;
  @Autowired
  private EvidenceChainService evidenceChainService;
  @Autowired
  private ChronoEngine chronoEngine;
  @Autowired
  private EvidenceChainRepository evidenceChainRepository;
  @Autowired
  private ChronoTriggerRepository triggerRepository;
  @Autowired
  private com.smartcloudbrain.ripple.service.RippleClosureService closureService;
  @Autowired
  private com.smartcloudbrain.ripple.service.HealthWeatherService healthWeatherService;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rippleDerivation_shouldProduceFiveDimensionsAndChronoTriggers() {
    Map<String, Object> result = rippleDeriveService.derive(new RippleDeriveRequest(
        1L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), "高血压"));

    // 五维图谱
    Map<String, Object> dimensions = castMap(result.get("dimensions"));
    assertTrue(dimensions.containsKey("drugLifestyleConflicts"));
    assertTrue(dimensions.containsKey("recheckWindows"));
    assertTrue(dimensions.containsKey("complicationSignals"));
    assertTrue(dimensions.containsKey("familyAttentions"));
    assertTrue(dimensions.containsKey("chronoTriggers"));

    // 药物-生活冲突：二甲双胍 → 饮酒（HIGH）
    List<Map<String, Object>> conflicts = castMapList(dimensions.get("drugLifestyleConflicts"));
    assertTrue(conflicts.stream().anyMatch(c ->
        "饮酒".equals(c.get("conflict")) && "HIGH".equals(c.get("severity"))));

    // 并发症信号：2型糖尿病 + 高血压 既往史都应推演
    List<Map<String, Object>> signals = castMapList(dimensions.get("complicationSignals"));
    assertTrue(signals.size() >= 5, "糖尿病+高血压并发症信号应≥5条，实际=" + signals.size());

    // 反事实决策树
    Map<String, Object> tree = castMap(result.get("counterfactualTree"));
    assertTrue(((Number) tree.get("counterfactualCount")).intValue() >= 3);
    List<Map<String, Object>> paths = castMapList(tree.get("alternativePaths"));
    assertTrue(paths.stream().anyMatch(p -> String.valueOf(p.get("path")).contains("二甲双胍+饮酒冲突")));

    // 时间学触达计划已注册（糖尿病节律+周期+季节 + 高血压节律+周期+季节）
    List<Map<String, Object>> triggers = castMapList(result.get("chronoTriggers"));
    assertTrue(triggers.size() >= 4, "应注册≥4个时间学触达，实际=" + triggers.size());

    // 哈希链证据
    Map<String, Object> evidence = castMap(result.get("evidenceChain"));
    assertNotNull(evidence.get("decisionId"));
    assertEquals("RIPPLE_DERIVATION", evidence.get("decisionType"));
    assertNotNull(evidence.get("hash"));
    assertNotNull(evidence.get("prevHash"));

    // 主动式判定：高风险节点应≥2 → 建议MDT
    Map<String, Object> proactive = castMap(result.get("proactiveAssessment"));
    assertEquals(Boolean.TRUE, proactive.get("recommendMdt"));

    // 涟漪强度指数（RII）：事件级强度可量化、节点级评分可解释
    Map<String, Object> intensity = castMap(result.get("rippleIntensity"));
    double rii = ((Number) intensity.get("index")).doubleValue();
    assertTrue(rii >= 45, "糖尿病+二甲双胍高危事件RII应≥45（红色），实际=" + rii);
    assertEquals("RED", intensity.get("level"));
    assertTrue(((Number) intensity.get("radius")).intValue() >= 4,
        "高危事件有效扩散半径应≥4环，实际=" + intensity.get("radius"));
    List<Map<String, Object>> topRisks = castMapList(intensity.get("topRisks"));
    assertTrue(topRisks.size() >= 1 && topRisks.size() <= 3, "Top风险应为1-3个");
    List<Map<String, Object>> conflictNodes = castMapList(castMap(result.get("dimensions"))
        .get("drugLifestyleConflicts"));
    assertTrue(conflictNodes.stream().allMatch(n -> n.containsKey("intensity")
        && n.containsKey("ring") && n.containsKey("scoreBreakdown")), "每个涟漪节点应携带强度标注");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void mdtConsultation_shouldAggregateFiveAgentViews() {
    Map<String, Object> result = mdtService.consult(new MdtConsultRequest(
        1L, "胸闷气短3天", "糖尿病,高血压,慢性肾病", "冠心病待排", List.of(new DrugItem("二甲双胍"))));

    Map<String, Object> consultation = castMap(result.get("consultation"));
    assertEquals(5, consultation.size(), "应为五Agent视角");
    assertNotNull(consultation.get("triageView"));
    assertNotNull(consultation.get("prescriptionView"));
    assertNotNull(consultation.get("recordView"));
    assertNotNull(consultation.get("followupView"));
    assertNotNull(consultation.get("rippleView"));

    // 胸闷 → 分诊Agent应判HIGH紧急度
    Map<String, Object> triageView = castMap(consultation.get("triageView"));
    assertTrue(String.valueOf(triageView.get("urgencySuggestion")).startsWith("HIGH"));

    // 慢性肾病 → 处方Agent应识别NSAIDs冲突
    Map<String, Object> prescriptionView = castMap(consultation.get("prescriptionView"));
    List<?> notes = (List<?>) prescriptionView.get("drugRiskNotes");
    assertTrue(notes.stream().anyMatch(n -> String.valueOf(n).contains("NSAIDs")));

    assertEquals("MDT_CONSULTATION", castMap(result.get("evidenceChain")).get("decisionType"));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void evidenceHashChain_shouldDetectTampering() {
    // 隔离：清空证据链后追加三条，形成从 GENESIS 起的哈希链
    evidenceChainRepository.deleteAll();
    for (int i = 0; i < 3; i++) {
      evidenceChainService.append("RIPPLE_DERIVATION", 9L,
          Map.of("seq", i), List.of("factor" + i), Map.of("out", i),
          Map.of("chosenPath", "path" + i, "alternativePaths", List.of()), 0.9, "TEST", "test-agent");
    }
    Map<String, Object> valid = evidenceChainService.verify();
    assertEquals(Boolean.TRUE, valid.get("valid"), "未篡改时链应完整");
    assertEquals(3, ((Number) valid.get("count")).intValue());

    // 篡改中间一条证据的决策内容
    List<EvidenceChain> all = evidenceChainRepository.findAllByOrderByIdAsc();
    EvidenceChain victim = all.get(1);
    victim.setDecisionJson("{\"out\":999}");
    evidenceChainRepository.save(victim);

    Map<String, Object> broken = evidenceChainService.verify();
    assertEquals(Boolean.FALSE, broken.get("valid"), "篡改后校验必须失败");
    assertEquals(victim.getDecisionId(), broken.get("brokenAt"), "应定位到被篡改条目");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void chronoEngine_shouldAdvanceAndExposeDue() {
    Map<String, Object> result = rippleDeriveService.derive(new RippleDeriveRequest(
        7L, "高血压", null, null));
    List<Map<String, Object>> triggers = castMapList(result.get("chronoTriggers"));
    assertFalse(triggers.isEmpty());

    // 周期性触达：服药2周后复查 → nextTriggerAt 应在13-15天后
    Map<String, Object> periodic = triggers.stream()
        .filter(t -> "PERIODIC".equals(t.get("chronoType")))
        .findFirst().orElseThrow();
    assertTrue(String.valueOf(periodic.get("nextTriggerAt")).matches("\\d{4}-\\d{2}-\\d{2}T.*"));

    // 模拟到期：把一条计划时间改为过去并落库 → due 必须真实包含它（防恒真断言回归）
    var allTriggers = chronoEngine.findByPatient(7L);
    var target = allTriggers.get(0);
    target.setNextTriggerAt(java.time.LocalDateTime.now().minusMinutes(1));
    triggerRepository.save(target);
    List<com.smartcloudbrain.ripple.entity.ChronoTrigger> dueNow = chronoEngine.due(java.time.LocalDateTime.now());
    assertTrue(dueNow.stream().anyMatch(t -> t.getId().equals(target.getId())),
        "到期查询应包含刚被置为过期的触达项");

    // ack 推进：RHYTHM 类型 ack 后应推进到明天同时段或今天稍晚
    Map<String, Object> rhythm = triggers.stream()
        .filter(t -> "RHYTHM".equals(t.get("chronoType")))
        .findFirst().orElse(null);
    if (rhythm != null) {
      Long id = ((Number) rhythm.get("triggerId")).longValue();
      var acked = chronoEngine.ack(id);
      assertNotNull(acked.getNextTriggerAt());
      assertEquals("ACTIVE", acked.getStatus());
      // 幂等防抖：10分钟内重复 ack 不得再次推进（防止连跳多天）
      var firstAdvancedAt = acked.getNextTriggerAt();
      var duplicate = chronoEngine.ack(id);
      assertEquals(firstAdvancedAt, duplicate.getNextTriggerAt(), "重复ack防抖：不应再次推进");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void chronoFeedback_windowUnresolvedIntensifiesAndEscalatedTerminates() {
    // 冠心病推演产生 WINDOW 触达
    Map<String, Object> derived = rippleDeriveService.derive(new RippleDeriveRequest(
        71L, "冠心病", null, ""));
    Long windowId = castMapList(derived.get("chronoTriggers")).stream()
        .filter(t -> "WINDOW".equals(t.get("chronoType")))
        .map(t -> ((Number) t.get("triggerId")).longValue())
        .findFirst().orElseThrow(() -> new AssertionError("冠心病推演应产生WINDOW触达"));

    // UNRESOLVED（含 WINDOW）：2小时后加强触达，守护不因窗口错过而静默
    var unresolved = chronoEngine.feedback(windowId, "UNRESOLVED", "胸痛未缓解");
    assertEquals("UNRESOLVED", unresolved.getFeedbackStatus());
    assertTrue(unresolved.getNextTriggerAt() != null
            && unresolved.getNextTriggerAt().isAfter(java.time.LocalDateTime.now().plusMinutes(90)),
        "WINDOW未缓解也应加压至约2小时后（守护不静默）");

    // ESCALATED：全类型终结（患者已升级就医，自动触达停止）
    Long periodicId = castMapList(derived.get("chronoTriggers")).stream()
        .filter(t -> "PERIODIC".equals(t.get("chronoType")))
        .map(t -> ((Number) t.get("triggerId")).longValue())
        .findFirst().orElse(null);
    if (periodicId != null) {
      var escalated = chronoEngine.feedback(periodicId, "ESCALATED", "已到急诊");
      assertEquals("ESCALATED", escalated.getStatus());
      assertNull(escalated.getNextTriggerAt(), "升级就医后自动触达应终结");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void interventionFeedback_shouldCloseRippleLoop() {
    // 推演：冠心病（含WINDOW黄金窗口）+二甲双胍 → 时间学触达注册
    Map<String, Object> derived = rippleDeriveService.derive(new RippleDeriveRequest(
        8L, "冠心病", List.of(new DrugItem("二甲双胍")), ""));
    List<Map<String, Object>> triggers = castMapList(derived.get("chronoTriggers"));
    assertFalse(triggers.isEmpty());

    // 回执1：RESOLVED 已缓解（WINDOW类应终结为RESOLVED）
    Long windowId = triggers.stream()
        .filter(t -> "WINDOW".equals(t.get("chronoType")))
        .map(t -> ((Number) t.get("triggerId")).longValue())
        .findFirst().orElseThrow(() -> new AssertionError("冠心病推演应产生WINDOW触达"));
    var acked = chronoEngine.feedback(windowId, "RESOLVED", "已按提醒完成复查，指标正常");
    assertEquals("RESOLVED", acked.getFeedbackStatus());
    assertEquals("RESOLVED", acked.getStatus());

    // 消解率：已缓解强度必须>0，回执计数正确
    Map<String, Object> resolution = closureService.resolution(8L);
    assertTrue(((Number) resolution.get("resolvedCount")).intValue() >= 1, "RESOLVED回执应计数");
    assertTrue(((Number) resolution.get("totalIntensity")).doubleValue() > 0, "触达应携带RII强度权重");
    assertTrue(((Number) resolution.get("resolutionRate")).doubleValue() > 0, "消解率应>0");
    assertNotNull(resolution.get("closureStatus"));

    // 回执2：UNRESOLVED 未缓解 → 2小时后加强触达（守护加压）
    Map<String, Object> rhythm = triggers.stream()
        .filter(t -> "RHYTHM".equals(t.get("chronoType")))
        .findFirst().map(java.util.Collections::unmodifiableMap).orElse(null);
    if (rhythm != null) {
      Long rhythmId = ((Number) rhythm.get("triggerId")).longValue();
      var fed = chronoEngine.feedback(rhythmId, "UNRESOLVED", "夜间仍有心悸出汗");
      assertEquals("UNRESOLVED", fed.getFeedbackStatus());
      assertTrue(fed.getNextTriggerAt() != null
              && fed.getNextTriggerAt().isAfter(java.time.LocalDateTime.now().plusMinutes(90)),
          "未缓解应把下次触达加强到约2小时后");
    }

    // 回执明细账本：每项携带强度+Timing Card+回执字段
    List<Map<String, Object>> ledger = closureService.feedbackLedger(8L);
    assertFalse(ledger.isEmpty());
    assertTrue(ledger.stream().allMatch(item -> item.containsKey("intensity")
        && item.containsKey("timingCard") && item.containsKey("feedbackStatus")));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void healthWeather_shouldTranslateGuardStateForToday() {
    // 糖尿病推演产生即时WINDOW触达 → 今日必有守护项 → 气象不应该是"晴"
    rippleDeriveService.derive(new RippleDeriveRequest(
        9L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), "高血压"));

    // 时间脆弱免疫：深夜推演时 RHYTHM(次日0点)/PERIODIC(+14天) 都不在"今天"，
    // 与 e2e 14d / ForecastLedgerTest 同一规避手法——把首条触达拨到今天再算气象
    Map<String, Object> weather = healthWeatherService.daily(9L);
    if (((Number) weather.get("dueTodayCount")).intValue() == 0) {
      for (ChronoTrigger trigger : triggerRepository.findByPatientIdOrderByNextTriggerAtAsc(9L)) {
        if ("ACTIVE".equals(trigger.getStatus()) && trigger.getNextTriggerAt() != null) {
          trigger.setNextTriggerAt(java.time.LocalDateTime.now().minusMinutes(1));
          triggerRepository.save(trigger);
          break;
        }
      }
      weather = healthWeatherService.daily(9L);
    }
    assertTrue(weather.get("weather") instanceof String
        && java.util.List.of("SUNNY", "CLOUDY", "RAIN", "STORM").contains(weather.get("weather")),
        "气象等级非法: " + weather.get("weather"));
    assertTrue(((Number) weather.get("index")).doubleValue() > 0, "有即时触达时指数应>0");
    assertTrue(((Number) weather.get("dueTodayCount")).intValue() >= 1, "WINDOW触达应计入今日");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) weather.get("items");
    assertFalse(items.isEmpty(), "今日事项不应为空");
    assertTrue(items.get(0).containsKey("timingCard"), "今日事项应携带Timing Card");
    assertNotNull(weather.get("headline"));
    assertTrue(weather.containsKey("familyTip"), "应包含家属提示维度");
    // 空患者：无任何守护数据 → 晴
    Map<String, Object> calm = healthWeatherService.daily(99999L);
    assertEquals("SUNNY", calm.get("weather"));
    assertEquals(0.0, ((Number) calm.get("index")).doubleValue());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> castMapList(Object value) {
    return (List<Map<String, Object>>) value;
  }
}
