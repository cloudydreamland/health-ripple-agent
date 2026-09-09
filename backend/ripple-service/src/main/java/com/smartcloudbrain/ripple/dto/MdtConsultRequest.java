package com.smartcloudbrain.ripple.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** MDT 多智能体会诊请求（疑难病例：多病共存/诊断不明/治疗矛盾）。 */
public record MdtConsultRequest(
    Long patientId,
    @NotBlank(message = "主诉不能为空") @Size(max = 300) String chiefComplaint,
    @Size(max = 500) String pastHistory,
    @Size(max = 200) String diagnosis,
    List<DrugItem> drugs
) {
}
