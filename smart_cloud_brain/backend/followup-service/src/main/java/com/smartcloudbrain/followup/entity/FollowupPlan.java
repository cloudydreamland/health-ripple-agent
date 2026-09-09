package com.smartcloudbrain.followup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "followup_plan")
public class FollowupPlan {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "patient_id")
  private Long patientId;

  private String diagnosis;

  @Column(name = "medications", columnDefinition = "TEXT")
  private String medications;

  @Column(name = "followup_days")
  private Integer followupDays;

  @Column(name = "followup_date")
  private String followupDate;

  @Column(name = "reminder_schedule", columnDefinition = "TEXT")
  private String reminderSchedule;

  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getPatientId() {
    return patientId;
  }

  public void setPatientId(Long patientId) {
    this.patientId = patientId;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public void setDiagnosis(String diagnosis) {
    this.diagnosis = diagnosis;
  }

  public String getMedications() {
    return medications;
  }

  public void setMedications(String medications) {
    this.medications = medications;
  }

  public Integer getFollowupDays() {
    return followupDays;
  }

  public void setFollowupDays(Integer followupDays) {
    this.followupDays = followupDays;
  }

  public String getFollowupDate() {
    return followupDate;
  }

  public void setFollowupDate(String followupDate) {
    this.followupDate = followupDate;
  }

  public String getReminderSchedule() {
    return reminderSchedule;
  }

  public void setReminderSchedule(String reminderSchedule) {
    this.reminderSchedule = reminderSchedule;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (status == null) {
      status = "ACTIVE";
    }
  }
}
