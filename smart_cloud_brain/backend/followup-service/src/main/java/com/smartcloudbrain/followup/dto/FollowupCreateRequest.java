package com.smartcloudbrain.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FollowupCreateRequest(
    @NotNull Long patientId,
    @NotBlank String diagnosis,
    String medications,
    Integer followupDays,
    String followupDate,
    String reminderSchedule
) {
}
