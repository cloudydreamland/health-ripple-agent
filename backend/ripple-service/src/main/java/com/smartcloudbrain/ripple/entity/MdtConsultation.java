package com.smartcloudbrain.ripple.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 多智能体 MDT 会诊记录（五 Agent 多视角聚合纪要）。 */
@Entity
@Table(name = "mdt_consultation")
public class MdtConsultation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "mdt_id", length = 50)
  private String mdtId;

  @Column(name = "patient_id")
  private Long patientId;

  @Column(name = "chief_complaint", length = 500)
  private String chiefComplaint;

  @Column(name = "past_history_json", columnDefinition = "TEXT")
  private String pastHistoryJson;

  private String diagnosis;

  @Column(name = "drugs_json", columnDefinition = "TEXT")
  private String drugsJson;

  @Column(name = "consultation_json", columnDefinition = "TEXT")
  private String consultationJson;

  @Column(name = "consensus_json", columnDefinition = "TEXT")
  private String consensusJson;

  @Column(name = "agent_count")
  private Integer agentCount;

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
  public String getMdtId() { return mdtId; }
  public void setMdtId(String mdtId) { this.mdtId = mdtId; }
  public Long getPatientId() { return patientId; }
  public void setPatientId(Long patientId) { this.patientId = patientId; }
  public String getChiefComplaint() { return chiefComplaint; }
  public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
  public String getPastHistoryJson() { return pastHistoryJson; }
  public void setPastHistoryJson(String pastHistoryJson) { this.pastHistoryJson = pastHistoryJson; }
  public String getDiagnosis() { return diagnosis; }
  public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
  public String getDrugsJson() { return drugsJson; }
  public void setDrugsJson(String drugsJson) { this.drugsJson = drugsJson; }
  public String getConsultationJson() { return consultationJson; }
  public void setConsultationJson(String consultationJson) { this.consultationJson = consultationJson; }
  public String getConsensusJson() { return consensusJson; }
  public void setConsensusJson(String consensusJson) { this.consensusJson = consensusJson; }
  public Integer getAgentCount() { return agentCount; }
  public void setAgentCount(Integer agentCount) { this.agentCount = agentCount; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
