package com.smartcloudbrain.ripple.service;

import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * 72小时涟漪预报服务（Ripple Forecast，第六轮创新：从"看见涟漪"到"预见涟漪"）。
 *
 * RII 回答"现在有多大浪"，预报回答"未来72小时浪什么时候来、从哪来"：
 * 把患者全部活跃时间学触达的 RII 强度，按各自的时间学语义在 72 小时轴上做
 * <b>确定性叠加</b>（无随机数，逐桶可复算）：
 * - WINDOW 窗口期：在触达时点产生高斯尖峰（σ=1.5h）——黄金窗口错过即衰减；
 * - RHYTHM 节律：在每日节律时段产生尖峰（σ=1.2h，未来4天逐日展开）；
 * - PERIODIC 周期：在到期时点产生宽峰（σ=6h，复查提醒的合理行动窗口）；
 * - SEASONAL 季节：目标月内维持低幅基线（×0.25），不在72小时轴上硬造峰值。
 *
 * 与消解闭环同源联动：RESOLVED/ESCALATED 触达剔除（已消解/已转人工）；
 * UNRESOLVED 触达 ×1.5 加压（守护不降级，与反馈语义一致）。
 *
 * 可解释性：每个小时桶列出贡献事件（贡献≥3.0），医生可逐桶追问"这个峰是谁"。
 */
@Service
public class RippleForecastService {

  public static final int HORIZON_HOURS = 72;
  private static final double UNRESOLVED_MULTIPLIER = 1.5;
  private static final double SEASONAL_BASELINE = 0.25;
  private static final double DRIVER_THRESHOLD = 3.0;

  private final ChronoTriggerRepository triggerRepository;
  private final RippleClosureService closureService;

  public RippleForecastService(ChronoTriggerRepository triggerRepository, RippleClosureService closureService) {
    this.triggerRepository = triggerRepository;
    this.closureService = closureService;
  }

  /** 患者未来72小时涟漪强度预报（逐小时桶 + 峰值 + 驱动事件）。 */
  public Map<String, Object> forecast(Long patientId) {
    return forecast(patientId, 0.0);
  }

