package com.smartcloudbrain.ripple.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 反事实决策证据链（tamper-evident hash chain）。
 *
 * 每条证据的 hash = SHA-256(prevHash + "|" + decisionId + "|" + 规范化内容)，
 * 任何一条被篡改，其后所有证据的哈希校验都会失败 —— 与区块链防篡改原理一致，
 * 满足医疗合规"可审计、可申诉、可复盘"的可解释 AI 要求。
 */
@Entity
@Table(name = "counterfactual_evidence")
public class EvidenceChain {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "decision_id", unique = true, length = 100)
  private String decisionId;

  @Column(name = "decision_type", length = 50)
  private String decisionType;

  @Column(name = "patient_id")
  private Long patientId;

  @Column(name = "chosen_path", columnDefinition = "TEXT")
  private String chosenPath;

  @Column(name = "alternative_paths_json", columnDefinition = "TEXT")
  private String alternativePathsJson;

  @Column(name = "inputs_json", columnDefinition = "TEXT")
  private String inputsJson;

  @Column(name = "considered_factors_json", columnDefinition = "TEXT")
  private String consideredFactorsJson;

  @Column(name = "decision_json", columnDefinition = "TEXT")
  private String decisionJson;

  @Column(name = "confidence", precision = 5, scale = 4)
  private BigDecimal confidence;

  @Column(name = "action_taken", length = 100)
  private String actionTaken;

  @Column(name = "agent_id", length = 100)
  private String agentId;

  @Column(name = "hash", length = 64)
  private String hash;

  /** 前一条证据的哈希，构成链式防篡改结构。 */
  @Column(name = "prev_hash", length = 64)
  private String prevHash;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getDecisionId() { return decisionId; }
  public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
  public String getDecisionType() { return decisionType; }
  public void setDecisionType(String decisionType) { this.decisionType = decisionType; }
  public Long getPatientId() { return patientId; }
  public void setPatientId(Long patientId) { this.patientId = patientId; }
  public String getChosenPath() { return chosenPath; }
  public void setChosenPath(String chosenPath) { this.chosenPath = chosenPath; }
  public String getAlternativePathsJson() { return alternativePathsJson; }
  public void setAlternativePathsJson(String alternativePathsJson) { this.alternativePathsJson = alternativePathsJson; }
  public String getInputsJson() { return inputsJson; }
  public void setInputsJson(String inputsJson) { this.inputsJson = inputsJson; }
  public String getConsideredFactorsJson() { return consideredFactorsJson; }
  public void setConsideredFactorsJson(String consideredFactorsJson) { this.consideredFactorsJson = consideredFactorsJson; }
  public String getDecisionJson() { return decisionJson; }
  public void setDecisionJson(String decisionJson) { this.decisionJson = decisionJson; }
  public BigDecimal getConfidence() { return confidence; }
  public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
  public String getActionTaken() { return actionTaken; }
  public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
  public String getAgentId() { return agentId; }
  public void setAgentId(String agentId) { this.agentId = agentId; }
  public String getHash() { return hash; }
  public void setHash(String hash) { this.hash = hash; }
  public String getPrevHash() { return prevHash; }
  public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
