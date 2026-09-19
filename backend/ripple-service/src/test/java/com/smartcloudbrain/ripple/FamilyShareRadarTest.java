package com.smartcloudbrain.ripple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import com.smartcloudbrain.ripple.service.CommunityRadarService;
import com.smartcloudbrain.ripple.service.FamilyShareService;
import com.smartcloudbrain.ripple.service.HealthWeatherService;
import com.smartcloudbrain.ripple.service.RippleClosureService;
import com.smartcloudbrain.ripple.service.RippleForecastService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 第九轮创新单测：家属守护圈（HMAC分享令牌）+ 社区涟漪雷达。
 *
 * 守护圈关键性质：
 * 1. 令牌往返：create→resolve 得到同一 patientId；
 * 2. 篡改 patientId / 过期时间 / 签名 → 一律拒绝（FORBIDDEN）；
 * 3. 过期令牌拒绝；
 * 4. 家属视图最小披露：不含 patientId/诊断/决策字段，含天气+趋势+家属须知+消解率。
 * 雷达关键性质：
 * 5. 聚合正确（未缓解/升级计数、信号强度权重、患者数去重）；
 * 6. 患者角色 403。
 */
class FamilyShareServiceTest {

  private FamilyShareService newShareService() {
    HealthWeatherService weather = mock(HealthWeatherService.class);
    RippleForecastService forecast = mock(RippleForecastService.class);
    RippleClosureService closure = mock(RippleClosureService.class);
    when(weather.daily(7L)).thenReturn(Map.of(
        "weather", "STORM", "weatherLabel", "暴雨·高度警戒", "index", 37.1,
        "headline", "今日3项守护", "familyTip", "家属需识别低血糖症状", "dueTodayCount", 3));
    when(forecast.forecast(7L)).thenReturn(Map.of(
        "buckets", List.of(Map.of("hourOffset", 0, "intensity", 20.0, "drivers", List.of("夜间低血糖")),
            Map.of("hourOffset", 1, "intensity", 8.0, "drivers", List.<String>of()))));
    when(closure.resolution(anyLong())).thenReturn(Map.of("resolutionRate", 42));
    return new FamilyShareService("test-secret", weather, forecast, closure);
  }

  @Test
  void share_tokenRoundTripResolvesSamePatient() {
    FamilyShareService service = newShareService();
    Map<String, Object> link = service.createLink(7L, 7);
    String token = String.valueOf(link.get("token"));
    assertEquals(7L, service.resolveToken(token));
    assertTrue(String.valueOf(link.get("expiresAt")).length() > 0);
  }

  @Test
  void share_rejectsTamperedAndExpiredTokens() {
    FamilyShareService service = newShareService();
    String token = String.valueOf(service.createLink(7L, 7).get("token"));
    String payload = token.split("\\.")[0];

    // 换患者（payload 改动 → 签名失配）
    String forged = base64("8." + (System.currentTimeMillis() / 1000 + 3600)) + "." + token.split("\\.")[1];
    assertThrows(BusinessException.class, () -> service.resolveToken(forged));
    // 签名段篡改
    assertThrows(BusinessException.class, () -> service.resolveToken(payload + ".deadbeef"));
    // 过期
    String expired = base64("7." + (System.currentTimeMillis() / 1000 - 10)) + "." + token.split("\\.")[1];
    assertThrows(BusinessException.class, () -> service.resolveToken(expired));
    // 垃圾
    assertThrows(BusinessException.class, () -> service.resolveToken("nonsense"));
  }

  @Test
  void share_daysRangeValidated() {
    FamilyShareService service = newShareService();
    assertThrows(BusinessException.class, () -> service.createLink(7L, 0));
    assertThrows(BusinessException.class, () -> service.createLink(7L, 31));
  }

  @Test
  void share_familyViewIsMinimalDisclosure() {
    FamilyShareService service = newShareService();
    Map<String, Object> view = service.familyView(7L);
    assertEquals("STORM", view.get("weather"));
    assertEquals("家属需识别低血糖症状", view.get("familyTip"));
    // 最小披露：绝不出现患者ID/诊断/驱动事件名/哈希等字段
    assertFalse(view.containsKey("patientId"));
    assertFalse(view.containsKey("diagnosis"));
    assertFalse(view.containsKey("drivers"));
    assertFalse(view.containsKey("hash"));
    assertTrue(String.valueOf(view.get("model")).contains("最小披露"));
  }

  private String base64(String value) {
    return java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void radar_aggregatesAndGuardsRole() {
    ChronoTriggerRepository repo = mock(ChronoTriggerRepository.class);
    com.smartcloudbrain.common.security.CurrentUserService currentUser =
        mock(com.smartcloudbrain.common.security.CurrentUserService.class);
    CommunityRadarService radar = new CommunityRadarService(repo, currentUser);

    LocalDateTime now = LocalDateTime.now();
    ChronoTrigger a1 = trigger(1L, "夜间0-3点低血糖高发", "ACTIVE", null, null);
    ChronoTrigger a2 = trigger(2L, "夜间0-3点低血糖高发", "ACTIVE", "UNRESOLVED", now.minusDays(1));
    ChronoTrigger a3 = trigger(3L, "夜间0-3点低血糖高发", "ACTIVE", "ESCALATED", now.minusDays(2));
    ChronoTrigger a4 = trigger(2L, "换季血糖波动", "ACTIVE", null, null);
    // 窗口外（8天前）的升级不应计入
    ChronoTrigger a5 = trigger(4L, "夜间0-3点低血糖高发", "ESCALATED", "ESCALATED", now.minusDays(8));
    when(repo.findAll()).thenReturn(List.of(a1, a2, a3, a4, a5));
    when(currentUser.get()).thenReturn(new com.smartcloudbrain.common.security.AuthenticatedUser(
        9L, com.smartcloudbrain.common.security.RoleType.DOCTOR, "张医生"));

    Map<String, Object> view = radar.radar();
    assertEquals(4, ((Number) view.get("patientsMonitored")).intValue());
    assertEquals(1, ((Number) view.get("unresolved7d")).intValue());
    assertEquals(1, ((Number) view.get("escalated7d")).intValue());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> signals = (List<Map<String, Object>>) view.get("signals");
    assertEquals("夜间0-3点低血糖高发", signals.get(0).get("event"), "信号最强的守护事项应排第一");
    assertEquals(3, ((Number) signals.get(0).get("patientCount")).intValue(), "覆盖患者去重计数");
    assertEquals(3 + 3 * 1 + 5 * 1, ((Number) signals.get(0).get("signalScore")).intValue());

    // 患者角色 403
    when(currentUser.get()).thenReturn(new com.smartcloudbrain.common.security.AuthenticatedUser(
        1L, com.smartcloudbrain.common.security.RoleType.PATIENT, "患者"));
    assertThrows(BusinessException.class, () -> radar.radar());
  }

  private ChronoTrigger trigger(Long patientId, String event, String status, String feedback, LocalDateTime feedbackAt) {
    ChronoTrigger t = new ChronoTrigger();
    t.setPatientId(patientId);
    t.setEvent(event);
    t.setStatus(status);
    t.setFeedbackStatus(feedback);
    t.setFeedbackAt(feedbackAt);
    t.setChronoType("RHYTHM");
    t.setNextTriggerAt(LocalDateTime.now().plusHours(2));
    return t;
  }
}
