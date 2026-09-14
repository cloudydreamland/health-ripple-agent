package com.smartcloudbrain.ripple.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 涟漪强度指数模型（RII）单元测试：
 * 1. 高危事件（糖尿病+二甲双胍）应判红色等级且涟漪波及五环
 * 2. 节点强度随环数单调衰减（近环 &gt; 远环）
 * 3. 严重度越高强度越大（单调性）
 * 4. 评分依据（S/U/A/衰减）随节点输出——评分本身可审计
 * 5. 空图谱：指数0、半径0、黄色兜底
 */
class RippleIntensityModelTest {

  private final RippleIntensityModel model = new RippleIntensityModel();

  @Test
  void highRiskEvent_shouldScoreRedAndReachFamilyRing() {
    Map<String, Object> graph = diabetesMetforminGraph();
    model.annotate(graph);

    Map<String, Object> intensity = summaryOf(graph).get("rippleIntensity") instanceof Map
        ? castMap(summaryOf(graph).get("rippleIntensity")) : Map.of();
    assertTrue(((Number) intensity.get("index")).doubleValue() >= 45,
        "糖尿病+二甲双胍高危事件RII应≥45（红色），实际=" + intensity.get("index"));
    assertEquals("RED", intensity.get("level"));
    assertEquals(5, ((Number) intensity.get("radius")).intValue(),
        "高危事件涟漪应波及至第5环（家庭影响圈）");
    List<Map<String, Object>> topRisks = castList(intensity.get("topRisks"));
    assertEquals(3, topRisks.size());
    // Top风险降序
    assertTrue(((Number) topRisks.get(0).get("intensity")).doubleValue()
        >= ((Number) topRisks.get(2).get("intensity")).doubleValue());
  }

  @Test
  void nodeIntensity_shouldDecayWithRing() {
    Map<String, Object> graph = diabetesMetforminGraph();
    model.annotate(graph);
    Map<String, Object> dimensions = castMap(graph.get("dimensions"));

    double conflictRing1 = intensityOfFirst(castList(dimensions.get("drugLifestyleConflicts")));
    double signalRing2 = intensityOfFirst(castList(dimensions.get("complicationSignals")));
    double recheckRing3 = intensityOfFirst(castList(dimensions.get("recheckWindows")));
    double familyRing5 = intensityOfFirst(castList(dimensions.get("familyAttentions")));

    assertTrue(conflictRing1 > signalRing2, "近环（用药安全圈）强度应高于疾病进展圈");
    assertTrue(signalRing2 > recheckRing3, "疾病进展圈强度应高于复查窗口圈");
    assertTrue(recheckRing3 > familyRing5, "复查窗口圈强度应高于家庭影响圈");
  }

  @Test
  void severity_shouldDriveIntensityMonotonically() {
    Map<String, Object> high = singleConflictGraph("HIGH");
    Map<String, Object> low = singleConflictGraph("LOW");
    model.annotate(high);
    model.annotate(low);

    double highIntensity = intensityOfFirst(castList(castMap(high.get("dimensions"))
        .get("drugLifestyleConflicts")));
    double lowIntensity = intensityOfFirst(castList(castMap(low.get("dimensions"))
        .get("drugLifestyleConflicts")));
    assertTrue(highIntensity > lowIntensity, "HIGH冲突强度必须高于LOW冲突");
  }

  @Test
  void scoreBreakdown_shouldBeAuditable() {
    Map<String, Object> graph = diabetesMetforminGraph();
    model.annotate(graph);
    Map<String, Object> conflict = castList(castMap(graph.get("dimensions"))
        .get("drugLifestyleConflicts")).get(0);

    assertEquals(1, ((Number) conflict.get("ring")).intValue());
    assertEquals("用药安全圈", conflict.get("ringName"));
    Map<String, Object> breakdown = castMap(conflict.get("scoreBreakdown"));
    assertEquals(0.95, ((Number) breakdown.get("severity")).doubleValue());
    assertEquals(0.9, ((Number) breakdown.get("urgency")).doubleValue());
    double decay = ((Number) breakdown.get("decay")).doubleValue();
    assertEquals(1.0, decay, 0.001, "第1环衰减系数应为1");
    // 强度可由评分依据复算：100×S×U×A×decay
    double recomputed = 100.0 * ((Number) breakdown.get("severity")).doubleValue()
        * ((Number) breakdown.get("urgency")).doubleValue()
        * ((Number) breakdown.get("actionability")).doubleValue() * decay;
    assertEquals(((Number) conflict.get("intensity")).doubleValue(), Math.round(recomputed * 10) / 10.0, 0.11);
  }

  @Test
  void emptyGraph_shouldFallbackGracefully() {
    Map<String, Object> graph = emptyGraph();
    model.annotate(graph);
    Map<String, Object> intensity = castMap(summaryOf(graph).get("rippleIntensity"));
    assertEquals(0.0, ((Number) intensity.get("index")).doubleValue());
    assertEquals(0, ((Number) intensity.get("radius")).intValue());
    assertEquals("YELLOW", intensity.get("level"));
    assertTrue(castList(intensity.get("topRisks")).isEmpty());
  }

