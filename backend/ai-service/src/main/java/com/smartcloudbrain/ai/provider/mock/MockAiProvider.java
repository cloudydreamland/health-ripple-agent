package com.smartcloudbrain.ai.provider.mock;

import com.smartcloudbrain.ai.provider.AiProvider;
import com.smartcloudbrain.aiapi.dto.DrugItem;
import com.smartcloudbrain.aiapi.dto.MedicalRecordGenerateRequest;
import com.smartcloudbrain.aiapi.dto.MedicalRecordGenerateResponse;
import com.smartcloudbrain.aiapi.dto.PrescriptionCheckRequest;
import com.smartcloudbrain.aiapi.dto.PrescriptionCheckResponse;
import com.smartcloudbrain.aiapi.dto.PromptResolveResponse;
import com.smartcloudbrain.aiapi.dto.TriageRequest;
import com.smartcloudbrain.aiapi.dto.TriageResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 规则引擎降级 Provider（无 LLM/Dify key 时的结构化降级，非"哑降级"）。
 *
 * 设计原则（医疗安全优先级）：
 * 1. 危险症状（胸痛/呼吸困难/晕厥等）优先判心内科/急诊，紧急度 EMERGENCY
 * 2. 代谢症状（多饮/多尿/血糖）判全科初诊，紧急度 ROUTINE
 * 3. 呼吸症状（咳嗽/发热/咽痛）判呼吸内科
 * 4. 无法识别 → 全科 + degraded=true（保留真实降级语义，诚实标注需要人工分诊）
 *
 * 该规则引擎使系统在 LLM 不可用时仍具备可用的一线分诊能力（结构化降级），
 * 同时所有命中均 degraded=false + 规则置信度（0.80），与 LLM 置信度（0.9+）可区分。
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "mock")
public class MockAiProvider implements AiProvider {

  private static final List<String> CARDIO_DANGER = List.of(
      "胸痛", "胸闷", "心悸", "气短", "呼吸困难", "晕厥", "大汗", "chest", "dyspnea", "palpitation");
  private static final List<String> METABOLIC = List.of(
      "多饮", "多尿", "口渴", "血糖", "体重下降", "polyuria", "thirst", "glucose");
  private static final List<String> RESPIRATORY = List.of(
      "咳嗽", "咳痰", "发热", "咽痛", "哮喘", "cough", "fever", "sore throat");
  private static final List<String> EMERGENCY_SIGNS = List.of(
      "胸痛", "呼吸困难", "晕厥", "意识不清", "大汗", "chest pain", "shortness of breath");
  /** 卒中等神经急症红色指征：出现即急诊分流，不允许降级兜底（RippleBench v1评测发现缺口，v2修复）。 */
  private static final List<String> NEURO_RED_FLAGS = List.of(
      "意识不清", "言语不清", "偏瘫", "面瘫", "肢体麻木", "抽搐", "昏迷");
  /** 哮喘持续状态：哮喘/喘息 + 呼吸困难 → 呼吸内科急诊（而非心内），RippleBench v1评测发现误路由，v2修复。 */
  private static final List<String> ASTHMA_MARKERS = List.of("哮喘", "喘息", "wheez");
  private static final List<String> BREATHLESS = List.of("呼吸困难", "讲话困难", "气促", "dyspnea");

  @Override
  public String providerName() {
    return "mock";
  }

