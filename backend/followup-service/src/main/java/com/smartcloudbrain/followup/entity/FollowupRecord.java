package com.smartcloudbrain.followup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "followup_record")
public class FollowupRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plan_id")
  private Long planId;

  private String status;

  @Column(columnDefinition = "TEXT")
  private String notes;

  private Boolean abnormal;

  @Column(name = "doctor_notified")
  private Boolean doctorNotified;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getPlanId() {
    return planId;
  }

  public void setPlanId(Long planId) {
    this.planId = planId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Boolean getAbnormal() {
    return abnormal;
  }

  public void setAbnormal(Boolean abnormal) {
    this.abnormal = abnormal;
  }

  public Boolean getDoctorNotified() {
    return doctorNotified;
  }

  public void setDoctorNotified(Boolean doctorNotified) {
    this.doctorNotified = doctorNotified;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
