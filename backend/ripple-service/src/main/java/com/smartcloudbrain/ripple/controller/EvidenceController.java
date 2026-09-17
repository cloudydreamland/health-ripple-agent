package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.event.RippleEventPublisher;
import com.smartcloudbrain.ripple.security.PatientOwnershipGuard;
import com.smartcloudbrain.ripple.service.EvidenceChainService;
import com.smartcloudbrain.ripple.service.GuardianLedgerService;
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
 * - 守护价值账本（计数型守护动作聚合）
 *
 * 患者维度端点统一经 {@link PatientOwnershipGuard} 归属校验（防越权遍历他人医疗数据）。
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

  private final EvidenceChainService evidenceChainService;
  private final RippleEventPublisher eventPublisher;
  private final PatientOwnershipGuard ownershipGuard;
  private final GuardianLedgerService guardianLedgerService;

  public EvidenceController(EvidenceChainService evidenceChainService, RippleEventPublisher eventPublisher,
      PatientOwnershipGuard ownershipGuard, GuardianLedgerService guardianLedgerService) {
    this.evidenceChainService = evidenceChainService;
    this.eventPublisher = eventPublisher;
    this.ownershipGuard = ownershipGuard;
    this.guardianLedgerService = guardianLedgerService;
  }

  /** GET /api/evidence/{decisionId} — 查询单条决策证据（反事实决策树可追问）。 */
  @GetMapping("/{decisionId}")
  public Result<?> byDecisionId(@PathVariable String decisionId) {
    var evidence = evidenceChainService.findByDecisionId(decisionId);
    if (evidence == null) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    ownershipGuard.checkResource((Long) evidence.get("patientId"));
    eventPublisher.publishAudit("evidence_query",
        (Long) evidence.get("patientId"),
        (String) evidence.get("decisionType"),
        decisionId);
    return Result.success(evidence);
  }

  /** GET /api/evidence/patient/{patientId} — 患者全部决策证据。 */
  @GetMapping("/patient/{patientId}")
  public Result<?> byPatient(@PathVariable Long patientId) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(evidenceChainService.findByPatient(patientId));
  }

  /**
   * GET /api/evidence/{decisionId}/fhir — 导出 HL7 FHIR R4 Provenance 兼容 JSON。
   *
   * AI 决策审计记录可被医院信息系统按国际标准消费
   * （agent=AI参与者, entity=决策依据, extension=哈希链证明）。
   */
  @GetMapping("/{decisionId}/fhir")
  public Result<?> asFhirProvenance(@PathVariable String decisionId) {
    var evidence = evidenceChainService.findByDecisionId(decisionId);
    ownershipGuard.checkResource(evidence == null ? null : (Long) evidence.get("patientId"));
    return Result.success(evidenceChainService.exportFhirProvenance(decisionId));
  }

  /** GET /api/evidence/verify — 哈希链完整性校验（审计：篡改即刻暴露）。 */
  @GetMapping("/verify")
  public Result<?> verify() {
    return Result.success(evidenceChainService.verify());
  }

  /**
   * GET /api/evidence/ledger — 守护价值账本（计数型：拦截/触达/回执聚合叙事）。
   * 医生不带参=全量账本；患者访问时强制限定本人 scope。
   */
  @GetMapping("/ledger")
  public Result<?> ledger(@org.springframework.web.bind.annotation.RequestParam(required = false) Long patientId) {
    Long scoped = ownershipGuard.scopeOrForbidden(patientId);
    return Result.success(guardianLedgerService.ledger(scoped));
  }
}
