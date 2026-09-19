package com.smartcloudbrain.ripple.service;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 家属守护圈（Family Guardian Circle，第九轮创新）：把守护态势安全地分享给家属。
 *
 * <p>安全设计（每一项都可被追问）：
 * <ul>
 *   <li>令牌 = base64url(patientId.expiry) + HMAC-SHA256 签名——无状态、无需新表；
 *       篡改 patientId 或过期时间都会导致签名失配；</li>
 *   <li>签名比较用常数时间 MessageDigest.isEqual，防时序侧信道；</li>
 *   <li>令牌带过期（默认7天，最长30天），过期即失效；泄露影响的只是一个只读视图；</li>
 *   <li><b>最小披露</b>：家属视图只含守护态势（天气等级/ headline/ 家属须知/ 72h趋势形状/ 消解率），
 *       <b>不含</b>患者姓名、诊断原文、决策ID、哈希——家属需要知道"怎么守护"，
 *       而不是"病历写了什么"。知情权与隐私权的边界在这里显式划清。</li>
 * </ul>
 */
@Service
public class FamilyShareService {

  private static final long MAX_DAYS = 30;
  private final String secret;
  private final HealthWeatherService healthWeatherService;
  private final RippleForecastService forecastService;
  private final RippleClosureService closureService;

  public FamilyShareService(
      @Value("${ripple.share.secret:${RIPPLE_SHARE_SECRET:local-dev-share-secret-change-me}}") String secret,
      HealthWeatherService healthWeatherService,
      RippleForecastService forecastService,
      RippleClosureService closureService) {
    this.secret = secret;
    this.healthWeatherService = healthWeatherService;
    this.forecastService = forecastService;
    this.closureService = closureService;
  }

  /** 生成家属分享令牌（days 有效期，1–30天）。 */
  public Map<String, Object> createLink(Long patientId, int days) {
    if (days < 1 || days > MAX_DAYS) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    long expiry = Instant.now().getEpochSecond() + days * 86400L;
    String payload = patientId + "." + expiry;
    String token = base64Url(payload) + "." + hmacHex(payload);
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("token", token);
    view.put("path", "/share/" + token);
    view.put("expiresAt", Instant.ofEpochSecond(expiry).toString());
    view.put("disclosure", "最小披露：仅守护态势（天气/趋势/家属须知/消解率），不含姓名、诊断与决策记录");
    return view;
  }

  /** 校验令牌并返回 patientId；签名不符/过期一律拒绝且不泄露原因区别。 */
  public Long resolveToken(String token) {
    if (token == null || token.isBlank()) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    String[] parts = token.split("\\.");
    if (parts.length != 2) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    String payload;
    try {
      payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    String expected = hmacHex(payload);
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    int dot = payload.indexOf('.');
    if (dot <= 0) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    long expiry;
    try {
      expiry = Long.parseLong(payload.substring(dot + 1));
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (Instant.now().getEpochSecond() > expiry) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    try {
      return Long.parseLong(payload.substring(0, dot));
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }

  /** 家属只读视图（最小披露口径，见类注释）。 */
  public Map<String, Object> familyView(Long patientId) {
    Map<String, Object> weather = healthWeatherService.daily(patientId);
    Map<String, Object> forecast = forecastService.forecast(patientId);
    Map<String, Object> resolution = closureService.resolution(patientId);

    Map<String, Object> view = new LinkedHashMap<>();
    view.put("weather", weather.get("weather"));
    view.put("weatherLabel", weather.get("weatherLabel"));
    view.put("index", weather.get("index"));
    view.put("headline", weather.get("headline"));
    view.put("familyTip", weather.get("familyTip"));
    view.put("dueTodayCount", weather.get("dueTodayCount"));
    // 72h 趋势只给形状（强度值），不给驱动事件名——趋势是"什么时候要当心"，事件名在患者自己的页面里
    view.put("trend", ((java.util.List<?>) forecast.get("buckets")).stream()
        .map(b -> ((Map<?, ?>) b).get("intensity"))
        .toList());
    view.put("resolutionRate", resolution == null ? 0 : resolution.get("resolutionRate"));
    view.put("model", "家属守护圈·只读最小披露视图：守护态势实时计算，不含姓名/诊断/决策记录");
    return view;
  }

  private String base64Url(String value) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private String hmacHex(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return java.util.HexFormat.of().formatHex(
          mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("分享令牌签名失败", e);
    }
  }
}
