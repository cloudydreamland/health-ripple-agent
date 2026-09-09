package com.smartcloudbrain.ripple.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 并发症早期信号知识（出现什么症状 → 疑似什么并发症 → 立即做什么）。 */
@Entity
@Table(name = "complication_signal", indexes = @Index(name = "idx_cs_diagnosis", columnList = "diagnosis_keyword"))
public class ComplicationSignalRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "diagnosis_keyword", length = 100)
  private String diagnosisKeyword;

  @Column(name = "signal_symptom", length = 255)
  private String signalSymptom;

  @Column(length = 255)
  private String complication;

  @Column(name = "action_advice", length = 255)
  private String actionAdvice;

  @Column(length = 20)
  private String urgency;

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
  public String getDiagnosisKeyword() { return diagnosisKeyword; }
  public void setDiagnosisKeyword(String diagnosisKeyword) { this.diagnosisKeyword = diagnosisKeyword; }
  public String getSignalSymptom() { return signalSymptom; }
  public void setSignalSymptom(String signalSymptom) { this.signalSymptom = signalSymptom; }
  public String getComplication() { return complication; }
  public void setComplication(String complication) { this.complication = complication; }
  public String getActionAdvice() { return actionAdvice; }
  public void setActionAdvice(String actionAdvice) { this.actionAdvice = actionAdvice; }
  public String getUrgency() { return urgency; }
  public void setUrgency(String urgency) { this.urgency = urgency; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