  /**
   * 带依从性沙盘的预报（人机共驾·反事实沙盘，第七轮创新）。
   *
   * @param adherence 守护执行度 0~1：模拟"触达干预被执行的比例"。
   *                  WINDOW/RHYTHM/PERIODIC 驱动的贡献 ×(1−adherence)（守护被执行→对应涟漪被消解）；
   *                  SEASONAL 为环境基线，不随守护执行度消失。
   *                  adherence=0 即当前预报；1−a 与消解闭环同语义，纯确定性可复算。
   */
  public Map<String, Object> forecast(Long patientId, double adherence) {
    final double a = Math.max(0.0, Math.min(1.0, adherence));
    LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
    List<ChronoTrigger> triggers = triggerRepository.findByPatientIdOrderByNextTriggerAtAsc(patientId);
    Map<String, Double> intensityMap = closureService.intensityMapByEvent(patientId);

    Series actual = computeSeries(triggers, intensityMap, now, 0.0);
    double[] buckets = actual.buckets;
    Map<Integer, LinkedHashMap<String, Double>> driverByBucket = actual.drivers;
    int activeTriggers = actual.activeTriggers;
    int suppressedResolved = actual.suppressedResolved;
    int suppressedEscalated = actual.suppressedEscalated;
    int suppressedVetoed = actual.suppressedVetoed;

    // 组装输出（封顶100，与RII同量纲）
    List<Map<String, Object>> bucketViews = new ArrayList<>();
    int peakHour = 0;
    for (int i = 0; i < HORIZON_HOURS; i++) {
      buckets[i] = Math.min(100.0, buckets[i]);
      if (buckets[i] > buckets[peakHour]) {
        peakHour = i;
      }
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("hourOffset", i);
      view.put("intensity", round1(buckets[i]));
      List<String> drivers = new ArrayList<>();
      LinkedHashMap<String, Double> contributions = driverByBucket.get(i);
      if (contributions != null) {
        for (Map.Entry<String, Double> e : contributions.entrySet()) {
          if (e.getValue() >= DRIVER_THRESHOLD) {
            drivers.add(e.getKey());
          }
        }
      }
      view.put("drivers", drivers);
      bucketViews.add(view);
    }

    double horizonAvg = round1(java.util.Arrays.stream(buckets).sum() / HORIZON_HOURS);
    Map<String, Object> peak = new LinkedHashMap<>();
    peak.put("hourOffset", peakHour);
    peak.put("intensity", round1(buckets[peakHour]));
    peak.put("drivers", bucketViews.get(peakHour).get("drivers"));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("patientId", patientId);
    result.put("forecastAt", now.toString());
    result.put("horizonHours", HORIZON_HOURS);
    result.put("buckets", bucketViews);
    result.put("peak", peak);
    result.put("horizonAvg", horizonAvg);
    result.put("activeTriggers", activeTriggers);
    result.put("suppressedResolved", suppressedResolved);
    result.put("suppressedEscalated", suppressedEscalated);
    result.put("suppressedVetoed", suppressedVetoed);
    result.put("model", "预报=时间学触达RII强度的确定性叠加（高斯激活 WINDOWσ1.5h/RHYTHMσ1.2h/PERIODICσ6h，"
        + "季节=目标月低幅基线），RESOLVED/ESCALATED/VETOED剔除、UNRESOLVED×1.5加压；逐桶列驱动事件，可复算");

    // 依从性沙盘：同一引擎按 (1−a) 折减非季节驱动的确定性重算（医生拖动滑杆预演"守护被执行后浪有多高"）
    if (a > 0.0) {
      Series sandbox = computeSeries(triggers, intensityMap, now, a);
      // 与实际曲线同口径：先封顶（100）再取峰/均值——600+触达叠加时原始桶远超100，口径不一会让沙盘"看起来更糟"
      for (int i = 0; i < HORIZON_HOURS; i++) {
        sandbox.buckets[i] = Math.min(100.0, sandbox.buckets[i]);
      }
      List<Map<String, Object>> sandboxViews = new ArrayList<>();
      int sandboxPeak = 0;
      for (int i = 0; i < HORIZON_HOURS; i++) {
        if (sandbox.buckets[i] > sandbox.buckets[sandboxPeak]) {
          sandboxPeak = i;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("hourOffset", i);
        view.put("intensity", round1(sandbox.buckets[i]));
        sandboxViews.add(view);
      }
      Map<String, Object> sandboxView = new LinkedHashMap<>();
      sandboxView.put("adherence", round2(a));
      sandboxView.put("buckets", sandboxViews);
      sandboxView.put("peakHourOffset", sandboxPeak);
      sandboxView.put("peakIntensity", round1(sandbox.buckets[sandboxPeak]));
      sandboxView.put("horizonAvg", round1(java.util.Arrays.stream(sandbox.buckets).sum() / HORIZON_HOURS));
      sandboxView.put("model", "沙盘=非季节驱动贡献×(1−依从度)的同源确定性重算；季节基线为环境因素不折减；与实际曲线同封顶口径");
      result.put("sandbox", sandboxView);
    }
    return result;
  }

  /** 一次预报序列计算（actual 与沙盘共用同一引擎逻辑，保证可复算）。 */
  private record Series(double[] buckets, Map<Integer, LinkedHashMap<String, Double>> drivers,
      int activeTriggers, int suppressedResolved, int suppressedEscalated, int suppressedVetoed) { }

  private Series computeSeries(List<ChronoTrigger> triggers, Map<String, Double> intensityMap,
      LocalDateTime now, double adherence) {
    double[] buckets = new double[HORIZON_HOURS];
    Map<Integer, LinkedHashMap<String, Double>> driverByBucket = new TreeMap<>();
    int suppressedResolved = 0;
    int suppressedEscalated = 0;
    int suppressedVetoed = 0;
    int activeTriggers = 0;

    for (ChronoTrigger trigger : triggers) {
      boolean resolved = "RESOLVED".equals(trigger.getFeedbackStatus()) || "RESOLVED".equals(trigger.getStatus());
      boolean escalated = "ESCALATED".equals(trigger.getFeedbackStatus()) || "ESCALATED".equals(trigger.getStatus());
      boolean vetoed = "VETOED".equals(trigger.getStatus());
      if (resolved) {
        suppressedResolved++;
        continue;
      }
      if (escalated) {
        suppressedEscalated++;
        continue;
      }
      if (vetoed) {
        suppressedVetoed++;
        continue;
      }
      double amplitude = closureService.intensityOf(intensityMap, trigger);
      if ("UNRESOLVED".equals(trigger.getFeedbackStatus())) {
        amplitude *= UNRESOLVED_MULTIPLIER;
      }
      String type = String.valueOf(trigger.getChronoType());
      activeTriggers++;
      switch (type) {
        case "WINDOW" -> addSpike(buckets, driverByBucket, trigger, now,
            spikeTime(trigger.getNextTriggerAt(), trigger.getLastFiredAt(), now), 1.5,
            amplitude * (1 - adherence));
        case "RHYTHM" -> {
          Integer hour = ChronoEngine.parseStartHour(trigger.getTriggerTime());
          if (hour == null) {
            hour = 8;
          }
          // 未来4天逐日节律峰
          for (int day = 0; day < 4; day++) {
            LocalDateTime at = now.toLocalDate().atTime(hour, 0).plusDays(day);
            if (!at.isAfter(now)) {
              at = at.plusDays(1);
            }
            addSpike(buckets, driverByBucket, trigger, now, at, 1.2, amplitude * (1 - adherence));
          }
        }
        case "PERIODIC" -> addSpike(buckets, driverByBucket, trigger, now,
            spikeTime(trigger.getNextTriggerAt(), null, now), 6.0, amplitude * (1 - adherence));
        case "SEASONAL" -> {
          Integer month = ChronoEngine.parseTargetMonth(trigger.getTriggerTime());
          if (month != null && month == now.getMonthValue()) {
            // 季节基线：环境因素，不随守护执行度折减；目标月内低幅平铺，不硬造峰值
            for (int i = 0; i < HORIZON_HOURS; i++) {
              buckets[i] += amplitude * SEASONAL_BASELINE;
              driverByBucket.computeIfAbsent(i, k -> new LinkedHashMap<>())
                  .merge(trigger.getEvent(), amplitude * SEASONAL_BASELINE, Double::sum);
            }
          }
        }
        default -> addSpike(buckets, driverByBucket, trigger, now,
            spikeTime(trigger.getNextTriggerAt(), null, now), 6.0, amplitude * (1 - adherence));
      }
    }
    return new Series(buckets, driverByBucket, activeTriggers, suppressedResolved, suppressedEscalated, suppressedVetoed);
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private LocalDateTime spikeTime(LocalDateTime nextTriggerAt, LocalDateTime lastFiredAt, LocalDateTime now) {
    if (nextTriggerAt != null) {
      return nextTriggerAt;
    }
    return lastFiredAt != null ? lastFiredAt : now;
  }

  /** 把一次高斯激活铺到72小时桶上（按桶中心时刻求值）。 */
  private void addSpike(double[] buckets, Map<Integer, LinkedHashMap<String, Double>> drivers,
                        ChronoTrigger trigger, LocalDateTime now, LocalDateTime at, double sigma, double amplitude) {
    if (at == null) {
      return;
    }
    double centerOffset = Duration.between(now, at).toMinutes() / 60.0;
    for (int i = 0; i < HORIZON_HOURS; i++) {
      double x = i + 0.5;
      double contribution = amplitude * Math.exp(-0.5 * Math.pow((x - centerOffset) / sigma, 2));
      if (contribution < 0.1) {
        continue;
      }
      buckets[i] += contribution;
      drivers.computeIfAbsent(i, k -> new LinkedHashMap<>())
          .merge(trigger.getEvent(), contribution, Double::sum);
    }
  }

  private static double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }
}
