package com.smartcloudbrain.ripple.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 涟漪强度指数模型（Ripple Intensity Index，核心创新：把"涟漪"从比喻升级为可计算模型）。
 *
 * 每个涟漪节点按"五环模型"定位到它距离健康事件的环数 r（1=用药安全圈 … 5=家庭影响圈），
 * 节点强度 = 100 × 严重度S × 紧迫度U × 可干预度A × e^(−λ(r−1))，λ=0.22 为空间衰减系数。
 *
 * 事件级指标：
 * - RII：强度最高前5个节点均值（理论量程 0-90.25：上限=100×S_max0.95×U_max1.0×A_max0.95，
 *   环衰减使远环节点不可能触顶——这保证了"高分必然来自近环高风险"），
 *   衡量该健康事件的整体连锁冲击力；
 * - 有效扩散半径：存在强度≥15的节点的最大环数（涟漪实际波及几层生活圈）；
 * - Top风险：强度降序前3节点（医生视线第一落点）。
 *
 * 风险等级阈值（RED≥45 / ORANGE≥25）的推导依据：环1高危节点（S=0.95,U=0.9,A=0.9）
 * 强度≈77-81，环1中危节点≈35——因此 Top5 均值≥45 当且仅当近环高风险在节点构成中占主导
 * （"该事件的连锁冲击以高危项为主"），25-45 对应"存在单一高危或中危为主"，
 * <25 为"低危/远环为主"。阈值同时经单测单调性约束（严重度/环衰减单调）与旗舰病例校验。
 *
 * 全部评分维度（S/U/A/环数/衰减系数）随响应输出，评分过程本身可解释、可审计——
 * 与反事实推理、哈希链共同构成"可解释且可量化"的涟漪推演闭环。
 */
@Service
public class RippleIntensityModel {

  /** 空间衰减系数：环每外移一层，强度衰减约 19.8%（e^-0.22 ≈ 0.802）。 */
  static final double DECAY_LAMBDA = 0.22;

  /** 有效扩散半径阈值：节点强度≥15 视为涟漪"实际波及"。 */
  static final double RADIUS_THRESHOLD = 15.0;

  /** 事件级 RII 取强度最高的前 N 个节点均值（抗长尾稀释）。 */
  private static final int TOP_K_FOR_INDEX = 5;

  /** 五维 → 涟漪环：1=用药安全圈 2=疾病进展圈 3=复查窗口圈 4=触达时机圈 5=家庭影响圈。 */
  private static final Map<String, Integer> RING_OF = Map.of(
      "drugLifestyleConflicts", 1,
      "complicationSignals", 2,
      "recheckWindows", 3,
      "chronoTriggers", 4,
      "familyAttentions", 5);

  private static final Map<Integer, String> RING_NAME = Map.of(
      1, "用药安全圈",
      2, "疾病进展圈",
      3, "复查窗口圈",
      4, "触达时机圈",
      5, "家庭影响圈");

  /** 为图谱中每个涟漪节点标注环数/强度/评分依据，并在 summary 写入事件级强度指标。 */
  public Map<String, Object> annotate(Map<String, Object> graph) {
    Map<String, Object> dimensions = CounterfactualService.castMap(graph.get("dimensions"));
    List<NodeScore> scores = new ArrayList<>();

    for (Map.Entry<String, Integer> entry : RING_OF.entrySet()) {
      String dimension = entry.getKey();
      int ring = entry.getValue();
      List<Map<String, Object>> nodes = CounterfactualService.castMapList(dimensions.get(dimension));
      for (Map<String, Object> node : nodes) {
        NodeScore score = scoreNode(dimension, ring, node);
        node.put("ring", ring);
        node.put("ringName", RING_NAME.get(ring));
        node.put("intensity", score.intensity);
        node.put("scoreBreakdown", Map.of(
            "severity", score.severity,
            "urgency", score.urgency,
            "actionability", score.actionability,
            "decay", round2(Math.exp(-DECAY_LAMBDA * (ring - 1)))));
        scores.add(score);
      }
    }

    // summary 重建（原 Map.of 不可变，追加 rippleIntensity 字段）
    Map<String, Object> oldSummary = CounterfactualService.castMap(graph.get("summary"));
    Map<String, Object> summary = new LinkedHashMap<>(oldSummary);
    summary.put("rippleIntensity", eventLevelView(scores));
    graph.put("summary", summary);
    return graph;
  }

  /** 事件级强度视图：RII 指数 + 风险等级 + 有效扩散半径 + Top风险节点。 */
  private Map<String, Object> eventLevelView(List<NodeScore> scores) {
    List<NodeScore> sorted = new ArrayList<>(scores);
    sorted.sort((a, b) -> Double.compare(b.intensity, a.intensity));

    double index = 0;
    List<NodeScore> top = sorted.subList(0, Math.min(TOP_K_FOR_INDEX, sorted.size()));
    for (NodeScore s : top) {
      index += s.intensity;
    }
    index = top.isEmpty() ? 0 : round1(index / top.size());

    int radius = 0;
    for (NodeScore s : scores) {
      if (s.intensity >= RADIUS_THRESHOLD && s.ring > radius) {
        radius = s.ring;
      }
    }

    List<Map<String, Object>> topRisks = new ArrayList<>();
    for (NodeScore s : sorted.subList(0, Math.min(3, sorted.size()))) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("dimension", s.dimension);
      item.put("label", s.label);
      item.put("ring", s.ring);
      item.put("ringName", RING_NAME.get(s.ring));
      item.put("intensity", s.intensity);
      topRisks.add(item);
    }

