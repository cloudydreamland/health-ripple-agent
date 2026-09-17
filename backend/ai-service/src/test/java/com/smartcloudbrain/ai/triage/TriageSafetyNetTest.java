package com.smartcloudbrain.ai.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcloudbrain.aiapi.dto.TriageRequest;
import com.smartcloudbrain.aiapi.dto.TriageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 分诊安全网 + 否定感知匹配测试：
 * 1. 否定语境不作为阳性证据（"无胸痛"不得触发急症红线）——医疗分诊最经典的误判；
 * 2. 红色指征强制 EMERGENCY（即使 LLM 输出了 ROUTINE 也被覆写）；
 * 3. 胸闷需危重伴随症才升级（稳定型表现属心内科常规评估）；
 * 4. 非法紧急度值归一化。
 */
class TriageSafetyNetTest {

  private final TriageSafetyNet safetyNet = new TriageSafetyNet();

  private TriageResponse passthrough(String urgency) {
    return new TriageResponse("Cardiology", "CARDIOLOGY", "心血管方向", urgency, 0.9,
        List.of(1L), "provider 原始判断", false);
  }

  @Test
  void negatedChestPainMustNotTriggerEmergencyOverride() {
    TriageRequest request = new TriageRequest(1L, "否认胸痛，无大汗，的生命体征平稳", "无胸痛,无呼吸困难");
    TriageResponse result = safetyNet.apply(request, passthrough("ROUTINE"));
    assertEquals("ROUTINE", result.urgencyLevel(), "否定语境的胸痛不得触发急症覆写");
  }

  @Test
  void redFlagOverridesEvenWhenProviderSaysRoutine() {
    // 模拟 LLM 误判：卒中红色指征（意识不清）但 Provider 返回 ROUTINE → 安全网强制 EMERGENCY
    TriageRequest request = new TriageRequest(1L, "突发意识不清伴言语不清1小时", "意识不清,言语不清");
    TriageResponse result = safetyNet.apply(request, passthrough("ROUTINE"));
    assertEquals("EMERGENCY", result.urgencyLevel(), "红线覆写必须独立于 Provider 判断");
    assertTrue(result.reason().startsWith("[安全网覆写]"), "覆写行为必须显式留痕");
  }

  @Test
  void softChestTightnessAloneStaysRoutineButEscalatesWithColdSweat() {
    TriageRequest stable = new TriageRequest(1L, "活动后胸闷心悸半个月", "胸闷,心悸");
    assertEquals("ROUTINE", safetyNet.apply(stable, passthrough("ROUTINE")).urgencyLevel(),
        "稳定型胸闷属心内科常规评估");

    TriageRequest warning = new TriageRequest(1L, "胸闷气短伴大汗1小时", "胸闷,气短,大汗");
    assertEquals("EMERGENCY", safetyNet.apply(warning, passthrough("ROUTINE")).urgencyLevel(),
        "胸闷+大汗=ACS警示组合，必须升级");
  }

  @Test
  void asthmaWithBreathlessnessEscalates() {
    TriageRequest request = new TriageRequest(1L, "哮喘病史，喘息伴呼吸困难加重2小时", "喘息,呼吸困难");
    assertEquals("EMERGENCY", safetyNet.apply(request, passthrough("ROUTINE")).urgencyLevel());
  }

  @Test
  void illegalUrgencyValueNormalizedToRoutine() {
    TriageRequest request = new TriageRequest(1L, "多饮多尿3个月", "口渴,多尿");
    TriageResponse garbage = new TriageResponse("General Practice", "GENERAL", "", "HIGH_URGENT", 0.7,
        List.of(2L), "llm 自由文本", false);
    assertEquals("ROUTINE", safetyNet.apply(request, garbage).urgencyLevel(), "非法紧急度必须归一化");
  }

  @Test
  void symptomMatcherUnderstandsNegationScope() {
    assertTrue(SymptomMatcher.mentions("胸痛伴大汗", List.of("胸痛")));
    assertFalse(SymptomMatcher.mentions("无胸痛，否认大汗", List.of("胸痛", "大汗")),
        "全部出现均被否定时不得命中");
    assertTrue(SymptomMatcher.mentions("无胸痛，但有呼吸困难", List.of("胸痛", "呼吸困难")),
        "部分否定不影响其余症状的阳性判定");
    assertFalse(SymptomMatcher.mentions("denies chest pain", List.of("chest pain")));
  }
}
