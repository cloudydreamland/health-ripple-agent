package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.event.RippleEventPublisher;
import com.smartcloudbrain.ripple.service.EvidenceChainService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 反事实决策证据链 API（核心创新2入口：XAI 可审计）。
 *
 * - 查询单条证据（含反事实决策树）
 * - 查询患者全部决策证据
 * - 哈希链完整性校验（tamper-evident audit）
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

  private final EvidenceChainService evidenceChainService;
  private final RippleEventPublisher eventPublisher;

  public EvidenceController(EvidenceChainService evidenceChainService, RippleEventPublisher eventPublisher) {
    this.evidenceChainService = evidenceChainService;
    this.eventPublisher = eventPublisher;
  }

  /** GET /api/evidence/{decisionId} — 查询单条决策证据（反事实决策树可追问）。 */
  @GetMapping("/{decisionId}")
  public Result<?> byDecisionId(@PathVariable String decisionId) {
    var evidence = evidenceChainService.findByDecisionId(decisionId);
    if (evidence == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    eventPublisher.publishAudit("evidence_query",
        (Long) evidence.get("patientId"),
        (String) evidence.get("decisionType"),
        decisionId);
    return Result.success(evidence);
  }

  /** GET /api/evidence/patient/{patientId} — 患者全部决策证据。 */
  @GetMapping("/patient/{patientId}")
  public Result<?> byPatient(@PathVariable Long patientId) {
    return Result.success(evidenceChainService.findByPatient(patientId));
  }

  /**
   * GET /api/evidence/{decisionId}/fhir — 导出 HL7 FHIR R4 Provenance 兼容 JSON。
   *
   * 对齐 AI Transparency on FHIR IG（2026 ballot）：AI 决策审计记录可被
   * 医院信息系统按国际标准消费（agent=AI参与者, entity=决策依据, signature=哈希链）。
   */
  @GetMapping("/{decisionId}/fhir")
  public Result<?> asFhirProvenance(@PathVariable String decisionId) {
    return Result.success(evidenceChainService.exportFhirProvenance(decisionId));
  }

  /** GET /api/evidence/verify — 哈希链完整性校验（审计：篡改即刻暴露）。 */
  @GetMapping("/verify")
  public Result<?> verify() {
    return Result.success(evidenceChainService.verify());
  }
}