    Map<String, Object> view = new LinkedHashMap<>();
    view.put("index", index);
    view.put("level", levelOf(index));
    view.put("levelLabel", levelLabelOf(index));
    view.put("radius", radius);
    view.put("topRisks", topRisks);
    view.put("model", "RII=100×S×U×A×e^(-0.22×(ring-1))，事件指数=Top5节点均值");
    return view;
  }

  /** 单节点评分：严重度 × 紧迫度 × 可干预度 × 环衰减。 */
  private NodeScore scoreNode(String dimension, int ring, Map<String, Object> node) {
    double severity = severityOf(dimension, node);
    double urgency = urgencyOf(dimension, node);
    double actionability = actionabilityOf(node);
    double intensity = round1(100.0 * severity * urgency * actionability
        * Math.exp(-DECAY_LAMBDA * (ring - 1)));
    return new NodeScore(dimension, ring, labelOf(dimension, node), intensity,
        round2(severity), round2(urgency), round2(actionability));
  }

  /** 严重度：文本风险等级映射；未知/缺失等级按保守中位 0.5 处理（不臆测高危也不轻纵）。 */
  private double severityOf(String dimension, Map<String, Object> node) {
    String level = String.valueOf(firstNonNull(node.get("severity"), node.get("urgency"), "MEDIUM"));
    return switch (level) {
      case "HIGH" -> 0.95;
      case "MEDIUM" -> 0.65;
      case "LOW" -> 0.35;
      default -> 0.5;
    };
  }

  /** 紧迫度：并发症信号看 urgency，复查/触达看时间学类型，用药冲突按暴露频度。 */
  private double urgencyOf(String dimension, Map<String, Object> node) {
    if ("complicationSignals".equals(dimension)) {
      return switch (String.valueOf(node.get("urgency"))) {
        case "HIGH" -> 1.0;
        case "MEDIUM" -> 0.7;
        case "LOW" -> 0.4;
        default -> 0.6;
      };
    }
    if ("recheckWindows".equals(dimension) || "chronoTriggers".equals(dimension)) {
      return switch (String.valueOf(node.get("chronoType"))) {
        case "WINDOW" -> 0.95;
        case "RHYTHM" -> 0.75;
        case "PERIODIC" -> 0.55;
        case "SEASONAL" -> 0.35;
        default -> 0.5;
      };
    }
    if ("drugLifestyleConflicts".equals(dimension)) {
      return switch (String.valueOf(node.get("severity"))) {
        case "HIGH" -> 0.9;
        case "MEDIUM" -> 0.6;
        default -> 0.4;
      };
    }
    return 0.5;
  }

  /** 可干预度：有明确处置建议 +0.45；建议含量化执行参数（数字+单位，如"15g糖/2周/＜5g盐"）再 +0.05。 */
  private double actionabilityOf(Map<String, Object> node) {
    String guidance = String.valueOf(firstNonNull(node.get("advice"), node.get("action"), ""));
    double base = 0.45;
    if (!guidance.isBlank() && !"null".equals(guidance)) {
      base += 0.45;
      // 量化执行参数 = 数字后接计量单位（纯数字不算——"每天喝8杯水"与"凌晨3点"应同权）
      if (guidance.matches(".*\\d+(\\.\\d+)?\\s*(g|mg|ml|mmol|μg|ug|kg|％|%|小时|分钟|天|日|周|个月|月|年|次|滴|杯).*")) {
        base += 0.05;
      }
    }
    return Math.min(0.95, base);
  }

  private String labelOf(String dimension, Map<String, Object> node) {
    return switch (dimension) {
      case "drugLifestyleConflicts" -> node.get("drug") + "+" + node.get("conflict");
      case "complicationSignals" -> node.get("diagnosis") + "→" + node.get("complication");
      case "recheckWindows" -> String.valueOf(node.get("item"));
      case "chronoTriggers" -> String.valueOf(node.get("event"));
      case "familyAttentions" -> String.valueOf(node.get("attention"));
      default -> String.valueOf(node);
    };
  }

  /** 风险等级：对齐医疗 alert 三级色（红/橙/黄），推导依据见类注释（近环高危占主导⇔RED）。 */
  private String levelOf(double index) {
    if (index >= 45) {
      return "RED";
    }
    return index >= 25 ? "ORANGE" : "YELLOW";
  }

  private String levelLabelOf(double index) {
    if (index >= 45) {
      return "红色·高强度涟漪";
    }
    return index >= 25 ? "橙色·中强度涟漪" : "黄色·低强度涟漪";
  }

  private static Object firstNonNull(Object a, Object b, Object fallback) {
    if (a != null && !"null".equals(String.valueOf(a))) {
      return a;
    }
    if (b != null && !"null".equals(String.valueOf(b))) {
      return b;
    }
    return fallback;
  }

  private static double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  /** 节点评分内部载体。 */
  private record NodeScore(
      String dimension, int ring, String label, double intensity,
      double severity, double urgency, double actionability) {
  }
}
