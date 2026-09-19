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

  /** 干预回执（涟漪消解闭环）：RESOLVED已缓解 / UNRESOLVED未缓解 / ESCALATED已升级就医，空=待回执。 */
  @Column(name = "feedback_status", length = 20)
  private String feedbackStatus;

  /** 干预回执备注（患者/家属自述，如"已补糖缓解"/"胸痛未缓解已拨打120"）。 */
  @Column(name = "feedback_note", length = 300)
  private String feedbackNote;

  /** 干预回执时间。 */
  @Column(name = "feedback_at")
  private LocalDateTime feedbackAt;

  /** 医生审定状态（人机共驾终审）：APPROVED通过 / ADJUSTED已调整 / VETOED已否决，空=待审定。 */
  @Column(name = "review_status", length = 20)
  private String reviewStatus;

  /** 医生审定备注（否决理由/调整说明）。 */
  @Column(name = "review_note", length = 300)
  private String reviewNote;

  /** 审定医生（姓名，随审定动作写入印鉴链）。 */
  @Column(name = "reviewer", length = 100)
  private String reviewer;

  /** 审定时间。 */
  @Column(name = "review_at")
  private LocalDateTime reviewAt;

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
  public String getFeedbackStatus() { return feedbackStatus; }
  public void setFeedbackStatus(String feedbackStatus) { this.feedbackStatus = feedbackStatus; }
  public String getFeedbackNote() { return feedbackNote; }
  public void setFeedbackNote(String feedbackNote) { this.feedbackNote = feedbackNote; }
  public LocalDateTime getFeedbackAt() { return feedbackAt; }
  public void setFeedbackAt(LocalDateTime feedbackAt) { this.feedbackAt = feedbackAt; }
  public String getReviewStatus() { return reviewStatus; }
  public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
  public String getReviewNote() { return reviewNote; }
  public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
  public String getReviewer() { return reviewer; }
  public void setReviewer(String reviewer) { this.reviewer = reviewer; }
  public LocalDateTime getReviewAt() { return reviewAt; }
  public void setReviewAt(LocalDateTime reviewAt) { this.reviewAt = reviewAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
