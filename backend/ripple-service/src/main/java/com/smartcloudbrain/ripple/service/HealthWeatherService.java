package com.smartcloudbrain.ripple.service;

import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.entity.RippleEvent;
import com.smartcloudbrain.ripple.repository.RippleEventRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 健康气象日报服务（Health Weather Daily，核心创新：患者友好型风险叙事）。
 *
 * 医疗风险数据对患者和家属是"天书"：RII=68.4、WINDOW触达、FLAGGED路径……
 * 健康气象用全民理解的天气隐喻转译当日守护态势：
 * - 晴 SUNNY：今日无重点守护项
 * - 多云 CLOUDY：轻度关注（有活跃守护项但今日压力小）
 * - 大雨 RAIN：重点守护（多项高强度触达落在今日）
 * - 暴雨 STORM：高度警戒（高强度+升级就医信号）
 *
 * 指数可解释：今日到期触达项的涟漪强度 ÷ 全部活跃触达强度 × 100
 * （即"守护压力有多大比例落在今天"），每升级就医项 +15（家属须知）。
 * 每项"降水"都携带 Timing Card 循证卡片——天气预报式的易读性 + 循证级严谨性。
 */
@Service
public class HealthWeatherService {

  private final ChronoEngine chronoEngine;
  private final RippleEventRepository rippleEventRepository;
  private final RippleClosureService closureService;

  public HealthWeatherService(ChronoEngine chronoEngine, RippleEventRepository rippleEventRepository,
      RippleClosureService closureService) {
    this.chronoEngine = chronoEngine;
    this.rippleEventRepository = rippleEventRepository;
    this.closureService = closureService;
  }

  /** 患者今日健康气象。 */
  public Map<String, Object> daily(Long patientId) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime endOfToday = LocalDate.now().atTime(23, 59, 59);
    List<ChronoTrigger> all = chronoEngine.findByPatient(patientId);
    Map<String, Double> intensityMap = closureService.intensityMapByEvent(patientId);

    List<ChronoTrigger> active = all.stream()
        .filter(t -> "ACTIVE".equals(t.getStatus()) || "FIRED".equals(t.getStatus()))
        .toList();
    List<ChronoTrigger> dueToday = active.stream()
        .filter(t -> t.getNextTriggerAt() != null && !t.getNextTriggerAt().isAfter(endOfToday))
        .toList();
    long escalatedRecent = all.stream()
        .filter(t -> "ESCALATED".equals(t.getFeedbackStatus()))
        .count();
    long unresolvedToday = all.stream()
        .filter(t -> "UNRESOLVED".equals(t.getFeedbackStatus())
            && t.getFeedbackAt() != null && t.getFeedbackAt().isAfter(now.minusHours(24)))
        .count();

    double totalActiveIntensity = active.stream().mapToDouble(t -> closureService.intensityOf(intensityMap, t)).sum();
    double todayIntensity = dueToday.stream().mapToDouble(t -> closureService.intensityOf(intensityMap, t)).sum();
    double index = totalActiveIntensity <= 0 ? 0.0
        : round1(todayIntensity / totalActiveIntensity * 100.0);
    index = Math.min(100.0, round1(index + escalatedRecent * 15.0 + unresolvedToday * 10.0));

