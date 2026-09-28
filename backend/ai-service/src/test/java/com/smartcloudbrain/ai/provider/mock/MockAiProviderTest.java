package com.smartcloudbrain.ai.provider.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcloudbrain.aiapi.dto.DrugItem;
import com.smartcloudbrain.aiapi.dto.MedicalRecordGenerateRequest;
import com.smartcloudbrain.aiapi.dto.PrescriptionCheckRequest;
import com.smartcloudbrain.aiapi.dto.TriageRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockAiProviderTest {

  private final MockAiProvider provider = new MockAiProvider();

  @Test
  void triageRecommendsCardiologyForChestPain() {
    var response = provider.triage(new TriageRequest(1L, "chest pain with shortness of breath"));

    assertEquals("CARDIOLOGY", response.departmentCode());
    assertEquals("EMERGENCY", response.urgencyLevel());
    assertFalse(response.degraded());
  }

  @Test
  void triageRoutesSyncopeToCardiologyEmergency() {
    // 回归锁：晕厥是心源性急症信号，必须路由心内科且EMERGENCY（盲测集T09泛化中发现的回归）
    var response = provider.triage(new TriageRequest(1L, "晕厥一次，持续约1分钟自行苏醒", "晕厥"));

    assertEquals("CARDIOLOGY", response.departmentCode());
    assertEquals("EMERGENCY", response.urgencyLevel());
    assertFalse(response.degraded());
  }

  @Test
  void triageRoutesChildFeverConvulsionToPediatricEmergency() {
    // 回归锁：患儿必须先按年龄层路由（成人神经急症规则不得抢跑）
    var response = provider.triage(new TriageRequest(1L, "患儿高热伴抽搐一次", "高热,抽搐"));

    assertEquals("PEDIATRICS", response.departmentCode());
    assertEquals("EMERGENCY", response.urgencyLevel());
  }

  @Test
  void triageRuleEngineRoutesMetabolicSymptomsToGeneral() {
    var response = provider.triage(
        new TriageRequest(1L, "多饮多尿伴血糖升高3个月", "口渴,多尿,乏力"));

    assertEquals("GENERAL", response.departmentCode());
    assertEquals("ROUTINE", response.urgencyLevel());
    assertFalse(response.degraded());
  }

  @Test
  void triageDangerSymptomsTakePriorityOverMetabolic() {
    var response = provider.triage(
        new TriageRequest(1L, "多饮多尿伴血糖升高3个月，近期胸闷气短", "口渴,多尿,活动后胸闷"));

    assertEquals("CARDIOLOGY", response.departmentCode());
    assertEquals(List.of(1L), response.recommendedDoctorIds());
    assertFalse(response.degraded());
  }

  @Test
  void triageUnrecognizedSymptomsDegradeHonestly() {
    var response = provider.triage(new TriageRequest(1L, "膝关节疼痛伴活动受限"));

    assertEquals("GENERAL", response.departmentCode());
    assertTrue(response.degraded());
  }

  @Test
  void prescriptionCheckFlagsAspirinRisk() {
    var response = provider.checkPrescription(new PrescriptionCheckRequest(
        1L,
        1L,
        List.of(new DrugItem("aspirin", "100mg", "once daily", "oral", 7, ""))
    ));

    assertEquals("MEDIUM", response.riskLevel());
    assertTrue(response.suggestions().contains("bleeding"));
  }

  @Test
  void prescriptionCheckInterceptsPenicillinAllergyWithAmoxicillin() {
    // 回归锁：过敏史与处方同类药冲突必须 HIGH 拦截（演示主链路：患者1青霉素过敏 + 阿莫西林）
    var response = provider.checkPrescription(new PrescriptionCheckRequest(
        1L, 1L, null, "感冒", 21, "FEMALE",
        "青霉素过敏（幼年用药后全身皮疹）", "",
        List.of(new DrugItem("阿莫西林", "0.5g", "每日三次", "口服", 7, ""))
    ));

    assertEquals("HIGH", response.riskLevel());
    assertFalse(response.degraded());
    assertTrue(response.contraindications().stream().anyMatch(s -> s.contains("青霉素")));
    assertTrue(response.adjustmentSuggestions().stream().anyMatch(s -> s.contains("阿奇霉素")));
  }

  @Test
  void prescriptionCheckInterceptsEnglishPenicillinAllergyCaseInsensitive() {
    var response = provider.checkPrescription(new PrescriptionCheckRequest(
        1L, 1L, null, "infection", 21, "FEMALE",
        "Penicillin allergy (severe rash in childhood)", "",
        List.of(new DrugItem("Amoxicillin", "0.5g", "tid", "oral", 7, ""))
    ));

    assertEquals("HIGH", response.riskLevel());
  }

  @Test
  void prescriptionCheckPenicillinAllergyWithoutPenicillinDrugStaysLow() {
    // 过敏史存在但处方不含青霉素类 → 不误拦（走阿司匹林外默认LOW基线）
    var response = provider.checkPrescription(new PrescriptionCheckRequest(
        1L, 1L, null, "感冒", 21, "FEMALE",
        "青霉素过敏（幼年用药后全身皮疹）", "",
        List.of(new DrugItem("布洛芬", "0.3g", "每日两次", "口服", 3, ""))
    ));

    assertEquals("LOW", response.riskLevel());
  }

  @Test
  void prescriptionCheckNoAllergyKeepsLowBaseline() {
    // 无过敏史 + 阿莫西林 → 不升级为HIGH（防止过敏规则误伤无过敏患者）
    var response = provider.checkPrescription(new PrescriptionCheckRequest(
        1L, 1L, null, "感冒", 21, "FEMALE",
        "No known drug allergy", "",
        List.of(new DrugItem("阿莫西林", "0.5g", "每日三次", "口服", 7, ""))
    ));

    assertEquals("LOW", response.riskLevel());
  }

  @Test
  void prescriptionCheckInterceptsPenicillinAllergyWithNameOnlyDrug() {
    // 回归锁：只给药名（剂量/频次/用法未提供）也必须能拦截——安全门不得因处方信息不全而失效
    // （2026-09-23 DuMate 实测暴露：下游只传药名时后端 400 → 降级放过）
    var response = provider.checkPrescription(new PrescriptionCheckRequest(
        1L, 1L, null, "", null, "",
        "青霉素过敏（幼年用药后全身皮疹）", "",
        List.of(new DrugItem("阿莫西林", null, null, null, null, null))
    ));

    assertEquals("HIGH", response.riskLevel());
    assertFalse(response.degraded());
  }

  @Test
  void medicalRecordExtractsDiagnosisAndMedicationFromDialogue() {
    // 规则引擎从对话确定性提取，不再返回与输入无关的固定模板
    var response = provider.generateMedicalRecord(new MedicalRecordGenerateRequest(
        6668L, "GEN", "患者诉多饮多尿1月伴胸闷，诊断2型糖尿病，开二甲双胍"));

    assertFalse(response.degraded());
    assertTrue(response.diagnosis().contains("2型糖尿病"), "诊断应提取出2型糖尿病");
    assertTrue(response.treatmentAdvice().contains("二甲双胍"), "处理建议应包含处方药");
    assertFalse(response.chiefComplaint().isBlank(), "主诉不应为空");
  }

  @Test
  void medicalRecordStaysHonestWhenNoDiagnosisKeyword() {
    // 未提及可识别诊断 → 明确标注待医生诊断，绝不臆造
    var response = provider.generateMedicalRecord(new MedicalRecordGenerateRequest(
        1L, "GEN", "患者诉头晕3天"));

    assertTrue(response.diagnosis().contains("待医生明确诊断"));
    assertFalse(response.degraded());
  }
}
