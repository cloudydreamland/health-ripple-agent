package com.smartcloudbrain.ripple.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 反事实护栏（Counterfactual Guardrail，核心创新：安全审计层）。
 *
 * 学术依据：MedCounterFact（UT Austin/MD Anderson, 2026）揭示前沿 LLM
 * 面对反事实医学证据会"自信地盲从"甚至危险证据。本护栏将这一学术安全发现
 * 产品化：反事实决策树在生成后必须经过安全审计——
 *
 * 1. 每条反事实路径（alternativePath）按危险等级与致死性关键词判定 SAFE/FLAGGED；
 * 2. FLAGGED 路径仅允许用于"解释为什么不能这样选"，禁止作为可执行建议下发；
 * 3. 护栏审计结论（guardrailSummary）随决策一并写入哈希链证据链，可审计。
 *
 * 这使反事实推理从"学术能力"升级为"带安全边界的生亟能力"。
 */
@Service
public class CounterfactualGuardrail {

  /** 致死性/危重后果关键词：命中即 FLAGGED（不可作为建议选项）。 */
  private static final List<String> LETHAL_KEYWORDS = List.of(
      "致死", "死亡", "梗死", "酸中毒", "大出血", "出血风险", "休克",
      "癫痫", "昏迷", "猝死", "危象", "持续状态", "肾功能急性恶化");

  /** 对反事实决策树执行安全审计：逐路径判定 SAFE/FLAGGED 并输出护栏摘要。 */
  public Map<String, Object> audit(Map<String, Object> counterfactualTree) {
    List<Map<String, Object>> paths = CounterfactualService.castMapList(
        counterfactualTree.get("alternativePaths"));
    int flagged = 0;
    for (Map<String, Object> path : paths) {
      String verdict = verdictOf(path);
      path.put("guardrailVerdict", verdict);
      if ("FLAGGED".equals(verdict)) {
        path.put("guardrailNote", "该反事实路径含危重后果，仅用于解释为何不可选，禁止作为建议下发");
        flagged++;
      }
    }
    counterfactualTree.put("guardrailSummary", Map.of(
        "policy", "FLAGGED路径禁止作为可执行建议，仅保留解释性用途",
        "auditedPaths", paths.size(),
        "flaggedPaths", flagged,
        "reference", "MedCounterFact(2026): LLM会盲从危险反事实证据，护栏先行审计"));
    return counterfactualTree;
  }

  /** 判定单条反事实路径：HIGH 风险或命中致死性关键词 → FLAGGED。 */
  private String verdictOf(Map<String, Object> path) {
    if ("HIGH".equals(String.valueOf(path.get("riskIfChosen")))) {
      return "FLAGGED";
    }
    String outcome = String.valueOf(path.get("counterfactualOutcome"));
    for (String keyword : LETHAL_KEYWORDS) {
      if (outcome.contains(keyword)) {
        return "FLAGGED";
      }
    }
    return "SAFE";
  }
}