  @Override
  public TriageResponse triage(TriageRequest request, PromptResolveResponse prompt) {
    String complaint = request.chiefComplaint() == null ? "" : request.chiefComplaint();
    String symptoms = request.symptoms() == null ? "" : request.symptoms();
    String text = (complaint + " " + symptoms).toLowerCase();

    // 优先级0a：神经急症红色指征（卒中/昏迷）→ 立即急诊分流，紧急度 EMERGENCY，禁止降级
    if (containsAny(text, NEURO_RED_FLAGS)) {
      return new TriageResponse(
          "General Practice",
          "GENERAL",
          "急诊方向",
          "EMERGENCY",
          0.85,
          List.of(2L),
          "规则引擎：识别卒中/意识急症红色指征，紧急度EMERGENCY，建议立即急诊医学科就诊（卒中黄金3小时），勿等待普通门诊",
          false);
    }
    // 优先级0b：哮喘持续状态（哮喘/喘息 + 呼吸困难）→ 呼吸内科急诊，避免被心血管规则误路由
    if (containsAny(text, ASTHMA_MARKERS) && containsAny(text, BREATHLESS)) {
      return new TriageResponse(
          "Respiratory Medicine",
          "RESPIRATORY",
          "呼吸急症方向",
          "EMERGENCY",
          0.85,
          List.of(3L),
          "规则引擎：哮喘伴呼吸困难提示哮喘持续状态风险，紧急度EMERGENCY，立即呼吸内科/急诊处置",
          false);
    }
    // 优先级1：心血管危险症状 → 心内科（危险症状合并存在时紧急度 EMERGENCY）
    if (containsAny(text, CARDIO_DANGER)) {
      boolean emergency = containsAny(text, EMERGENCY_SIGNS);
      return new TriageResponse(
          "Cardiology",
          "CARDIOLOGY",
          "心血管方向",
          emergency ? "EMERGENCY" : "ROUTINE",
          0.80,
          List.of(1L),
          emergency
              ? "规则引擎：主诉含危险心血管症状，紧急度EMERGENCY，需立即排除急性冠脉综合征"
              : "规则引擎：主诉含胸闷/心悸等心血管症状，推荐心内科评估",
          false);
    }
    // 优先级2：代谢症状 → 全科初诊（糖尿病方向）
    if (containsAny(text, METABOLIC)) {
      return new TriageResponse(
          "General Practice",
          "GENERAL",
          "全科/内分泌方向",
          "ROUTINE",
          0.80,
          List.of(2L),
          "规则引擎：主诉含多饮/多尿/血糖升高等代谢症状，推荐全科门诊完善血糖评估",
          false);
    }
    // 优先级3：呼吸症状 → 呼吸内科
    if (containsAny(text, RESPIRATORY)) {
      return new TriageResponse(
          "Respiratory Medicine",
          "RESPIRATORY",
          "呼吸方向",
          "ROUTINE",
          0.80,
          List.of(3L),
          "规则引擎：主诉含咳嗽/发热等呼吸道症状，推荐呼吸内科",
          false);
    }
    // 兜底：无法识别 → 真实降级语义（需人工分诊）
    return new TriageResponse(
        "General Practice",
        "GENERAL",
        "",
        "ROUTINE",
        0.50,
        List.of(2L),
        "规则引擎无法识别症状特征，降级为全科人工分诊",
        true);
  }

  private boolean containsAny(String text, List<String> keywords) {
    return keywords.stream().anyMatch(text::contains);
  }

  @Override
  public MedicalRecordGenerateResponse generateMedicalRecord(MedicalRecordGenerateRequest request, PromptResolveResponse prompt) {
    return new MedicalRecordGenerateResponse(
        "Chest pain with dyspnea for two days",
        "Symptoms worsen after activity and are relieved by rest.",
        "No clear past history was provided.",
        "Physical examination should be completed by the doctor.",
        "Chest pain under evaluation.",
        "Complete ECG and cardiac enzyme checks.",
        false
    );
  }

  @Override
  public PrescriptionCheckResponse checkPrescription(PrescriptionCheckRequest request, PromptResolveResponse prompt) {
    boolean aspirin = request.drugs().stream()
        .map(DrugItem::drugName)
        .anyMatch(name -> name.contains("阿司匹林") || name.toLowerCase().contains("aspirin"));
    if (aspirin) {
      return new PrescriptionCheckResponse("MEDIUM", "Review bleeding risk before saving.", List.of("Aspirin may increase bleeding risk."), false);
    }
    return new PrescriptionCheckResponse("LOW", "No obvious high-risk interaction was found.", List.of(), false);
  }
}
