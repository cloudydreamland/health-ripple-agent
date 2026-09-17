package com.smartcloudbrain.ai.provider.mock;

import com.smartcloudbrain.ai.provider.AiProvider;
import com.smartcloudbrain.ai.triage.SymptomMatcher;
import com.smartcloudbrain.aiapi.dto.DrugItem;
import com.smartcloudbrain.aiapi.dto.MedicalRecordGenerateRequest;
import com.smartcloudbrain.aiapi.dto.MedicalRecordGenerateResponse;
import com.smartcloudbrain.aiapi.dto.PrescriptionCheckRequest;
import com.smartcloudbrain.aiapi.dto.PrescriptionCheckResponse;
import com.smartcloudbrain.aiapi.dto.PromptResolveResponse;
import com.smartcloudbrain.aiapi.dto.TriageRequest;
import com.smartcloudbrain.aiapi.dto.TriageResponse;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 规则引擎 Provider（无 LLM/Dify key 时的结构化降级，非"哑降级"）。
 *
 * 路由规则按临床分诊常规设计（危险症状优先于器官系统、急症优先于慢症），
 * 全部症状匹配经 {@link SymptomMatcher} 否定感知（"无胸痛"不作为阳性证据）：
 *
 * 优先级0：儿科红色指征（患儿按年龄层最先路由，高热/抽搐等→儿科急诊，成人规则不得误套）
 * 优先级1-4：急症红色指征（神经/消化道/哮喘持续状态/颅压危象）→ EMERGENCY 分流
 * 优先级5：妊娠相关 → 产科
 * 优先级6：心血管危险症状 → 心内科（胸闷需危重伴随症才升急，稳定型属常规评估）
 * 优先级7：代谢症状 → 全科初诊
 * 优先级8：呼吸症状 → 呼吸内科
 * 优先级9：消化症状 → 消化内科方向（全科首诊转诊）
 * 兜底：无法识别（含头晕/乏力/失眠等非特异主诉）→ 全科 + degraded=true
 *（保留真实降级语义，诚实标注需要人工分诊）
 *
 * 所有命中均 degraded=false + 规则置信度（0.80），与 LLM 置信度可区分；
 * 输出统一经过 TriageSafetyNet 红线覆写（见 AiOrchestrationService）。
 *
 * 推荐医生 ID 与种子数据（sql/kingbase_seed_ascii.sql）对齐：
 * 1=心内科 Dr Zhang，2=全科 Dr Li，3=呼吸内科 Dr Wang；
 * 新增科室方向（消化/神经/儿科/产科）种子中无对应医生，统一推荐全科(2)首诊转诊。
 * 生产部署应改为经排班服务按科室动态解析在班医生。
 */
@Component
@ConditionalOnProperty(prefix = "ai", name = "provider", havingValue = "mock")
public class MockAiProvider implements AiProvider {

  /** 科室 → 种子数据在班医生（演示环境；生产经排班服务动态解析）。 */
  private static final Map<String, List<Long>> SEED_DOCTORS = Map.of(
      "CARDIOLOGY", List.of(1L),
      "GENERAL", List.of(2L),
      "RESPIRATORY", List.of(3L));
  private static final List<Long> GENERAL_REFERRAL = List.of(2L);

  private static final List<String> NEURO_RED_FLAGS = List.of(
      "意识不清", "言语不清", "偏瘫", "面瘫", "肢体麻木", "抽搐", "昏迷");
  private static final List<String> GI_RED_FLAGS = List.of("呕血", "便血", "黑便");
  private static final List<String> ASTHMA_MARKERS = List.of("哮喘", "喘息", "wheez");
  private static final List<String> BREATHLESS = List.of("呼吸困难", "讲话困难", "气促", "dyspnea");

  private static final List<String> CARDIO_DANGER = List.of(
      "胸痛", "胸闷", "心悸", "气短", "晕厥", "chest pain", "dyspnea", "palpitation", "syncope");
  private static final List<String> CARDIO_EMERGENCY_ACCOMPANIED = List.of("大汗", "晕厥", "呼吸困难", "意识不清");
  private static final List<String> CARDIO_SOLO_EMERGENCY = List.of("胸痛", "晕厥", "chest pain");

