package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.dto.RippleDeriveRequest;
import com.smartcloudbrain.ripple.security.PatientOwnershipGuard;
import com.smartcloudbrain.ripple.security.RippleSecurityGuard;
import com.smartcloudbrain.ripple.service.RippleDeriveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康事件涟漪推演 API（核心创新1入口）。
 * 患者维度端点统一经 {@link PatientOwnershipGuard} 归属校验（防越权遍历他人医疗数据）。
 */
@RestController
@RequestMapping("/api/health-event")
public class RippleController {

  private final RippleDeriveService rippleDeriveService;
  private final com.smartcloudbrain.ripple.service.RippleClosureService closureService;
  private final RippleSecurityGuard securityGuard;
  private final PatientOwnershipGuard ownershipGuard;

  public RippleController(RippleDeriveService rippleDeriveService,
      com.smartcloudbrain.ripple.service.RippleClosureService closureService,
      RippleSecurityGuard securityGuard,
      PatientOwnershipGuard ownershipGuard) {
    this.rippleDeriveService = rippleDeriveService;
    this.closureService = closureService;
    this.securityGuard = securityGuard;
    this.ownershipGuard = ownershipGuard;
  }

  /** POST /api/health-event/ripple — 一个健康事件触发多维度涟漪推演。 */
  @PostMapping("/ripple")
  public Result<?> derive(@Valid @RequestBody RippleDeriveRequest request) {
    securityGuard.guardWrite();
    ownershipGuard.checkAccess(request.patientId());
    return Result.success(rippleDeriveService.derive(request));
  }

  /** GET /api/health-event/ripple/patient/{patientId} — 患者涟漪推演历史。 */
  @GetMapping("/ripple/patient/{patientId}")
  public Result<?> history(@PathVariable Long patientId) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(rippleDeriveService.history(patientId));
  }

  /** GET /api/health-event/ripple/resolution — 患者级干预回执统计与涟漪消解率。 */
  @GetMapping("/ripple/resolution")
  public Result<?> resolution(@RequestParam Long patientId) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(closureService.resolution(patientId));
  }

  /** GET /api/health-event/ripple/feedback-ledger — 患者全部触达的回执明细（患者端/家属端列表）。 */
  @GetMapping("/ripple/feedback-ledger")
  public Result<?> feedbackLedger(@RequestParam Long patientId) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(closureService.feedbackLedger(patientId));
  }
}
