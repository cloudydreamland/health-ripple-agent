package com.smartcloudbrain.ripple.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 反事实决策树构建服务（核心创新：Counterfactual Reasoning / XAI 学术前沿）。
 *
 * 每个 AI 决策不只记录"做了什么"（chosenPath），还记录
 * "如果做了其他选择会怎样"（alternativePaths + counterfactualOutcome），
 * 满足医疗合规可解释性：可审计、可申诉、可复盘。
 *
 * 路径分三类（pathKind），语义各不相同：
 * - SYSTEM_REJECTED：系统排除的方案（如"未识别X冲突"），wasRejected=true；
 * - PATIENT_RISK：患者不依从的行为路径（如"未遵循禁酒建议"），wasRejected=false
 *   ——它不是被系统拒绝的选项，而是护栏必须重点锁定的教育性警示；
 * - ALTERNATIVE：可选但未被采纳的替代方案。
 *
 * 每条路径携带 sourceDimension/sourceSeverity（来源维度与原始等级），
 * 供护栏做独立的标签一致性校验——护栏不盲信生成器自己打的等级。
 */
@Service
public class CounterfactualService {

  /** 为涟漪图谱构建反事实决策树：高风险冲突/高危信号/时间学触达各生成反事实路径。 */
  public Map<String, Object> buildForRipple(Map<String, Object> rippleGraph) {
    Map<String, Object> dimensions = castMap(rippleGraph.get("dimensions"));
    List<Map<String, Object>> alternativePaths = new ArrayList<>();

    // 药物-生活冲突的反事实：系统排除路径（未识别）+ 患者风险路径（不依从）
    for (Map<String, Object> conflict : castMapList(dimensions.get("drugLifestyleConflicts"))) {
      if ("HIGH".equals(conflict.get("severity"))) {
        alternativePaths.add(counterfactualPath(
            "未识别" + conflict.get("drug") + "+" + conflict.get("conflict") + "冲突",
            "涟漪推演已主动识别该冲突",
            String.valueOf(conflict.get("counterfactualNote")),
            "HIGH",
            "药物=" + conflict.get("drug") + "；冲突=" + conflict.get("conflict"),
            "SYSTEM_REJECTED",
            "drugLifestyleConflicts", "HIGH"));
        alternativePaths.add(counterfactualPath(
            "患者未遵循" + conflict.get("drug") + "的" + conflict.get("conflict") + "禁忌建议",
            "该路径不由系统选择，但必须提前警示（患者教育用途）",
            String.valueOf(conflict.get("counterfactualNote")),
            "HIGH",
            "药物=" + conflict.get("drug") + "；冲突=" + conflict.get("conflict"),
            "PATIENT_RISK",
            "drugLifestyleConflicts", "HIGH"));
      }
    }

    // 并发症信号的反事实：若未告知早期信号 → 延误诊治
    for (Map<String, Object> signal : castMapList(dimensions.get("complicationSignals"))) {
      if ("HIGH".equals(signal.get("urgency"))) {
        String urgency = String.valueOf(signal.get("urgency"));
        alternativePaths.add(counterfactualPath(
            "未告知" + signal.get("diagnosis") + "的" + signal.get("complication") + "早期信号",
            "涟漪推演已主动告知并发症信号",
            "患者出现" + signal.get("signal") + "时未能及时就医，延误" + signal.get("complication") + "诊治",
            "HIGH",
            "诊断=" + signal.get("diagnosis") + "；并发症=" + signal.get("complication"),
            "SYSTEM_REJECTED",
            "complicationSignals", urgency));
      }
    }

    // 时间学触达的反事实：若未设置主动触达 → 错过干预窗口
    for (Map<String, Object> trigger : castMapList(dimensions.get("chronoTriggers"))) {
      String chronoType = String.valueOf(trigger.get("chronoType"));
      String risk = "WINDOW".equals(chronoType) ? "HIGH" : "MEDIUM";
      alternativePaths.add(counterfactualPath(
          "未设置" + trigger.get("event") + "的时间学触达",
          "涟漪推演已主动设置时间学触达",
          "错过" + trigger.get("event") + "的主动干预窗口，患者未能及时响应",
          risk,
          "时间学类型=" + chronoType + "；事件=" + trigger.get("event"),
          "SYSTEM_REJECTED",
          "chronoTriggers", risk));
    }

    Map<String, Object> tree = new LinkedHashMap<>();
    tree.put("chosenPath", "推演健康事件涟漪影响并生成守护计划+时间学触达");
    tree.put("alternativePaths", alternativePaths);
    tree.put("counterfactualCount", alternativePaths.size());
    return tree;
  }

  /** MDT 会诊的反事实：若采用单一 Agent 决策 → 视角局限。 */
  public Map<String, Object> buildForMdt(String chiefComplaint, List<String> pastHistory) {
    List<Map<String, Object>> alternativePaths = new ArrayList<>();
    alternativePaths.add(counterfactualPath(
        "单一Agent决策（无MDT）",
        "多病共存需多视角分析，单一Agent视角局限",
        "可能遗漏其他专科视角的风险点，导致诊疗不全面",
        "MEDIUM",
        "主诉=" + chiefComplaint + "；既往史=" + String.join(",", pastHistory),
        "SYSTEM_REJECTED",
        "mdt", "MEDIUM"));
    Map<String, Object> tree = new LinkedHashMap<>();
    tree.put("chosenPath", "聚合五Agent多视角形成MDT会诊纪要");
    tree.put("alternativePaths", alternativePaths);
    tree.put("counterfactualCount", alternativePaths.size());
    return tree;
  }

  private Map<String, Object> counterfactualPath(
      String path, String rejectionReason, String outcome, String risk, String evidence,
      String pathKind, String sourceDimension, String sourceSeverity) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("path", path);
    item.put("pathKind", pathKind);
    item.put("wasRejected", "SYSTEM_REJECTED".equals(pathKind));
    item.put("rejectionReason", rejectionReason);
    item.put("counterfactualOutcome", outcome);
    item.put("riskIfChosen", risk);
    item.put("evidence", evidence);
    item.put("sourceDimension", sourceDimension);
    item.put("sourceSeverity", sourceSeverity);
    return item;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> castMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> castMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        result.add((Map<String, Object>) map);
      }
    }
    return result;
  }
}
