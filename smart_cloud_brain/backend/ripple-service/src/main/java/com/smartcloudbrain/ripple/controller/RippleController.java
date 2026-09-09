package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.dto.RippleDeriveRequest;
import com.smartcloudbrain.ripple.security.RippleSecurityGuard;
import com.smartcloudbrain.ripple.service.RippleDeriveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 健康事件涟漪推演 API（核心创新1入口）。 */
@RestController
@RequestMapping("/api/health-event")
public class RippleController {

  private final RippleDeriveService rippleDeriveService;
  private final RippleSecurityGuard securityGuard;

  public RippleController(RippleDeriveService rippleDeriveService, RippleSecurityGuard securityGuard) {
    this.rippleDeriveService = rippleDeriveService;
    this.securityGuard = securityGuard;
  }

  /** POST /api/health-event/ripple — 一个健康事件触发多维度涟漪推演。 */
  @PostMapping("/ripple")
  public Result<?> derive(@Valid @RequestBody RippleDeriveRequest request) {
    securityGuard.guardWrite();
    return Result.success(rippleDeriveService.derive(request));
  }

  /** GET /api/health-event/ripple/patient/{patientId} — 患者涟漪推演历史。 */
  @GetMapping("/ripple/patient/{patientId}")
  public Result<?> history(@PathVariable Long patientId) {
    return Result.success(rippleDeriveService.history(patientId));
  }
}
