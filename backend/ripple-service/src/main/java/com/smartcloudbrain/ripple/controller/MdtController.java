package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.dto.MdtConsultRequest;
import com.smartcloudbrain.ripple.security.RippleSecurityGuard;
import com.smartcloudbrain.ripple.service.MdtService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 多智能体 MDT 会诊 API（核心创新3入口）。 */
@RestController
@RequestMapping("/api/mdt")
public class MdtController {

  private final MdtService mdtService;
  private final RippleSecurityGuard securityGuard;

  public MdtController(MdtService mdtService, RippleSecurityGuard securityGuard) {
    this.mdtService = mdtService;
    this.securityGuard = securityGuard;
  }

  /** POST /api/mdt/consult — 疑难病例五 Agent 多视角会诊。 */
  @PostMapping("/consult")
  public Result<?> consult(@Valid @RequestBody MdtConsultRequest request) {
    securityGuard.guardWrite();
    return Result.success(mdtService.consult(request));
  }

  /** GET /api/mdt/consult/patient/{patientId} — 患者 MDT 会诊历史。 */
  @GetMapping("/consult/patient/{patientId}")
  public Result<?> history(@PathVariable Long patientId) {
    return Result.success(mdtService.history(patientId));
  }
}
