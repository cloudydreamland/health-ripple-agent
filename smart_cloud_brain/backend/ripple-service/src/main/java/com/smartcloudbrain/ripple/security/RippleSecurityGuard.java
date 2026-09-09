package com.smartcloudbrain.ripple.security;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 涟漪服务写操作守卫（安全边界）。
 *
 * 本地演示（DuMate Skill 沙箱直连）默认关闭令牌校验；
 * 生产部署置 RIPPLE_WRITE_TOKEN_REQUIRED=true + 内部令牌，
 * 写操作（涟漪推演/MDT会诊）必须携带 X-Internal-Token，防止未授权生成诊疗建议。
 * 常数时间比较防时序侧信道（与 common-lib InternalRequestGuard 同标准）。
 */
@Component
public class RippleSecurityGuard {

  public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

  private final HttpServletRequest request;
  private final boolean writeTokenRequired;
  private final String expectedToken;

  public RippleSecurityGuard(
      HttpServletRequest request,
      @Value("${ripple.security.write-token-required:false}") boolean writeTokenRequired,
      @Value("${internal.service-token:${INTERNAL_SERVICE_TOKEN:smart-cloud-brain-internal-local-token-change}}")
      String expectedToken) {
    this.request = request;
    this.writeTokenRequired = writeTokenRequired;
    this.expectedToken = expectedToken;
  }

  public void guardWrite() {
    if (!writeTokenRequired) {
      return;
    }
    String actual = request.getHeader(INTERNAL_TOKEN_HEADER);
    if (isBlank(expectedToken) || isBlank(actual) || !constantTimeEquals(expectedToken, actual)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
  }

  private boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        actual.getBytes(StandardCharsets.UTF_8));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
