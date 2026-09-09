package com.smartcloudbrain.followup.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.followup.dto.FollowupCreateRequest;
import com.smartcloudbrain.followup.dto.FollowupRecordRequest;
import com.smartcloudbrain.followup.service.FollowupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/followup")
public class FollowupController {

  private final FollowupService followupService;

  public FollowupController(FollowupService followupService) {
    this.followupService = followupService;
  }

  @PostMapping("/create")
  public Result<?> create(@Valid @RequestBody FollowupCreateRequest request) {
    return Result.success(followupService.create(request));
  }

  @GetMapping("/patient/{patientId}")
  public Result<?> findByPatient(@PathVariable Long patientId) {
    return Result.success(followupService.findByPatient(patientId));
  }

  @PostMapping("/record")
  public Result<?> submitRecord(@Valid @RequestBody FollowupRecordRequest request) {
    return Result.success(followupService.submitRecord(request));
  }
}
