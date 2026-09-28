package com.smartcloudbrain.aiapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 处方药品项。
 *
 * 安全语义：处方安全审核（过敏/相互作用/禁忌）**只依赖 drugName** 即可成立——
 * 医生口述"开阿莫西林"或下游 AI 技能只传到药名时，审核必须照常执行并拦截；
 * 剂量/频次/用法缺失属于处方完整性问题，应由医生后续补全，不能阻断安全门。
 * 因此仅 drugName 为必填，其余字段可空（空值在 AI 提示词与审计记录中按"未注明"处理）。
 */
public record DrugItem(
    @NotBlank String drugName,
    String dosage,
    String frequency,
    String usageMethod,
    Integer days,
    String remark
) {
  /** 兼容既有三参调用（doc 03 示例、历史构造点）。 */
  public DrugItem(String drugName, String dosage, String frequency) {
    this(drugName, dosage, frequency, null, null, null);
  }
}
