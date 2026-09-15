package com.smartcloudbrain.ripple.service;

import com.smartcloudbrain.ripple.entity.ChronoRule;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 医疗时间学引擎（chrono-medical engine，核心创新：时间维度）。
 *
 * 将"医疗的时间性"形式化为四类可计算规则：
 * - WINDOW   窗口期：黄金120分钟/3小时 —— 即时一次性触达，错过即失效
 * - RHYTHM   节律性：夜间哮喘3-5点/凌晨血压晨峰 —— 每日固定时段触达
 * - PERIODIC 周期性：服药2周复查肝功/糖化3个月 —— 偏移天数起算，可周期滚动
 * - SEASONAL 季节性：换季慢病波动 —— 目标月份触达，年周期滚动
 *
 * DuMate 智能体通过 GET /api/chrono/due 拉取到期触达项，主动触达患者（主动式智能体）。
 */
@Service
public class ChronoEngine {

  private final ChronoTriggerRepository triggerRepository;

  public ChronoEngine(ChronoTriggerRepository triggerRepository) {
    this.triggerRepository = triggerRepository;
  }

  /** 依据时间学规则与基准时间（事件发生时刻）创建触达计划（含 Timing Card 循证卡片）。 */
  public ChronoTrigger schedule(ChronoRule rule, Long patientId, Long rippleEventId, LocalDateTime baseTime) {
    ChronoTrigger trigger = new ChronoTrigger();
    trigger.setPatientId(patientId);
    trigger.setRippleEventId(rippleEventId);
    trigger.setChronoType(rule.getChronoType());
    trigger.setEvent(rule.getEvent());
    trigger.setTriggerTime(rule.getTriggerTime());
    trigger.setAction(rule.getAction());
    trigger.setEvidenceBasis(rule.getEvidenceBasis());
    trigger.setMissCost(rule.getMissCost());
    trigger.setEvidenceLevel(rule.getEvidenceLevel());
    trigger.setStatus("ACTIVE");
    trigger.setNextTriggerAt(computeNext(rule, baseTime));
    return triggerRepository.save(trigger);
  }

  /** 到期查询：返回所有 ACTIVE 且 nextTriggerAt <= now 的触达项（DuMate 定时任务轮询入口）。 */
  public List<ChronoTrigger> due(LocalDateTime now) {
    return triggerRepository.findByStatusAndNextTriggerAtLessThanEqualOrderByNextTriggerAtAsc("ACTIVE", now);
  }

  /**
   * 确认已触达：按时间学类型推进或完结。
   * - WINDOW：一次性 → FIRED
   * - RHYTHM：明日同时段
   * - PERIODIC：有周期则滚动，无周期则 COMPLETED
   * - SEASONAL：明年同月
   */
  public ChronoTrigger ack(Long triggerId) {
    ChronoTrigger trigger = triggerRepository.findById(triggerId)
        .orElseThrow(() -> new IllegalArgumentException("触达计划不存在: " + triggerId));
    LocalDateTime now = LocalDateTime.now();
    trigger.setLastFiredAt(now);
    switch (String.valueOf(trigger.getChronoType())) {
      case "WINDOW" -> {
        trigger.setStatus("FIRED");
        trigger.setNextTriggerAt(null);
      }
      case "RHYTHM" -> trigger.setNextTriggerAt(nextRhythm(trigger, now));
      case "PERIODIC" -> trigger.setNextTriggerAt(nextPeriodic(trigger, now));
      case "SEASONAL" -> trigger.setNextTriggerAt(nextSeasonal(trigger, now));
      default -> trigger.setStatus("COMPLETED");
    }
    return triggerRepository.save(trigger);
  }

  /**
   * 干预回执（涟漪消解闭环核心）：患者/家属对一次触达的响应登记。
   * - RESOLVED 已缓解：WINDOW 类终结为 RESOLVED；周期/节律类保持滚动但本轮回执闭环；
   * - UNRESOLVED 未缓解：保持 ACTIVE 并将下次触达提前到2小时后（加强守护）；
   * - ESCALATED 已升级就医：WINDOW/一次性 → ESCALATED 终结；周期类保持滚动。
   */
  public ChronoTrigger feedback(Long triggerId, String outcome, String note) {
    ChronoTrigger trigger = triggerRepository.findById(triggerId)
        .orElseThrow(() -> new IllegalArgumentException("触达计划不存在: " + triggerId));
    LocalDateTime now = LocalDateTime.now();
    trigger.setFeedbackStatus(outcome);
    trigger.setFeedbackNote(note == null ? "" : note);
    trigger.setFeedbackAt(now);
    switch (outcome) {
      case "RESOLVED" -> {
        if ("WINDOW".equals(trigger.getChronoType()) || "FIRED".equals(trigger.getStatus())) {
          trigger.setStatus("RESOLVED");
          trigger.setNextTriggerAt(null);
        }
      }
      case "UNRESOLVED" -> {
        // 未缓解：2小时后加强触达（守护加压）
        if (!"WINDOW".equals(trigger.getChronoType())) {
          trigger.setNextTriggerAt(now.plusHours(2));
        }
      }
      case "ESCALATED" -> {
        if ("WINDOW".equals(trigger.getChronoType())) {
          trigger.setStatus("ESCALATED");
          trigger.setNextTriggerAt(null);
        }
      }
      default -> throw new IllegalArgumentException(
          "非法回执结果: " + outcome + "（允许 RESOLVED/UNRESOLVED/ESCALATED）");
    }
    return triggerRepository.save(trigger);
  }

