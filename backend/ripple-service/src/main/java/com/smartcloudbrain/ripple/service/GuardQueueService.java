package com.smartcloudbrain.ripple.service;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.common.security.AuthenticatedUser;
import com.smartcloudbrain.common.security.CurrentUserService;
import com.smartcloudbrain.common.security.RoleType;
import com.smartcloudbrain.ripple.entity.RippleEvent;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import com.smartcloudbrain.ripple.repository.RippleEventRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 今日守护队列（Guard Queue，第六轮创新：把"事件级健康操作系统"落到基层医生的每天）。
 *
 * 基层医生早上最需要回答的问题不是"系统能做什么"，而是"今天我该先管谁"。
 * 本服务跨患者聚合守护态势并按优先级排序：
 *   优先级分 = 健康气象指数 + 25×今日到期触达数 + 40×近24h升级就医 + 10×近24h未缓解
 * （升级就医权重最高——已经出事的患者永远排第一；其余权重可按机构策略调整，公式随响应输出。）
 *
 * 访问控制：仅 DOCTOR/ADMIN 可调用（诊疗关系跨患者），PATIENT 返回 403——
 * 与患者数据归属校验（PatientOwnershipGuard）互为正反面：患者看自己，医生看全队列。
 */
@Service
public class GuardQueueService {

  private static final int MAX_QUEUE_SIZE = 10;

  private final ChronoTriggerRepository triggerRepository;
  private final RippleEventRepository rippleEventRepository;
  private final HealthWeatherService healthWeatherService;
  private final CurrentUserService currentUserService;

  public GuardQueueService(ChronoTriggerRepository triggerRepository,
      RippleEventRepository rippleEventRepository,
      HealthWeatherService healthWeatherService,
      CurrentUserService currentUserService) {
    this.triggerRepository = triggerRepository;
    this.rippleEventRepository = rippleEventRepository;
    this.healthWeatherService = healthWeatherService;
    this.currentUserService = currentUserService;
  }

  /** 今日守护队列：跨患者按优先级排序（仅医生/管理员）。 */
  public List<Map<String, Object>> todayQueue() {
    AuthenticatedUser user = currentUserService.get();
    if (user.role() == RoleType.PATIENT) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    List<Map<String, Object>> queue = new ArrayList<>();
    for (Long patientId : triggerRepository.findDistinctPatientIds()) {
      Map<String, Object> weather = healthWeatherService.daily(patientId);
      int dueToday = parseInt(weather.get("dueTodayCount"));
      int escalated = parseInt(weather.get("escalatedCount"));
      int unresolved = parseInt(weather.get("unresolvedRecent"));
      double index = parseDouble(weather.get("index"));
      double score = index + 25.0 * dueToday + 40.0 * escalated + 10.0 * unresolved;

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("patientId", patientId);
      row.put("priorityScore", Math.round(score * 10.0) / 10.0);
      row.put("weather", weather.get("weather"));
      row.put("weatherLabel", weather.get("weatherLabel"));
      row.put("guardIndex", index);
      row.put("dueTodayCount", dueToday);
      row.put("escalatedRecent", escalated);
      row.put("unresolvedRecent", unresolved);
      row.put("reason", buildReason(weather.get("headline"), escalated, unresolved, dueToday));
      latestEventOf(patientId, row);
      queue.add(row);
    }
    queue.sort((a, b) -> Double.compare((Double) b.get("priorityScore"), (Double) a.get("priorityScore")));
    return queue.size() > MAX_QUEUE_SIZE ? new ArrayList<>(queue.subList(0, MAX_QUEUE_SIZE)) : queue;
  }

  private void latestEventOf(Long patientId, Map<String, Object> row) {
    List<RippleEvent> events = rippleEventRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    if (!events.isEmpty()) {
      RippleEvent latest = events.get(0);
      row.put("latestDiagnosis", latest.getDiagnosis());
      row.put("latestEventAt", latest.getCreatedAt() == null ? "" : latest.getCreatedAt().toString());
    } else {
      row.put("latestDiagnosis", "");
      row.put("latestEventAt", "");
    }
  }

  private String buildReason(Object headline, int escalated, int unresolved, int dueToday) {
    List<String> reasons = new ArrayList<>();
    if (escalated > 0) {
      reasons.add(escalated + "项已升级就医需跟进");
    }
    if (unresolved > 0) {
      reasons.add(unresolved + "项未缓解守护加压中");
    }
    if (dueToday > 0) {
      reasons.add(dueToday + "项触达今日到期");
    }
    if (reasons.isEmpty()) {
      reasons.add(String.valueOf(headline));
    }
    return String.join("；", reasons);
  }

  private static int parseInt(Object value) {
    return value instanceof Number n ? n.intValue() : 0;
  }

  private static double parseDouble(Object value) {
    return value instanceof Number n ? n.doubleValue() : 0.0;
  }
}
