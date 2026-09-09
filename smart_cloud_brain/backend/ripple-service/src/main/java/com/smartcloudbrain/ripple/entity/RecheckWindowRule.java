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

/** 复查窗口知识（何时查什么：服药2周复查肝肾功/3个月查糖化/每年眼底筛查）。 */
@Entity
@Table(name = "recheck_window", indexes = @Index(name = "idx_rw_diagnosis", columnList = "diagnosis_keyword"))
public class RecheckWindowRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "diagnosis_keyword", length = 100)
  private String diagnosisKeyword;

  @Column(length = 255)
  private String item;

  @Column(length = 100)
  private String timing;

  @Column(name = "chrono_type", length = 20)
  private String chronoType;

  @Column(columnDefinition = "TEXT")
  private String advice;

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
  public String getItem() { return item; }
  public void setItem(String item) { this.item = item; }
  public String getTiming() { return timing; }
  public void setTiming(String timing) { this.timing = timing; }
  public String getChronoType() { return chronoType; }
  public void setChronoType(String chronoType) { this.chronoType = chronoType; }
  public String getAdvice() { return advice; }
  public void setAdvice(String advice) { this.advice = advice; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
