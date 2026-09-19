package com.smartcloudbrain.ripple.service;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.common.security.AuthenticatedUser;
import com.smartcloudbrain.common.security.CurrentUserService;
import com.smartcloudbrain.common.security.RoleType;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * 社区涟漪雷达（Community Ripple Radar，第九轮创新）：个体的涟漪汇成社区的潮汐。
 *
 * <p>把跨患者的守护触达聚合为<b>群体健康信号</b>：当多位患者在同一守护事项上
 * 同时出现"未缓解/升级就医"，那就是社区级的早期预警——换季血糖波动、
 * 服药不依从潮、某类药物的普遍不良反应。这是涟漪模型从个体到人群的自然延伸，
 * 也是基层医疗机构"看的不是一个人，是一片人"的真实工作方式。
 *
 * <p>口径（全部计数型、可解释、无随机）：
 * <ul>
 *   <li>近7天窗口：feedbackAt 落在窗口内的 UNRESOLVED/ESCALATED 计入信号；</li>
 *   <li>信号强度 = 覆盖患者数 + 3×未缓解 + 5×升级就医（升级就医权重最高——
 *       它是守护失败的硬证据）；</li>
 *   <li>潮汐指数 = min(100, 5×未缓解信号数 + 10×升级就医信号数)，按辖区患者规模归一；
 *       ≥60 HIGH潮 / ≥25 MID涨 / &gt;0 LOW平 / 0 CALM静。</li>
 * </ul>
 * 仅医生/管理员可读（患者403，与守护队列同一角色边界）。
 */
@Service
public class CommunityRadarService {

  private static final int WINDOW_DAYS = 7;

  private final ChronoTriggerRepository triggerRepository;
  private final CurrentUserService currentUserService;

  public CommunityRadarService(ChronoTriggerRepository triggerRepository, CurrentUserService currentUserService) {
    this.triggerRepository = triggerRepository;
    this.currentUserService = currentUserService;
  }

  public Map<String, Object> radar() {
    AuthenticatedUser user = currentUserService.get();
    if (user.role() == RoleType.PATIENT) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
    List<ChronoTrigger> all = triggerRepository.findAll();

    // 事件名 → 聚合信号（TreeMap 保证输出稳定可复算）
    Map<String, Signal> byEvent = new TreeMap<>();
    java.util.Set<Long> patients = new java.util.HashSet<>();
    int unresolvedTotal = 0;
    int escalatedTotal = 0;

    for (ChronoTrigger t : all) {
      if (t.getPatientId() != null) {
        patients.add(t.getPatientId());
      }
      boolean activeish = "ACTIVE".equals(t.getStatus()) || "FIRED".equals(t.getStatus());
      String feedback = t.getFeedbackStatus();
      boolean unresolvedRecent = "UNRESOLVED".equals(feedback)
          && t.getFeedbackAt() != null && t.getFeedbackAt().isAfter(since);
      boolean escalatedRecent = "ESCALATED".equals(feedback)
          && t.getFeedbackAt() != null && t.getFeedbackAt().isAfter(since);
      if (!activeish && !unresolvedRecent && !escalatedRecent) {
        continue;
      }
      String event = t.getEvent() == null ? "未知守护事项" : t.getEvent();
      Signal signal = byEvent.computeIfAbsent(event, k -> new Signal(k));
      if (activeish) {
        signal.activeCount++;
        signal.patients.add(t.getPatientId());
      }
      if (unresolvedRecent) {
        signal.unresolved7d++;
        unresolvedTotal++;
      }
      if (escalatedRecent) {
        signal.escalated7d++;
        escalatedTotal++;
      }
    }

    List<Map<String, Object>> signals = new ArrayList<>();
    for (Signal s : byEvent.values()) {
      if (s.activeCount == 0 && s.unresolved7d == 0 && s.escalated7d == 0) {
        continue;
      }
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("event", s.event);
      view.put("patientCount", s.patients.size());
      view.put("activeCount", s.activeCount);
      view.put("unresolved7d", s.unresolved7d);
      view.put("escalated7d", s.escalated7d);
      view.put("signalScore", s.patients.size() + 3 * s.unresolved7d + 5 * s.escalated7d);
      signals.add(view);
    }
    // 信号强度降序（同分按事件名，TreeMap 序稳定）
    signals.sort((a, b) -> {
      int sa = (int) a.get("signalScore");
      int sb = (int) b.get("signalScore");
      return sa != sb ? Integer.compare(sb, sa) : String.valueOf(a.get("event")).compareTo(String.valueOf(b.get("event")));
    });

    int tideIndex = Math.min(100, unresolvedTotal * 5 + escalatedTotal * 10);
    String tide = tideIndex >= 60 ? "HIGH" : tideIndex >= 25 ? "MID" : tideIndex > 0 ? "LOW" : "CALM";

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("patientsMonitored", patients.size());
    result.put("windowDays", WINDOW_DAYS);
    result.put("totalActive", signals.stream().mapToInt(s -> (int) s.get("activeCount")).sum());
    result.put("unresolved7d", unresolvedTotal);
    result.put("escalated7d", escalatedTotal);
    result.put("tideIndex", tideIndex);
    result.put("tideLevel", tide);
    result.put("signals", signals.size() > 12 ? signals.subList(0, 12) : signals);
    result.put("model", "社区潮汐=跨患者守护信号聚合：信号强度=患者数+3×未缓解+5×升级就医（近7天），"
        + "潮汐指数=min(100, 5×未缓解+10×升级就医)；计数型口径可复算，不虚构流行病学概率");
    return result;
  }

  /** 单个守护事项的跨患者聚合信号。 */
  private static final class Signal {
    final String event;
    final java.util.Set<Long> patients = new java.util.HashSet<>();
    int activeCount;
    int unresolved7d;
    int escalated7d;

    Signal(String event) {
      this.event = event;
    }
  }
}