  public List<ChronoTrigger> findByPatient(Long patientId) {
    return triggerRepository.findByPatientIdOrderByNextTriggerAtAsc(patientId);
  }

  /* ================= 时间学计算 ================= */

  LocalDateTime computeNext(ChronoRule rule, LocalDateTime base) {
    return switch (String.valueOf(rule.getChronoType())) {
      case "WINDOW" -> base; // 窗口期：即时触达
      case "RHYTHM" -> nextRhythmHour(rule.getStartHour(), base);
      case "PERIODIC" -> base.plusDays(rule.getOffsetDays() == null ? 1 : rule.getOffsetDays());
      case "SEASONAL" -> nextSeasonMonth(rule.getTargetMonth(), base);
      default -> base.plusDays(1);
    };
  }

  private LocalDateTime computeNext(ChronoTrigger trigger, LocalDateTime from) {
    return switch (String.valueOf(trigger.getChronoType())) {
      case "WINDOW" -> from;
      case "RHYTHM" -> nextRhythmHour(parseStartHour(trigger.getTriggerTime()), from);
      case "PERIODIC" -> from.plusDays(1);
      case "SEASONAL" -> nextSeasonMonth(parseTargetMonth(trigger.getTriggerTime()), from);
      default -> from.plusDays(1);
    };
  }

  private LocalDateTime nextRhythm(ChronoTrigger trigger, LocalDateTime now) {
    Integer hour = parseStartHour(trigger.getTriggerTime());
    return nextRhythmHour(hour, now.plusMinutes(1));
  }

  private LocalDateTime nextPeriodic(ChronoTrigger trigger, LocalDateTime now) {
    Integer period = parsePeriodDays(trigger);
    if (period == null) {
      trigger.setStatus("COMPLETED");
      return null;
    }
    return now.plusDays(period);
  }

  private LocalDateTime nextSeasonal(ChronoTrigger trigger, LocalDateTime now) {
    Integer month = parseTargetMonth(trigger.getTriggerTime());
    return nextSeasonMonth(month, now.plusDays(1));
  }

  /** RHYTHM：今天 startHour 未过则今天，否则明天。 */
  private LocalDateTime nextRhythmHour(Integer startHour, LocalDateTime base) {
    int hour = startHour == null ? 8 : startHour;
    LocalDateTime today = base.toLocalDate().atTime(LocalTime.of(hour, 0));
    return today.isAfter(base) ? today : today.plusDays(1);
  }

  /** SEASONAL：目标月份的1日；已过则明年。 */
  private LocalDateTime nextSeasonMonth(Integer targetMonth, LocalDateTime base) {
    int month = targetMonth == null ? 9 : targetMonth;
    int year = base.getYear();
    LocalDate candidate = LocalDate.of(year, month, 1);
    LocalDateTime at = candidate.atStartOfDay();
    return at.isAfter(base) ? at : at.plusYears(1);
  }

  /** 从 triggerTime 文本解析起始小时（如"凌晨0-3点"→0，"凌晨4-6点"→4）。 */
  static Integer parseStartHour(String triggerTime) {
    if (triggerTime == null) {
      return null;
    }
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})\\s*[-~到至]?")
        .matcher(triggerTime);
    if (matcher.find()) {
      try {
        return Math.min(23, Math.max(0, Integer.parseInt(matcher.group(1))));
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  /** 从 triggerTime 文本解析季节目标月份（如"入秋/秋冬换季"→9，"春季"→3，"入冬"→11）。 */
  static Integer parseTargetMonth(String triggerTime) {
    if (triggerTime == null) {
      return null;
    }
    if (triggerTime.contains("春")) {
      return 3;
    }
    if (triggerTime.contains("冬")) {
      return 11;
    }
    if (triggerTime.contains("夏")) {
      return 6;
    }
    // 秋/换季默认9月
    return 9;
  }

  /** PERIODIC 周期滚动：从事件文本推断周期天数（每月=30，每季=90，每年=365），无周期语义则完结。 */
  static Integer parsePeriodDays(ChronoTrigger trigger) {
    String text = trigger.getEvent() == null ? "" : trigger.getEvent();
    if (text.contains("每年") || text.contains("年度")) {
      return 365;
    }
    if (text.contains("每季") || text.contains("季度")) {
      return 90;
    }
    if (text.contains("每月") || text.contains("每月")) {
      return 30;
    }
    return null;
  }
}
