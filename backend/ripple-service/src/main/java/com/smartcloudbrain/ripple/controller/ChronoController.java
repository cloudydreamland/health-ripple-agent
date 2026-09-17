package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.security.PatientOwnershipGuard;
import com.smartcloudbrain.ripple.service.ChronoEngine;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医疗时间学触达 API（核心创新4入口）。
 *
 * DuMate 定时任务轮询 GET /api/chrono/due 拉取到期触达项 → 主动触达患者 →
 * POST /api/chrono/trigger/{id}/ack 确认 → 引擎按时间学规则推进下一次触达。
 *
 * 患者维度端点统一经 {@link PatientOwnershipGuard} 归属校验；
 * due 轮询在带患者身份时只返回本人触达项。
 */
@RestController
@RequestMapping("/api/chrono")
public class ChronoController {

  private final ChronoEngine chronoEngine;
  private final PatientOwnershipGuard ownershipGuard;

  public ChronoController(ChronoEngine chronoEngine, PatientOwnershipGuard ownershipGuard) {
    this.chronoEngine = chronoEngine;
    this.ownershipGuard = ownershipGuard;
  }

  /** GET /api/chrono/due — 到期触达查询（DuMate 定时任务轮询入口）。 */
  @GetMapping("/due")
  public Result<?> due(@RequestParam(required = false) Long patientId) {
    if (patientId != null) {
      ownershipGuard.checkAccess(patientId);
    }
    List<Map<String, Object>> dueList = new ArrayList<>();
    for (var trigger : chronoEngine.due(LocalDateTime.now())) {
      if (patientId != null && !patientId.equals(trigger.getPatientId())) {
        continue;
      }
      dueList.add(view(trigger));
    }
    return Result.success(Map.of("dueCount", dueList.size(), "dueTriggers", dueList));
  }

  /** GET /api/chrono/triggers/patient/{patientId} — 患者全部时间学触达计划。 */
  @GetMapping("/triggers/patient/{patientId}")
  public Result<?> byPatient(@PathVariable Long patientId) {
    ownershipGuard.checkAccess(patientId);
    List<Map<String, Object>> result = new ArrayList<>();
    for (var trigger : chronoEngine.findByPatient(patientId)) {
      result.add(view(trigger));
    }
    return Result.success(result);
  }

  /** POST /api/chrono/trigger/{id}/ack — 确认已触达，引擎推进下一次。 */
  @PostMapping("/trigger/{id}/ack")
  public Result<?> ack(@PathVariable Long id) {
    return Result.success(view(chronoEngine.ack(id)));
  }

  /**
   * POST /api/chrono/trigger/{id}/feedback — 干预回执（涟漪消解闭环）。
   *
   * @param outcome RESOLVED已缓解 / UNRESOLVED未缓解（2小时后加强触达）/ ESCALATED已升级就医
   * @param note    患者/家属自述备注
   */
  @PostMapping("/trigger/{id}/feedback")
  public Result<?> feedback(@PathVariable Long id,
      @RequestParam String outcome,
      @RequestParam(required = false, defaultValue = "") String note) {
    ownershipGuard.checkResource(chronoEngine.get(id).getPatientId());
    return Result.success(view(chronoEngine.feedback(id, outcome, note)));
  }

  private Map<String, Object> view(com.smartcloudbrain.ripple.entity.ChronoTrigger trigger) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("triggerId", trigger.getId());
    view.put("patientId", trigger.getPatientId());
    view.put("rippleEventId", trigger.getRippleEventId());
    view.put("chronoType", trigger.getChronoType());
    view.put("event", trigger.getEvent());
    view.put("triggerTime", trigger.getTriggerTime());
    view.put("nextTriggerAt", trigger.getNextTriggerAt() == null ? "" : trigger.getNextTriggerAt().toString());
    view.put("status", trigger.getStatus());
    view.put("action", trigger.getAction());
    view.put("lastFiredAt", trigger.getLastFiredAt() == null ? "" : trigger.getLastFiredAt().toString());
    view.put("feedbackStatus", trigger.getFeedbackStatus() == null ? "" : trigger.getFeedbackStatus());
    view.put("feedbackNote", trigger.getFeedbackNote() == null ? "" : trigger.getFeedbackNote());
    view.put("feedbackAt", trigger.getFeedbackAt() == null ? "" : trigger.getFeedbackAt().toString());
    return view;
  }
}