  private static final List<String> PEDIATRIC = List.of("患儿", "儿童", "小儿", "小孩", "孩子", "child");
  private static final List<String> PREGNANCY = List.of("怀孕", "妊娠", "孕妇", "pregnan");

  private static final List<String> METABOLIC = List.of(
      "多饮", "多尿", "口渴", "血糖", "体重下降", "polyuria", "thirst", "glucose");
  private static final List<String> RESPIRATORY = List.of(
      "咳嗽", "咳痰", "发热", "咽痛", "哮喘", "cough", "fever", "sore throat");
  private static final List<String> DIGESTIVE = List.of(
      "腹痛", "腹泻", "呕吐", "反酸", "恶心", "腹胀", "abdominal pain", "vomit", "diarrhea");
  /** 颅压危象组合：头痛伴呕吐/视物模糊 → 急诊（单纯头痛属非特异症状走诚实降级）。 */
  private static final List<String> HEADACHE = List.of("头痛", "headache");
  private static final List<String> HEADACHE_ACCOMPANIED = List.of("呕吐", "视物模糊", "突发剧痛");

  @Override
  public String providerName() {
    return "mock";
  }

  @Override
  public TriageResponse triage(TriageRequest request, PromptResolveResponse prompt) {
    String complaint = request.chiefComplaint() == null ? "" : request.chiefComplaint();
    String symptoms = request.symptoms() == null ? "" : request.symptoms();
    String text = (complaint + " " + symptoms).toLowerCase();

    // 优先级0：儿科红色指征——患儿主诉最先按年龄层路由（成人规则不得误套于儿童）
    if (SymptomMatcher.mentions(text, PEDIATRIC)) {
      boolean childEmergency = SymptomMatcher.mentions(text,
          List.of("高热", "抽搐", "呼吸困难", "意识不清", "喘息", "脱水"));
      return triage("Pediatrics", "PEDIATRICS", "儿科方向", childEmergency ? "EMERGENCY" : "ROUTINE",
          0.80, GENERAL_REFERRAL,
          childEmergency
              ? "规则引擎：患儿伴危重表现，紧急度EMERGENCY，立即儿科/急诊处置"
              : "规则引擎：患儿主诉，推荐儿科就诊（种子环境建议全科首诊转诊）",
          false);
    }
    // 优先级1：神经急症红色指征（卒中/昏迷）→ 急诊分流（成人）
    if (SymptomMatcher.mentions(text, NEURO_RED_FLAGS)) {
      return triage("General Practice", "GENERAL", "急诊方向", "EMERGENCY", 0.85, GENERAL_REFERRAL,
          "规则引擎：识别卒中/意识急症红色指征，紧急度EMERGENCY，建议立即急诊医学科就诊（卒中黄金3小时），勿等待普通门诊",
          false);
    }
    // 优先级2：消化道急症（呕血/黑便提示上消化道大出血）
    if (SymptomMatcher.mentions(text, GI_RED_FLAGS)) {
      return triage("General Practice", "GENERAL", "急诊方向", "EMERGENCY", 0.85, GENERAL_REFERRAL,
          "规则引擎：呕血/黑便提示上消化道出血，紧急度EMERGENCY，立即急诊并建立静脉通路",
          false);
    }
    // 优先级3：哮喘持续状态（哮喘/喘息 + 呼吸困难）→ 呼吸急症，避免被心血管规则误路由
    if (SymptomMatcher.mentions(text, ASTHMA_MARKERS) && SymptomMatcher.mentions(text, BREATHLESS)) {
      return triage("Respiratory Medicine", "RESPIRATORY", "呼吸急症方向", "EMERGENCY", 0.85,
          SEED_DOCTORS.get("RESPIRATORY"),
          "规则引擎：哮喘伴呼吸困难提示哮喘持续状态风险，紧急度EMERGENCY，立即呼吸内科/急诊处置",
          false);
    }
    // 优先级4：颅压危象组合（头痛伴呕吐/视物模糊）
    if (SymptomMatcher.mentions(text, HEADACHE) && SymptomMatcher.mentions(text, HEADACHE_ACCOMPANIED)) {
      return triage("General Practice", "GENERAL", "急诊方向", "EMERGENCY", 0.85, GENERAL_REFERRAL,
          "规则引擎：头痛伴呕吐/视物模糊提示颅内压升高，紧急度EMERGENCY，立即急诊排除脑血管意外",
          false);
    }
    // 优先级5：妊娠相关 → 产科
    if (SymptomMatcher.mentions(text, PREGNANCY)) {
      return triage("Obstetrics", "OBSTETRICS", "产科方向", "ROUTINE", 0.80, GENERAL_REFERRAL,
          "规则引擎：妊娠相关主诉，推荐产科就诊（种子环境建议全科首诊转诊）",
          false);
    }
    // 优先级6：心血管——胸痛/晕厥单独即急症；胸闷/心悸需危重伴随症才升急（稳定型属常规评估）
    if (SymptomMatcher.mentions(text, CARDIO_DANGER)) {
      boolean emergency = SymptomMatcher.mentions(text, CARDIO_SOLO_EMERGENCY)
          || (SymptomMatcher.mentions(text, List.of("胸闷", "心悸"))
              && SymptomMatcher.mentions(text, CARDIO_EMERGENCY_ACCOMPANIED));
      return triage("Cardiology", "CARDIOLOGY", "心血管方向", emergency ? "EMERGENCY" : "ROUTINE", 0.80,
          SEED_DOCTORS.get("CARDIOLOGY"),
          emergency
              ? "规则引擎：主诉含危险心血管症状，紧急度EMERGENCY，需立即排除急性冠脉综合征"
              : "规则引擎：主诉含胸闷/心悸等心血管症状，推荐心内科评估",
          false);
    }
    // 优先级7：代谢症状 → 全科初诊（糖尿病方向）
    if (SymptomMatcher.mentions(text, METABOLIC)) {
      return triage("General Practice", "GENERAL", "全科/内分泌方向", "ROUTINE", 0.80,
          SEED_DOCTORS.get("GENERAL"),
          "规则引擎：主诉含多饮/多尿/血糖升高等代谢症状，推荐全科门诊完善血糖评估",
          false);
    }
    // 优先级8：呼吸症状 → 呼吸内科
    if (SymptomMatcher.mentions(text, RESPIRATORY)) {
      return triage("Respiratory Medicine", "RESPIRATORY", "呼吸方向", "ROUTINE", 0.80,
          SEED_DOCTORS.get("RESPIRATORY"),
          "规则引擎：主诉含咳嗽/发热等呼吸道症状，推荐呼吸内科",
          false);
    }
    // 优先级9：消化症状 → 消化内科方向（全科首诊转诊）
    if (SymptomMatcher.mentions(text, DIGESTIVE)) {
      return triage("Gastroenterology", "GASTROENTEROLOGY", "消化方向", "ROUTINE", 0.80, GENERAL_REFERRAL,
          "规则引擎：主诉含腹痛/腹泻等消化症状，推荐消化内科（种子环境建议全科首诊转诊）",
          false);
    }
    // 兜底：无法识别 → 真实降级语义（需人工分诊）。头晕/乏力/失眠等非特异主诉
    // 保留在降级层——诚实标注"需要人工"，好过硬猜一个科室（真实降级语义用例）。
    return triage("General Practice", "GENERAL", "", "ROUTINE", 0.50, GENERAL_REFERRAL,
        "规则引擎无法识别症状特征，降级为全科人工分诊",
        true);
  }

  private TriageResponse triage(String department, String departmentCode, String direction,
                                String urgency, double confidence, List<Long> doctorIds,
                                String reason, boolean degraded) {
    return new TriageResponse(department, departmentCode, direction, urgency, confidence, doctorIds, reason, degraded);
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
