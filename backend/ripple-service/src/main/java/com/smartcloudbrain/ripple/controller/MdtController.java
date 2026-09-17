package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.dto.MdtConsultRequest;
import com.smartcloudbrain.ripple.security.PatientOwnershipGuard;
import com.smartcloudbrain.ripple.security.RippleSecurityGuard;
import com.smartcloudbrain.ripple.service.MdtService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 多智能体 MDT 会诊 API（核心创新3入口）。患者维度端点经归属校验。 */
@RestController
@RequestMapping("/api/mdt")
public class MdtController {

  private final MdtService mdtService;
  private final RippleSecurityGuard securityGuard;
  private final PatientOwnershipGuard ownershipGuard;

  public MdtController(MdtService mdtService, RippleSecurityGuard securityGuard,
      PatientOwnershipGuard ownershipGuard) {
    this.mdtService = mdtService;
    this.securityGuard = securityGuard;
    this.ownershipGuard = ownershipGuard;
  }

  /** POST /api/mdt/consult — 疑难病例五 Agent 多视角会诊。 */
  @PostMapping("/consult")
  public Result<?> consult(@Valid @RequestBody MdtConsultRequest request) {
    securityGuard.guardWrite();
    ownershipGuard.checkAccess(request.patientId());
    return Result.success(mdtService.consult(request));
  }

  /** GET /api/mdt/consult/patient/{patientId} — 患者 MDT 会诊历史。 */
  @GetMapping("/consult/patient/{patientId}")
  public Result<?> history(@PathVariable Long patientId) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(mdtService.history(patientId));
  }
}
