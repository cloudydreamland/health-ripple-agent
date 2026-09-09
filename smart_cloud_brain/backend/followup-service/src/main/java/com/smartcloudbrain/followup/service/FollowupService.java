package com.smartcloudbrain.followup.service;

import com.smartcloudbrain.common.error.ErrorCode;
import com.smartcloudbrain.common.exception.BusinessException;
import com.smartcloudbrain.followup.dto.FollowupCreateRequest;
import com.smartcloudbrain.followup.dto.FollowupRecordRequest;
import com.smartcloudbrain.followup.entity.FollowupPlan;
import com.smartcloudbrain.followup.entity.FollowupRecord;
import com.smartcloudbrain.followup.repository.FollowupPlanRepository;
import com.smartcloudbrain.followup.repository.FollowupRecordRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowupService {

  private final FollowupPlanRepository planRepository;
  private final FollowupRecordRepository recordRepository;

  public FollowupService(FollowupPlanRepository planRepository, FollowupRecordRepository recordRepository) {
    this.planRepository = planRepository;
    this.recordRepository = recordRepository;
  }

  @Transactional
  public Map<String, Object> create(FollowupCreateRequest request) {
    FollowupPlan plan = new FollowupPlan();
    plan.setPatientId(request.patientId());
    plan.setDiagnosis(request.diagnosis());
    plan.setMedications(request.medications() == null ? "" : request.medications());
    plan.setFollowupDays(request.followupDays() == null ? 7 : request.followupDays());
    plan.setFollowupDate(request.followupDate() == null ? "" : request.followupDate());
    plan.setReminderSchedule(request.reminderSchedule() == null ? "" : request.reminderSchedule());
    plan.setStatus("ACTIVE");
    FollowupPlan saved = planRepository.save(plan);
    return planView(saved);
  }

  public Map<String, Object> findByPatient(Long patientId) {
    List<FollowupPlan> plans = planRepository.findByPatientId(patientId);
    List<Map<String, Object>> planViews = plans.stream().map(this::planView).toList();
    List<FollowupRecord> allRecords = plans.stream()
        .flatMap(p -> recordRepository.findByPlanId(p.getId()).stream())
        .toList();
    List<Map<String, Object>> recordViews = allRecords.stream().map(this::recordView).toList();
    return Map.of("plans", planViews, "records", recordViews);
  }

  @Transactional
  public Map<String, Object> submitRecord(FollowupRecordRequest request) {
    FollowupPlan plan = planRepository.findById(request.planId())
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    FollowupRecord record = new FollowupRecord();
    record.setPlanId(request.planId());
    record.setStatus(request.status());
    record.setNotes(request.notes() == null ? "" : request.notes());
    // 自主决策：异常标记
    boolean abnormal = isAbnormalStatus(request.status());
    record.setAbnormal(abnormal);
    record.setDoctorNotified(abnormal);
    if (request.submittedAt() != null && !request.submittedAt().isEmpty()) {
      try {
        record.setSubmittedAt(LocalDateTime.parse(request.submittedAt(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      } catch (Exception e) {
        record.setSubmittedAt(LocalDateTime.now());
      }
    } else {
      record.setSubmittedAt(LocalDateTime.now());
    }
    record.setCreatedAt(LocalDateTime.now());
    FollowupRecord saved = recordRepository.save(record);
    // 若痊愈，更新计划状态
    if ("痊愈".equals(request.status())) {
      plan.setStatus("COMPLETED");
      planRepository.save(plan);
    }
    return recordView(saved);
  }

  private boolean isAbnormalStatus(String status) {
    if (status == null) {
      return false;
    }
    return status.contains("加重") || status.contains("恶化") || status.contains("严重") || status.contains("无变化");
  }

  private Map<String, Object> planView(FollowupPlan plan) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("planId", plan.getId());
    view.put("patientId", plan.getPatientId());
    view.put("diagnosis", plan.getDiagnosis());
    view.put("medications", plan.getMedications());
    view.put("followupDays", plan.getFollowupDays());
    view.put("followupDate", plan.getFollowupDate());
    view.put("reminderSchedule", plan.getReminderSchedule());
    view.put("status", plan.getStatus());
    view.put("createdAt", plan.getCreatedAt() == null ? "" : plan.getCreatedAt().toString());
    return view;
  }

  private Map<String, Object> recordView(FollowupRecord record) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("recordId", record.getId());
    view.put("planId", record.getPlanId());
    view.put("status", record.getStatus());
    view.put("notes", record.getNotes());
    view.put("abnormal", record.getAbnormal());
    view.put("doctorNotified", record.getDoctorNotified());
    view.put("submittedAt", record.getSubmittedAt() == null ? "" : record.getSubmittedAt().toString());
    return view;
  }
}
