package com.smartcloudbrain.ai.provider.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcloudbrain.aiapi.dto.DrugItem;
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
}
