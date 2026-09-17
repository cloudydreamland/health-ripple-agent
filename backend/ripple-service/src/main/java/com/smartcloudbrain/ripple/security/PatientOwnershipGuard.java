package com.smartcloudbrain.ripple.security;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.common.security.AuthenticatedUser;
import com.smartcloudbrain.common.security.CurrentUserService;
import com.smartcloudbrain.common.security.RoleType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 患者数据归属守卫（防 IDOR：Insecure Direct Object Reference）。
 *
 * 涟漪域的全部数据（推演历史/回执/证据/时间学计划/健康气象）都以 patientId 为主键，
 * 若不校验归属，任何登录患者改一个 ID 即可遍历他人的医疗数据——医疗隐私的一票否决项。
 *
 * 策略（两层，与部署形态对齐）：
 * - 请求经网关（有 X-User-Id 身份头）：PATIENT 角色只能访问自己 patientId 的数据，
 *   不匹配即 403；DOCTOR/ADMIN 因诊疗关系可跨患者访问（与挂号/病历服务同规则）。
 * - 无身份头（DuMate Skill 沙箱本地直连后端，未过网关）：放行，由
 *   {@link RippleSecurityGuard} 的写令牌与网关白名单承担边界——
 *   本地沙箱是受信执行环境，这与"本地安全沙箱"的平台设计一致。
 */
@Component
public class PatientOwnershipGuard {

  private final CurrentUserService currentUserService;
  private final HttpServletRequest request;

  public PatientOwnershipGuard(CurrentUserService currentUserService, HttpServletRequest request) {
    this.currentUserService = currentUserService;
    this.request = request;
  }

  /** 校验当前请求有权访问目标 patientId 的数据；PATIENT 身份不匹配抛 403。 */
  public void checkAccess(Long patientId) {
    AuthenticatedUser user = currentUserOrNull();
    if (user == null) {
      return; // 沙箱直连（无网关身份头）：由写令牌/网络边界兜底
    }
    if (user.role() == RoleType.PATIENT && !user.userId().equals(patientId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }

  /** 校验当前请求有权访问属于目标 patientId 的单条资源；resourcePatientId 为空时按无归属处理。 */
  public void checkResource(Long resourcePatientId) {
    AuthenticatedUser user = currentUserOrNull();
    if (user == null) {
      return;
    }
    if (user.role() == RoleType.PATIENT
        && (resourcePatientId == null || !user.userId().equals(resourcePatientId))) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }

  private AuthenticatedUser currentUserOrNull() {
    if (request.getHeader(com.smartcloudbrain.common.security.UserContextHeaders.USER_ID) == null) {
      return null;
    }
    return currentUserService.get();
  }
}
