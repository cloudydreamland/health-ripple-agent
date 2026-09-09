package com.smartcloudbrain.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FollowupRecordRequest(
    @NotNull Long planId,
    @NotBlank String status,
    String notes,
    String submittedAt
) {
}
