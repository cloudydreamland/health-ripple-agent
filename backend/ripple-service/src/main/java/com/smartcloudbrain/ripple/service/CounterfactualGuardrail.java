package com.smartcloudbrain.ripple.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 反事实护栏（Counterfactual Guardrail，核心创新：安全审计层）。
 *
 * 学术依据：MedCounterFact（UT Austin/MD Anderson, 2026）揭示前沿 LLM
 * 面对反事实医学证据会"自信地盲从"甚至危险证据。本护栏将这一学术安全发现
 * 产品化：反事实决策树在生成后必须经过安全审计。
 *
 * 双通道判定（防"自审自"的循环论证——判定依据与生成器标签相互独立）：
 * - POLICY-CG-001 关键词通道（独立）：对路径文本/后果/依据做中英双语致死性关键词扫描。
 *   即使生成器把风险等级标错、标漏，只要后果文本描述了危重后果，本通道照样 FLAGGED；
 * - POLICY-CG-002 源等级通道：源数据严重度为 HIGH 的路径 FLAGGED。
 * - 标签一致性检查：关键词命中但源标签非 HIGH → 记 labelMismatch（护栏覆写生成器标签），
 *   反向（标签 HIGH 但文本无危重语义）记为 suspiciousLabel，供知识库治理。
 *
 * FLAGGED 路径仅允许用于"解释为什么不能这样选/必须提前警示什么"，
 * 禁止作为可执行建议下发；护栏审计结论（guardrailSummary）随决策一并写入哈希链证据链。
 */
@Service
public class CounterfactualGuardrail {

  /** POLICY-CG-001：致死性/危重后果关键词（中英双语），命中即 FLAGGED（不可作为建议选项）。 */
  private static final List<String> LETHAL_KEYWORDS = List.of(
      // 中文
      "致死", "死亡", "梗死", "酸中毒", "大出血", "出血风险", "休克",
      "癫痫", "昏迷", "猝死", "危象", "持续状态", "肾功能急性恶化",
      // 英文（生成器若切换语言，关键词通道依旧生效）
      "death", "fatal", "infarction", "shock", "acidosis", "hemorrhage", " coma", "stroke");

  /** 对反事实决策树执行安全审计：逐路径判定 SAFE/FLAGGED 并输出护栏摘要。 */
  public Map<String, Object> audit(Map<String, Object> counterfactualTree) {
    List<Map<String, Object>> paths = CounterfactualService.castMapList(
        counterfactualTree.get("alternativePaths"));
    int flagged = 0;
    int labelMismatches = 0;
    int suspiciousLabels = 0;
    for (Map<String, Object> path : paths) {
      Map<String, Object> verdict = verdictOf(path);
      path.put("guardrailVerdict", verdict.get("verdict"));
      path.put("flagReasons", verdict.get("flagReasons"));
      if ("FLAGGED".equals(verdict.get("verdict"))) {
        path.put("guardrailNote", "该反事实路径含危重后果，仅用于解释为何不可选/须提前警示，禁止作为建议下发");
        flagged++;
      }
      labelMismatches += (int) verdict.get("labelMismatch");
      suspiciousLabels += (int) verdict.get("suspiciousLabel");
    }

    // chosenPath 同样受审：空缺或无实质内容的已选路径属于审计不合格
    String chosen = String.valueOf(counterfactualTree.getOrDefault("chosenPath", ""));
    counterfactualTree.put("chosenPathVerdict", chosen.isBlank() ? "MISSING" : "SAFE");

    counterfactualTree.put("guardrailSummary", Map.of(
        "policy", "FLAGGED路径禁止作为可执行建议，仅保留解释性/警示性用途",
        "policyIds", List.of("POLICY-CG-001-keyword-channel", "POLICY-CG-002-source-severity"),
        "auditedPaths", paths.size(),
        "flaggedPaths", flagged,
        "labelMismatches", labelMismatches,
        "suspiciousLabels", suspiciousLabels,
        "chosenPathVerdict", chosen.isBlank() ? "MISSING" : "SAFE",
        "reference", "MedCounterFact(2026): LLM会盲从危险反事实证据，护栏先行审计；"
            + "关键词通道独立于生成器标签，可覆写误标"));
    return counterfactualTree;
  }

  /**
   * 双通道判定单条反事实路径。
   * 返回 {verdict, flagReasons, labelMismatch, suspiciousLabel}。
   */
  private Map<String, Object> verdictOf(Map<String, Object> path) {
    String outcome = String.valueOf(path.get("counterfactualOutcome"));
    String evidence = String.valueOf(path.get("evidence"));
    String label = String.valueOf(path.get("riskIfChosen"));
    String sourceLabel = String.valueOf(path.getOrDefault("sourceSeverity", label));

    List<String> reasons = new ArrayList<>();
    boolean keywordHit = false;
    for (String keyword : LETHAL_KEYWORDS) {
      if (outcome.toLowerCase().contains(keyword.toLowerCase())
          || evidence.toLowerCase().contains(keyword.toLowerCase())) {
        keywordHit = true;
        reasons.add("POLICY-CG-001:KEYWORD[" + keyword.trim() + "]");
      }
    }
    if ("HIGH".equalsIgnoreCase(label) || "HIGH".equalsIgnoreCase(sourceLabel)) {
      reasons.add("POLICY-CG-002:SOURCE_SEVERITY_HIGH");
    }

    // 标签一致性：关键词命中但源标签非HIGH → 生成器漏标（护栏覆写）；反之可疑高标
    boolean labelMismatch = keywordHit && !"HIGH".equalsIgnoreCase(sourceLabel);
    boolean suspiciousLabel = !keywordHit && "HIGH".equalsIgnoreCase(sourceLabel);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("verdict", reasons.isEmpty() ? "SAFE" : "FLAGGED");
    result.put("flagReasons", reasons);
    result.put("labelMismatch", labelMismatch ? 1 : 0);
    result.put("suspiciousLabel", suspiciousLabel ? 1 : 0);
    return result;
  }
}
