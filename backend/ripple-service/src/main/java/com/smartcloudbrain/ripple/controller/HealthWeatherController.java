package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.security.PatientOwnershipGuard;
import com.smartcloudbrain.ripple.service.HealthWeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康气象日报 API（Health Weather Daily）。
 *
 * GET /api/health-weather/daily?patientId=1 — 患者今日健康气象
 * GET /api/health-weather/daily/{patientId} — 同上（路径参数版，供患者端页面直连）
 *
 * 患者维度端点统一经 {@link PatientOwnershipGuard} 归属校验。
 */
@RestController
@RequestMapping("/api/health-weather")
public class HealthWeatherController {

  private final HealthWeatherService healthWeatherService;
  private final PatientOwnershipGuard ownershipGuard;

  public HealthWeatherController(HealthWeatherService healthWeatherService,
      PatientOwnershipGuard ownershipGuard) {
    this.healthWeatherService = healthWeatherService;
    this.ownershipGuard = ownershipGuard;
  }

  @GetMapping("/daily")
  public Result<?> daily(@RequestParam Long patientId) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(healthWeatherService.daily(patientId));
  }

  @GetMapping("/daily/{patientId}")
  public Result<?> dailyByPath(@PathVariable Long patientId) {
    ownershipGuard.checkAccess(patientId);
    return Result.success(healthWeatherService.daily(patientId));
  }
}
