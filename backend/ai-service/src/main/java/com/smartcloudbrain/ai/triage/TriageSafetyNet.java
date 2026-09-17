package com.smartcloudbrain.ai.triage;

import com.smartcloudbrain.aiapi.dto.TriageRequest;
import com.smartcloudbrain.aiapi.dto.TriageResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 分诊安全网（Triage Safety Net）——对所有 Provider（LLM/规则/Dify）的输出统一执行的
 * 红色指征强制覆写层。这是分诊的医疗安全红线：无论上游 AI 怎么判，
 * 出现急症红色指征（.negation-aware 识别）时紧急度强制 EMERGENCY，
 * 非法紧急度值归一化为 ROUTINE，LLM 输出再离谱也不会把卒中判成普通门诊。
 *
 * 设计原则：红线与业务路由分离——Provider 负责"推荐哪个科"，
 * 安全网只负责"急症必须走急诊"。覆写行为写入 reason（[安全网覆写]前缀），
 * 可被测试与评测验证（RippleBench T 子集红线用例）。
 */
@Component
public class TriageSafetyNet {

  /** 神经急症红色指征（卒中/昏迷/抽搐）——出现即急诊，禁止降级兜底。 */
  private static final List<String> NEURO_RED_FLAGS = List.of(
      "意识不清", "意识障碍", "昏迷", "抽搐", "偏瘫", "言语不清", "面瘫", "肢体麻木");

  /** 单独出现即急症的红色指征（胸痛/晕厥/大汗/呼吸困难）。 */
  private static final List<String> SOLO_RED_FLAGS = List.of(
      "胸痛", "晕厥", "大汗", "呼吸困难", "chest pain", "shortness of breath");

  /** 胸闷/心悸需与危重伴随症组合才升级（稳定型心绞痛样表现属心内科常规评估，非急诊）。 */
  private static final List<String> SOFT_CARDIO = List.of("胸闷", "心悸");
  private static final List<String> SOFT_CARDIO_ACCOMPANIED = List.of("大汗", "晕厥", "呼吸困难", "意识不清");

  /** 消化道急症红色指征（呕血/黑便提示上消化道大出血）。 */
  private static final List<String> GI_RED_FLAGS = List.of("呕血", "便血", "black stool");

  private static final List<String> ASTHMA_MARKERS = List.of("哮喘", "喘息", "wheez");
  private static final List<String> BREATHLESS = List.of("呼吸困难", "讲话困难", "气促", "dyspnea");

  /** 对 Provider 的分诊结果执行安全覆写（急症强制 EMERGENCY + 紧急度合法化）。 */
  public TriageResponse apply(TriageRequest request, TriageResponse response) {
    String text = safe(request.chiefComplaint()) + " " + safe(request.symptoms());
    boolean redFlag =
        SymptomMatcher.mentions(text, NEURO_RED_FLAGS)
            || SymptomMatcher.mentions(text, SOLO_RED_FLAGS)
            || SymptomMatcher.mentions(text, GI_RED_FLAGS)
            || (SymptomMatcher.mentions(text, SOFT_CARDIO)
                && SymptomMatcher.mentions(text, SOFT_CARDIO_ACCOMPANIED))
            || (SymptomMatcher.mentions(text, ASTHMA_MARKERS)
                && SymptomMatcher.mentions(text, BREATHLESS));

    String urgency = normalizeUrgency(response.urgencyLevel());
    if (!redFlag) {
      // 无红色指征：只做紧急度合法化（LLM 可能输出枚举外的值）
      if (urgency.equals(response.urgencyLevel())) {
        return response;
      }
      return new TriageResponse(
          response.recommendedDepartment(), response.departmentCode(),
          response.recommendedDoctorDirection(), urgency, response.confidence(),
          response.recommendedDoctorIds(), response.reason(), response.degraded());
    }

    String reason = "[安全网覆写] 识别急症红色指征，紧急度强制EMERGENCY；" + safe(response.reason());
    String direction = response.recommendedDoctorDirection() == null
        || response.recommendedDoctorDirection().isBlank()
        ? "急诊方向" : response.recommendedDoctorDirection();
    return new TriageResponse(
        response.recommendedDepartment(), response.departmentCode(),
        direction, "EMERGENCY", response.confidence(),
        response.recommendedDoctorIds(), reason, response.degraded());
  }

  /** 紧急度合法化：仅接受 EMERGENCY/ROUTINE，其余归 ROUTINE。 */
  private String normalizeUrgency(String urgencyLevel) {
    return "EMERGENCY".equalsIgnoreCase(String.valueOf(urgencyLevel)) ? "EMERGENCY" : "ROUTINE";
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