    String weather = weatherOf(index);
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("patientId", patientId);
    view.put("date", now.toLocalDate().toString());
    view.put("index", index);
    view.put("weather", weather);
    view.put("weatherLabel", labelOf(weather));
    view.put("color", colorOf(weather));
    view.put("headline", headlineOf(weather, dueToday, escalatedRecent));
    view.put("dueTodayCount", dueToday.size());
    view.put("escalatedCount", escalatedRecent);
    view.put("unresolvedRecent", unresolvedToday);
    view.put("items", itemsOf(dueToday, intensityMap));
    view.put("familyTip", familyTipOf(patientId));
    view.put("model", "健康气象指数=今日到期触达强度÷全部活跃触达强度×100，每升级就医项+15");
    return view;
  }

  private List<Map<String, Object>> itemsOf(List<ChronoTrigger> dueToday, Map<String, Double> intensityMap) {
    List<Map<String, Object>> items = new ArrayList<>();
    // 按守护事件名去重（多病共存推演会对同一事件产生多条触达，日报只报一次、取强度最高者）
    Map<String, ChronoTrigger> distinct = new LinkedHashMap<>();
    for (ChronoTrigger trigger : dueToday) {
      String key = String.valueOf(trigger.getEvent());
      double score = closureService.intensityOf(intensityMap, trigger);
      double existing = distinct.containsKey(key)
          ? closureService.intensityOf(intensityMap, distinct.get(key)) : -1;
      if (!distinct.containsKey(key) || score > existing) {
        distinct.put(key, trigger);
      }
    }
    for (ChronoTrigger trigger : distinct.values().stream()
        .sorted(Comparator.comparing((ChronoTrigger t) -> -closureService.intensityOf(intensityMap, t))
            .thenComparing(ChronoTrigger::getNextTriggerAt))
        .limit(6).toList()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("chronoType", trigger.getChronoType());
      item.put("event", trigger.getEvent());
      item.put("triggerTime", trigger.getTriggerTime());
      item.put("nextTriggerAt", trigger.getNextTriggerAt() == null ? "" : trigger.getNextTriggerAt().toString());
      item.put("action", trigger.getAction());
      item.put("intensity", closureService.intensityOf(intensityMap, trigger));
      item.put("timingCard", Map.of(
          "evidenceBasis", nullToEmpty(trigger.getEvidenceBasis()),
          "missCost", nullToEmpty(trigger.getMissCost()),
          "evidenceLevel", nullToEmpty(trigger.getEvidenceLevel())));
      items.add(item);
    }
    return items;
  }

  /** 家属提示：取最近一次涟漪推演的家庭影响圈最强节点。 */
  private String familyTipOf(Long patientId) {
    for (RippleEvent event : rippleEventRepository.findByPatientIdOrderByCreatedAtDesc(patientId)) {
      try {
        Map<String, Object> graph = CounterfactualService.castMap(
            new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                event.getRippleGraphJson() == null ? "{}" : event.getRippleGraphJson(), Object.class));
        Map<String, Object> dimensions = CounterfactualService.castMap(graph.get("dimensions"));
        List<Map<String, Object>> family = CounterfactualService.castMapList(dimensions.get("familyAttentions"));
        String best = null;
        double bestScore = -1;
        for (Map<String, Object> node : family) {
          double score = node.get("intensity") instanceof Number n ? n.doubleValue() : 0;
          if (score > bestScore) {
            bestScore = score;
            best = String.valueOf(node.get("attention"));
          }
        }
        if (best != null) {
          return best;
        }
      } catch (Exception ignored) {
        // 解析失败继续找下一条事件
      }
    }
    return "";
  }

  private String weatherOf(double index) {
    if (index >= 60) {
      return "STORM";
    }
    if (index >= 30) {
      return "RAIN";
    }
    return index > 0 ? "CLOUDY" : "SUNNY";
  }

  private String labelOf(String weather) {
    return switch (weather) {
      case "STORM" -> "暴雨·高度警戒";
      case "RAIN" -> "大雨·重点守护";
      case "CLOUDY" -> "多云·轻度关注";
      default -> "晴·今日无重点守护";
    };
  }

  private String colorOf(String weather) {
    return switch (weather) {
      case "STORM" -> "#ff5d6c";
      case "RAIN" -> "#ffab4a";
      case "CLOUDY" -> "#ffd94a";
      default -> "#3ee6a4";
    };
  }

  private String headlineOf(String weather, List<ChronoTrigger> dueToday, long escalated) {
    if (escalated > 0) {
      return "有" + escalated + "项已升级就医，请家属重点关注医生反馈";
    }
    if (dueToday.isEmpty()) {
      return "今日无重点守护项，保持现有用药与监测节奏";
    }
    // 按事件名去重后再播报（多病共存推演会对同一事件产生多条触达）
    List<String> distinctNames = dueToday.stream()
        .map(t -> String.valueOf(t.getEvent()))
        .distinct()
        .toList();
    StringBuilder sb = new StringBuilder("今日").append(distinctNames.size()).append("项守护：");
    for (int i = 0; i < Math.min(2, distinctNames.size()); i++) {
      if (i > 0) {
        sb.append("、");
      }
      sb.append(distinctNames.get(i));
    }
    return sb.toString();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
