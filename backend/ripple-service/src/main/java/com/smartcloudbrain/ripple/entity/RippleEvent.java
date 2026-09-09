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

/** 健康事件涟漪推演事件（一次推演 = 一条记录，图谱 JSON 持久化）。 */
@Entity
@Table(name = "ripple_event")
public class RippleEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "patient_id")
  private Long patientId;

  private String diagnosis;

  @Column(name = "drugs_json", columnDefinition = "TEXT")
  private String drugsJson;

  @Column(name = "past_history", length = 1000)
  private String pastHistory;

  @Column(name = "ripple_graph_json", columnDefinition = "TEXT")
  private String rippleGraphJson;

  @Column(name = "total_nodes")
  private Integer totalNodes;

  @Column(name = "high_risk_count")
  private Integer highRiskCount;

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
  public Long getPatientId() { return patientId; }
  public void setPatientId(Long patientId) { this.patientId = patientId; }
  public String getDiagnosis() { return diagnosis; }
  public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
  public String getDrugsJson() { return drugsJson; }
  public void setDrugsJson(String drugsJson) { this.drugsJson = drugsJson; }
  public String getPastHistory() { return pastHistory; }
  public void setPastHistory(String pastHistory) { this.pastHistory = pastHistory; }
  public String getRippleGraphJson() { return rippleGraphJson; }
  public void setRippleGraphJson(String rippleGraphJson) { this.rippleGraphJson = rippleGraphJson; }
  public Integer getTotalNodes() { return totalNodes; }
  public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }
  public Integer getHighRiskCount() { return highRiskCount; }
  public void setHighRiskCount(Integer highRiskCount) { this.highRiskCount = highRiskCount; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
