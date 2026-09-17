package com.smartcloudbrain.ripple.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcloudbrain.ripple.entity.EvidenceChain;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import com.smartcloudbrain.ripple.repository.EvidenceChainRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 守护价值账本（Guardian Value Ledger，第六轮创新：回答"这套守护到底值多少"）。
 *
 * 从印鉴链与时间学触达聚合系统已经产生的守护动作计数：
 * - 审计决策总数 / 涟漪推演数 / MDT会诊数 / 处方拦截数（按 decisionType 计数）
 * - 高危反事实路径锁定数（FLAGGED——每一条都是"被讲清楚为什么不能这么做"的潜在事故）
 * - 黄金窗口触达数（WINDOW 已触达/已缓解）、干预回执已缓解数、升级就医转诊数
 *
 * 诚实边界：本账本是<b>计数型</b>账本——所有数字都来自已落库的真实决策与触达，
 * 不虚构"避免了X次死亡"式的概率换算（那种换算需要真实世界对照数据，我们在
 * System Card 里把它列为路线图而不是现在吹的指标）。
 */
@Service
public class GuardianLedgerService {

  private final EvidenceChainRepository evidenceChainRepository;
  private final ChronoTriggerRepository triggerRepository;
  private final ObjectMapper objectMapper;

  public GuardianLedgerService(EvidenceChainRepository evidenceChainRepository,
      ChronoTriggerRepository triggerRepository, ObjectMapper objectMapper) {
    this.evidenceChainRepository = evidenceChainRepository;
    this.triggerRepository = triggerRepository;
    this.objectMapper = objectMapper;
  }

  /** 守护价值账本：patientId 为空时聚合全量（医生视角），否则聚合单患者（患者/家属视角）。 */
  public Map<String, Object> ledger(Long patientId) {
    List<EvidenceChain> records = patientId == null
        ? evidenceChainRepository.findAllByOrderByIdAsc()
        : evidenceChainRepository.findByPatientIdOrderByCreatedAtAsc(patientId);

    long rippleDerivations = 0;
    long mdtConsultations = 0;
    long prescriptionBlocks = 0;
    long otherDecisions = 0;
    long flaggedPathsLocked = 0;

    for (EvidenceChain record : records) {
      String type = String.valueOf(record.getDecisionType());
      switch (type) {
        case "RIPPLE_DERIVATION" -> rippleDerivations++;
        case "MDT_CONSULTATION" -> mdtConsultations++;
        case "PRESCRIPTION_BLOCK" -> prescriptionBlocks++;
        default -> otherDecisions++;
      }
      flaggedPathsLocked += countFlaggedPaths(record.getAlternativePathsJson());
    }

    long windowTouched = triggerRepository.countByChronoTypeAndStatusIn(
        "WINDOW", List.of("FIRED", "RESOLVED"));
    long resolvedFeedback = triggerRepository.countByFeedbackStatus("RESOLVED");
    long escalatedFeedback = triggerRepository.countByFeedbackStatus("ESCALATED");

    Map<String, Object> view = new LinkedHashMap<>();
    view.put("scope", patientId == null ? "GLOBAL" : "PATIENT");
    if (patientId != null) {
      view.put("patientId", patientId);
    }
    view.put("totalDecisions", records.size());
    view.put("rippleDerivations", rippleDerivations);
    view.put("mdtConsultations", mdtConsultations);
    view.put("prescriptionBlocks", prescriptionBlocks);
    view.put("otherDecisions", otherDecisions);
    view.put("flaggedPathsLocked", flaggedPathsLocked);
    view.put("windowTouches", windowTouched);
    view.put("resolvedFeedback", resolvedFeedback);
    view.put("escalatedTransfers", escalatedFeedback);
    view.put("narrative", String.format(
        "印鉴链在案：审计决策 %d 条（涟漪推演 %d / MDT会诊 %d / 处方拦截 %d），"
            + "锁定高危反事实路径 %d 条，黄金窗口触达 %d 次，"
            + "干预回执已缓解 %d 项、升级就医转诊 %d 次。",
        records.size(), rippleDerivations, mdtConsultations, prescriptionBlocks,
        flaggedPathsLocked, windowTouched, resolvedFeedback, escalatedFeedback));
    view.put("model", "计数型账本：全部数字来自已入印鉴链的真实决策与触达，不虚构概率换算"
        + "（概率级'避免事件数'需真实世界对照数据，列为路线图项）");
    return view;
  }

  /** 统计一条证据里被护栏锁定的反事实路径数（落库前已完成审计，verdict 随路径持久化）。 */
  private long countFlaggedPaths(String alternativePathsJson) {
    try {
      Object parsed = objectMapper.readValue(alternativePathsJson == null ? "[]" : alternativePathsJson, Object.class);
      if (!(parsed instanceof List<?> paths)) {
        return 0;
      }
      return paths.stream()
          .filter(p -> p instanceof Map<?, ?> m && "FLAGGED".equals(String.valueOf(m.get("guardrailVerdict"))))
          .count();
    } catch (Exception e) {
      return 0;
    }
  }
}
