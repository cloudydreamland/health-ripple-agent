package com.smartcloudbrain.ripple.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 反事实护栏审计测试（核心：护栏判定不循环依赖生成器标签）。
 *
 * 关键性质：即使生成器把危重路径的 riskIfChosen 误标为 LOW/MEDIUM
 * （自审自的循环论证破绽），护栏的关键词通道也必须独立将其 FLAGGED，
 * 并记录 labelMismatch（标签覆写）——这是"护栏可捕获生成器误标"的直接证明。
 */
class CounterfactualGuardrailTest {

  private final CounterfactualGuardrail guardrail = new CounterfactualGuardrail();

  @Test
  void mislabeledLethalPathMustStillBeFlaggedByKeywordChannel() {
    // 生成器误标：后果描述乳酸酸中毒（致死性），但 riskIfChosen 误标为 LOW
    Map<String, Object> tree = treeWith(path("患者未遵循禁酒建议",
        "可能发生乳酸酸中毒", "LOW", "LOW"));
    guardrail.audit(tree);

    List<Map<String, Object>> paths = paths(tree);
    Map<String, Object> victim = paths.get(0);
    assertEquals("FLAGGED", victim.get("guardrailVerdict"),
        "关键词通道必须独立于标签：误标路径仍需锁定");
    assertTrue(((Number) castMap(tree.get("guardrailSummary")).get("labelMismatches")).intValue() >= 1,
        "必须记录标签不一致（护栏覆写生成器标签）");
    assertTrue(String.valueOf(victim.get("flagReasons")).contains("POLICY-CG-001"),
        "FLAGGED 原因必须引用独立关键词通道策略");
  }

  @Test
  void englishLethalKeywordIsCaught() {
    Map<String, Object> tree = treeWith(path("if bleeding not recognized",
        "may cause hemorrhagic shock and death", "LOW", "LOW"));
    guardrail.audit(tree);
    assertEquals("FLAGGED", paths(tree).get(0).get("guardrailVerdict"),
        "英文危重关键词同样触发关键词通道");
  }

  @Test
  void benignPathStaysSafeAndLabeledConsistent() {
    Map<String, Object> tree = treeWith(path("未设置换季血糖监测触达",
        "错过换季血糖监测的主动干预窗口，患者未能及时响应", "MEDIUM", "MEDIUM"));
    guardrail.audit(tree);
    Map<String, Object> path = paths(tree).get(0);
    assertEquals("SAFE", path.get("guardrailVerdict"), "良性路径不得误锁");
    assertEquals(0, ((Number) castMap(tree.get("guardrailSummary")).get("suspiciousLabels")).intValue(),
        "良性路径不应被记为可疑高标");
  }

  @Test
  void highSourceSeverityWithoutLethalTextIsFlaggedAndMarkedSuspicious() {
    // 标签 HIGH 但后果文本无危重语义：按源等级通道 FLAGGED，同时记 suspiciousLabel 供知识库治理
    Map<String, Object> tree = treeWith(path("未识别造影剂联用冲突",
        "需要影像科复查安排", "HIGH", "HIGH"));
    guardrail.audit(tree);
    Map<String, Object> path = paths(tree).get(0);
    assertEquals("FLAGGED", path.get("guardrailVerdict"));
    assertTrue(((Number) castMap(tree.get("guardrailSummary")).get("suspiciousLabels")).intValue() >= 1);
  }

  @Test
  void chosenPathIsAudited() {
    Map<String, Object> tree = treeWith(path("p", "无危重语义", "LOW", "LOW"));
    tree.put("chosenPath", "推演健康事件涟漪影响并生成守护计划");
    guardrail.audit(tree);
    assertEquals("SAFE", tree.get("chosenPathVerdict"));

    tree.put("chosenPath", "");
    guardrail.audit(tree);
    assertEquals("MISSING", tree.get("chosenPathVerdict"), "已选路径缺失必须被判为审计不合格");
  }

  @Test
  void patientRiskPathsAreNotMarkedAsRejected() {
    // PATIENT_RISK 路径语义：不是被系统拒绝的选项，wasRejected 必须为 false
    Map<String, Object> tree = new CounterfactualService().buildForRipple(rippleGraph());
    List<Map<String, Object>> paths = CounterfactualService.castMapList(tree.get("alternativePaths"));
    assertTrue(paths.stream().anyMatch(p -> "PATIENT_RISK".equals(p.get("pathKind"))
            && Boolean.FALSE.equals(p.get("wasRejected"))),
        "患者行为路径应为 PATIENT_RISK 且 wasRejected=false");
    assertTrue(paths.stream().anyMatch(p -> "SYSTEM_REJECTED".equals(p.get("pathKind"))
            && Boolean.TRUE.equals(p.get("wasRejected"))),
        "系统排除路径应为 SYSTEM_REJECTED 且 wasRejected=true");
  }

  /* ================= 测试脚手架 ================= */

  private Map<String, Object> rippleGraph() {
    Map<String, Object> dimensions = new LinkedHashMap<>();
    dimensions.put("drugLifestyleConflicts", List.of(Map.of(
        "drug", "二甲双胍", "conflict", "饮酒", "severity", "HIGH",
        "risk", "乳酸酸中毒（严重可致死）", "advice", "服药期间禁止饮酒",
        "counterfactualNote", "若未识别饮酒冲突，患者可能发生乳酸酸中毒")));
    dimensions.put("recheckWindows", List.of());
    dimensions.put("complicationSignals", List.of());
    dimensions.put("familyAttentions", List.of());
    dimensions.put("chronoTriggers", List.of(Map.of(
        "chronoType", "SEASONAL", "event", "换季血糖波动", "triggerTime", "秋冬换季", "action", "提醒监测")));
    Map<String, Object> graph = new LinkedHashMap<>();
    graph.put("dimensions", dimensions);
    graph.put("summary", Map.of("totalNodes", 2, "highRiskCount", 1));
    return graph;
  }

  private Map<String, Object> path(String name, String outcome, String risk, String sourceSeverity) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("path", name);
    item.put("pathKind", "SYSTEM_REJECTED");
    item.put("wasRejected", true);
    item.put("rejectionReason", "r");
    item.put("counterfactualOutcome", outcome);
    item.put("riskIfChosen", risk);
    item.put("evidence", "e");
    item.put("sourceDimension", "test");
    item.put("sourceSeverity", sourceSeverity);
    return item;
  }

  private Map<String, Object> treeWith(Map<String, Object> path) {
    Map<String, Object> tree = new LinkedHashMap<>();
    tree.put("chosenPath", "chosen");
    tree.put("alternativePaths", new ArrayList<>(List.of(path)));
    tree.put("counterfactualCount", 1);
    return tree;
  }

  private List<Map<String, Object>> paths(Map<String, Object> tree) {
    return CounterfactualService.castMapList(tree.get("alternativePaths"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }
}
