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

/** 药物-生活冲突知识（服华法林不能吃柚子/服二甲双胍禁酒/四环素避晒）。 */
@Entity
@Table(name = "drug_lifestyle_conflict", indexes = @Index(name = "idx_dlc_drug", columnList = "drug_keyword"))
public class DrugLifestyleConflictRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "drug_keyword", length = 100)
  private String drugKeyword;

  @Column(name = "conflict_item", length = 100)
  private String conflictItem;

  @Column(name = "risk_description", columnDefinition = "TEXT")
  private String riskDescription;

  @Column(length = 20)
  private String severity;

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
  public String getDrugKeyword() { return drugKeyword; }
  public void setDrugKeyword(String drugKeyword) { this.drugKeyword = drugKeyword; }
  public String getConflictItem() { return conflictItem; }
  public void setConflictItem(String conflictItem) { this.conflictItem = conflictItem; }
  public String getRiskDescription() { return riskDescription; }
  public void setRiskDescription(String riskDescription) { this.riskDescription = riskDescription; }
  public String getSeverity() { return severity; }
  public void setSeverity(String severity) { this.severity = severity; }
  public String getAdvice() { return advice; }
  public void setAdvice(String advice) { this.advice = advice; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