  /* ================= 测试图谱构造 ================= */

  private Map<String, Object> diabetesMetforminGraph() {
    Map<String, Object> dimensions = new LinkedHashMap<>();
    dimensions.put("drugLifestyleConflicts", List.of(
        conflictNode("二甲双胍", "饮酒", "HIGH", "服药期间饮酒可能诱发乳酸酸中毒", "严格禁酒，含酒精饮品/藿香正气水均避免")));
    dimensions.put("complicationSignals", List.of(
        signalNode("2型糖尿病", "视力模糊", "糖尿病视网膜病变", "1周内眼科就诊查眼底", "HIGH"),
        signalNode("2型糖尿病", "心悸出汗手抖", "低血糖", "立即进食15g糖，15分钟后复测", "MEDIUM")));
    dimensions.put("recheckWindows", List.of(
        recheckNode("2型糖尿病", "肝肾功能+空腹血糖", "服药2周后复查", "PERIODIC")));
    dimensions.put("chronoTriggers", List.of(
        chronoNode("WINDOW", "心梗黄金救治窗口", "发病120分钟内", "WINDOW"),
        chronoNode("夜间低血糖节律守护", "0:00-3:00", "RHYTHM", "RHYTHM")));
    dimensions.put("familyAttentions", List.of(
        familyNode("家属需识别低血糖症状（心悸/出汗/手抖）并即时补糖",
            "随身备糖块，症状出现15分钟内口服15g糖", "HIGH")));
    return baseGraph(dimensions);
  }

  private Map<String, Object> singleConflictGraph(String severity) {
    Map<String, Object> dimensions = new LinkedHashMap<>();
    dimensions.put("drugLifestyleConflicts", List.of(
        conflictNode("华法林", "柚子", severity, "柚子抑制药物代谢，升高血药浓度", "避免食用柚子及其果汁")));
    return baseGraph(dimensions);
  }

  private Map<String, Object> emptyGraph() {
    Map<String, Object> dimensions = new LinkedHashMap<>();
    dimensions.put("drugLifestyleConflicts", List.of());
    dimensions.put("complicationSignals", List.of());
    dimensions.put("recheckWindows", List.of());
    dimensions.put("chronoTriggers", List.of());
    dimensions.put("familyAttentions", List.of());
    return baseGraph(dimensions);
  }

  private Map<String, Object> baseGraph(Map<String, Object> dimensions) {
    Map<String, Object> graph = new LinkedHashMap<>();
    graph.put("healthEvent", Map.of("diagnosis", "2型糖尿病", "drugs", List.of("二甲双胍")));
    graph.put("dimensions", dimensions);
    int total = 0;
    for (Object list : dimensions.values()) {
      total += ((List<?>) list).size();
    }
    graph.put("summary", new LinkedHashMap<>(Map.of("totalNodes", total, "highRiskCount", 2)));
    return graph;
  }

  private Map<String, Object> conflictNode(String drug, String conflict, String severity,
      String risk, String advice) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("drug", drug);
    node.put("conflict", conflict);
    node.put("risk", risk);
    node.put("severity", severity);
    node.put("advice", advice);
    return node;
  }

  private Map<String, Object> signalNode(String diagnosis, String signal, String complication,
      String action, String urgency) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("diagnosis", diagnosis);
    node.put("signal", signal);
    node.put("complication", complication);
    node.put("action", action);
    node.put("urgency", urgency);
    return node;
  }

  private Map<String, Object> recheckNode(String diagnosis, String item, String timing,
      String chronoType) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("diagnosis", diagnosis);
    node.put("item", item);
    node.put("timing", timing);
    node.put("chronoType", chronoType);
    node.put("advice", "按时复查");
    return node;
  }

  private Map<String, Object> chronoNode(String event, String triggerTime, String action,
      String chronoType) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("diagnosis", "2型糖尿病");
    node.put("chronoType", chronoType);
    node.put("event", event);
    node.put("triggerTime", triggerTime);
    node.put("action", action);
    return node;
  }

  private Map<String, Object> familyNode(String attention, String advice, String severity) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("attention", attention);
    node.put("advice", advice);
    node.put("severity", severity);
    return node;
  }

  /* ================= 断言工具 ================= */

  private Map<String, Object> summaryOf(Map<String, Object> graph) {
    return castMap(graph.get("summary"));
  }

  private double intensityOfFirst(List<Map<String, Object>> nodes) {
    if (nodes.isEmpty()) {
      return -1;
    }
    return ((Number) nodes.get(0).get("intensity")).doubleValue();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> castList(Object value) {
    return value instanceof List ? new ArrayList<>((List<Map<String, Object>>) value) : List.of();
  }
}
