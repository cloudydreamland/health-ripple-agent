package com.smartcloudbrain.ripple.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 涟漪推演请求：一个健康事件（新诊断/新处方）。
 * diagnosis 与 drugs 至少其一非空（由服务层校验）。
 */
public record RippleDeriveRequest(
    Long patientId,
    @Size(max = 200, message = "诊断过长") String diagnosis,
    List<DrugItem> drugs,
    @Size(max = 500, message = "既往史过长") String pastHistory
) {
}
