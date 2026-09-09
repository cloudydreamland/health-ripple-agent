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
 * 医疗时间学触达计划（chrono-medical trigger）。
 *
 * chronoType 四类时间学：
 * - WINDOW   窗口期（心梗黄金120分钟/卒中3小时）→ 即时一次性高优先级触达
 * - RHYTHM   节律性（夜间哮喘3-5点/凌晨血压晨峰）→ 每日固定时段触达
 * - PERIODIC 周期性（服药2周复查肝功/季度复查）→ 按天/周期滚动触达
 * - SEASONAL 季节性（换季慢病波动）→ 按月份季节触达
 */
@Entity
@Table(name = "chrono_trigger", indexes = {
    @Index(name = "idx_ct_patient", columnList = "patient_id"),
    @Index(name = "idx_ct_due", columnList = "status, next_trigger_at")
})
public class ChronoTrigger {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "patient_id")
  private Long patientId;

  @Column(name = "ripple_event_id")
  private Long rippleEventId;

  @Column(name = "chrono_type", length = 20)
  private String chronoType;

  @Column(length = 255)
  private String event;

  @Column(name = "trigger_time", length = 100)
  private String triggerTime;

  /** 下次应触达时间（引擎按时间学规则计算，DuMate 定时任务轮询 /api/chrono/due 拉取到期项）。 */
  @Column(name = "next_trigger_at")
  private LocalDateTime nextTriggerAt;

  @Column(length = 20)
  private String status;

  @Column(length = 255)
  private String action;

  /** Timing Card 循证卡片：触发依据（指南/研究引用）。 */
  @Column(name = "evidence_basis", length = 500)
  private String evidenceBasis;

  /** Timing Card 循证卡片：错过代价（错过窗口的量化健康损失）。 */
  @Column(name = "miss_cost", length = 500)
  private String missCost;

  /** Timing Card 循证卡片：证据等级（GUIDELINE/RCT/OBSERVATIONAL）。 */
  @Column(name = "evidence_level", length = 30)
  private String evidenceLevel;

  @Column(name = "last_fired_at")
  private LocalDateTime lastFiredAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (status == null) {
      status = "ACTIVE";
    }
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getPatientId() { return patientId; }
  public void setPatientId(Long patientId) { this.patientId = patientId; }
  public Long getRippleEventId() { return rippleEventId; }
  public void setRippleEventId(Long rippleEventId) { this.rippleEventId = rippleEventId; }
  public String getChronoType() { return chronoType; }
  public void setChronoType(String chronoType) { this.chronoType = chronoType; }
  public String getEvent() { return event; }
  public void setEvent(String event) { this.event = event; }
  public String getTriggerTime() { return triggerTime; }
  public void setTriggerTime(String triggerTime) { this.triggerTime = triggerTime; }
  public LocalDateTime getNextTriggerAt() { return nextTriggerAt; }
  public void setNextTriggerAt(LocalDateTime nextTriggerAt) { this.nextTriggerAt = nextTriggerAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getEvidenceBasis() { return evidenceBasis; }
  public void setEvidenceBasis(String evidenceBasis) { this.evidenceBasis = evidenceBasis; }
  public String getMissCost() { return missCost; }
  public void setMissCost(String missCost) { this.missCost = missCost; }
  public String getEvidenceLevel() { return evidenceLevel; }
  public void setEvidenceLevel(String evidenceLevel) { this.evidenceLevel = evidenceLevel; }
  public LocalDateTime getLastFiredAt() { return lastFiredAt; }
  public void setLastFiredAt(LocalDateTime lastFiredAt) { this.lastFiredAt = lastFiredAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
