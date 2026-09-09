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

/**
 * 医疗时间学规则知识（chrono-medical rule）。
 *
 * 结构化字段供 ChronoEngine 计算 nextTriggerAt：
 * - startHour   RHYTHM 节律触达的起始小时（如夜间低血糖=0，凌晨血压晨峰=4）
 * - offsetDays  PERIODIC 周期触达的偏移天数（如服药2周复查=14）
 * - periodDays  PERIODIC 滚动周期（如稳定期每月查INR=30；空=一次性）
 * - targetMonth SEASONAL 季节触达的目标月份（如入秋=9，春季花粉=3）
 */
@Entity
@Table(name = "chrono_rule", indexes = @Index(name = "idx_cr_diagnosis", columnList = "diagnosis_keyword"))
public class ChronoRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "diagnosis_keyword", length = 100)
  private String diagnosisKeyword;

  @Column(name = "chrono_type", length = 20)
  private String chronoType;

  @Column(length = 255)
  private String event;

  @Column(name = "trigger_time", length = 100)
  private String triggerTime;

  @Column(length = 255)
  private String action;

  @Column(name = "start_hour")
  private Integer startHour;

  @Column(name = "offset_days")
  private Integer offsetDays;

  @Column(name = "period_days")
  private Integer periodDays;

  @Column(name = "target_month")
  private Integer targetMonth;

  /** Timing Card 循证卡片：触发依据（指南/研究引用）。 */
  @Column(name = "evidence_basis", length = 500)
  private String evidenceBasis;

  /** Timing Card 循证卡片：错过代价（错过窗口的量化健康损失）。 */
  @Column(name = "miss_cost", length = 500)
  private String missCost;

  /** Timing Card 循证卡片：证据等级（GUIDELINE/RCT/OBSERVATIONAL）。 */
  @Column(name = "evidence_level", length = 30)
  private String evidenceLevel;

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
  public String getChronoType() { return chronoType; }
  public void setChronoType(String chronoType) { this.chronoType = chronoType; }
  public String getEvent() { return event; }
  public void setEvent(String event) { this.event = event; }
  public String getTriggerTime() { return triggerTime; }
  public void setTriggerTime(String triggerTime) { this.triggerTime = triggerTime; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public Integer getStartHour() { return startHour; }
  public void setStartHour(Integer startHour) { this.startHour = startHour; }
  public Integer getOffsetDays() { return offsetDays; }
  public void setOffsetDays(Integer offsetDays) { this.offsetDays = offsetDays; }
  public Integer getPeriodDays() { return periodDays; }
  public void setPeriodDays(Integer periodDays) { this.periodDays = periodDays; }
  public Integer getTargetMonth() { return targetMonth; }
  public void setTargetMonth(Integer targetMonth) { this.targetMonth = targetMonth; }
  public String getEvidenceBasis() { return evidenceBasis; }
  public void setEvidenceBasis(String evidenceBasis) { this.evidenceBasis = evidenceBasis; }
  public String getMissCost() { return missCost; }
  public void setMissCost(String missCost) { this.missCost = missCost; }
  public String getEvidenceLevel() { return evidenceLevel; }
  public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
