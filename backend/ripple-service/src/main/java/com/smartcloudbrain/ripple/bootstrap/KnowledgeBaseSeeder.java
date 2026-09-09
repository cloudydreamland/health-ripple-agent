package com.smartcloudbrain.ripple.bootstrap;

import com.smartcloudbrain.ripple.entity.ChronoRule;
import com.smartcloudbrain.ripple.entity.ComplicationSignalRule;
import com.smartcloudbrain.ripple.entity.DrugLifestyleConflictRule;
import com.smartcloudbrain.ripple.entity.RecheckWindowRule;
import com.smartcloudbrain.ripple.repository.ChronoRuleRepository;
import com.smartcloudbrain.ripple.repository.ComplicationSignalRuleRepository;
import com.smartcloudbrain.ripple.repository.DrugLifestyleConflictRuleRepository;
import com.smartcloudbrain.ripple.repository.RecheckWindowRuleRepository;
import com.smartcloudbrain.ripple.service.KnowledgeBaseService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 涟漪知识库种子装载器：表空时写入医疗时间学规则等知识（幂等）。
 *
 * drug_lifestyle_conflict / complication_signal / recheck_window 三表
 * 已由 sql/mysql_schema.sql 预置种子，此处补齐 chrono_rule 时间学规则，
 * 保证服务在任意空库上自举可演示（真实知识沉淀，非硬编码逻辑）。
 */
@Component
public class KnowledgeBaseSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseSeeder.class);

  private final DrugLifestyleConflictRuleRepository conflictRepository;
  private final ComplicationSignalRuleRepository signalRepository;
  private final RecheckWindowRuleRepository recheckRepository;
  private final ChronoRuleRepository chronoRuleRepository;
  private final KnowledgeBaseService knowledgeBaseService;

  public KnowledgeBaseSeeder(
      DrugLifestyleConflictRuleRepository conflictRepository,
      ComplicationSignalRuleRepository signalRepository,
      RecheckWindowRuleRepository recheckRepository,
      ChronoRuleRepository chronoRuleRepository,
      KnowledgeBaseService knowledgeBaseService) {
    this.conflictRepository = conflictRepository;
    this.signalRepository = signalRepository;
    this.recheckRepository = recheckRepository;
    this.chronoRuleRepository = chronoRuleRepository;
    this.knowledgeBaseService = knowledgeBaseService;
  }

  @Override
  public void run(String... args) {
    seedDrugConflicts();
    seedComplicationSignals();
    seedRecheckWindows();
    seedChronoRules();
    knowledgeBaseService.invalidateCache();
  }

  private void seedDrugConflicts() {
    if (conflictRepository.count() > 0) {
      return;
    }
    List.of(
        row("二甲双胍", "饮酒", "乳酸酸中毒（严重可致死）", "HIGH", "服药期间禁止饮酒"),
        row("二甲双胍", "维生素B12缺乏", "长期服用致B12缺乏性贫血", "MEDIUM", "建议定期监测B12水平"),
        row("二甲双胍", "造影剂联用", "肾损伤", "HIGH", "造影检查前需提前停药48小时"),
        row("华法林", "柚子/葡萄柚", "增强抗凝效果，出血风险", "HIGH", "服药期间禁止食用柚子"),
        row("华法林", "大量绿叶蔬菜（维生素K）", "降低抗凝效果，血栓风险", "MEDIUM", "保持稳定摄入量，勿突然增减"),
        row("阿莫西林", "饮酒", "双硫仑样反应", "MEDIUM", "服药期间及停药后7天避免饮酒"),
        row("布洛芬", "饮酒", "胃肠道出血", "HIGH", "服药期间禁止饮酒"),
        row("布洛芬", "空腹服用", "胃黏膜损伤", "MEDIUM", "建议餐后服用"),
        row("他汀类", "柚子/葡萄柚", "肌病/横纹肌溶解风险", "HIGH", "服药期间禁止食用柚子"),
        row("头孢类", "饮酒", "双硫仑样反应（严重可致死）", "HIGH", "服药期间及停药后7-10天禁止饮酒"),
        row("磺脲类降糖药", "饮酒", "低血糖/双硫仑反应", "HIGH", "服药期间禁止饮酒"),
        row("四环素", "日晒", "光敏反应", "MEDIUM", "服药期间避免强烈日晒"),
        row("甲氨蝶呤", "饮酒", "肝损伤加重", "HIGH", "服药期间禁止饮酒"))
        .forEach(conflictRepository::save);
    log.info("药物-生活冲突知识库已装载: {} 条", conflictRepository.count());
  }

  private void seedComplicationSignals() {
    if (signalRepository.count() > 0) {
      return;
    }
    List.of(
        signal("2型糖尿病", "视力模糊/飞蚊症突发", "糖尿病视网膜病变", "立即眼科就诊", "HIGH"),
        signal("2型糖尿病", "足部感觉异常/伤口不愈", "糖尿病足", "立即外科就诊", "HIGH"),
        signal("2型糖尿病", "心悸/出汗/手抖/饥饿感", "低血糖", "即时补糖并就医", "HIGH"),
        signal("2型糖尿病", "多尿/多饮/乏力加重/意识模糊", "糖尿病酮症酸中毒", "立即急诊", "HIGH"),
        signal("高血压", "剧烈头痛/呕吐/视物模糊", "高血压危象", "立即急诊", "HIGH"),
        signal("高血压", "胸痛/胸闷/大汗", "心肌梗死/主动脉夹层", "立即急诊（黄金120分钟）", "HIGH"),
        signal("高血压", "肢体麻木/言语不清/面瘫", "脑卒中", "立即急诊（黄金3小时）", "HIGH"),
        signal("冠心病", "持续胸痛>15分钟/含服硝酸甘油不缓解", "急性心肌梗死", "立即急诊（黄金120分钟）", "HIGH"),
        signal("冠心病", "夜间阵发呼吸困难/不能平卧", "心力衰竭", "心内科就诊", "HIGH"),
        signal("哮喘", "呼吸困难加重/讲话困难/嗜睡", "哮喘持续状态", "立即急诊", "HIGH"),
        signal("慢性肾病", "尿量骤减/水肿加重", "肾功能急性恶化", "立即肾内科", "HIGH"),
        signal("慢性肾病", "呼吸困难/不能平卧", "心衰/容量超负荷", "立即急诊", "HIGH"))
        .forEach(signalRepository::save);
    log.info("并发症早期信号知识库已装载: {} 条", signalRepository.count());
  }

  private void seedRecheckWindows() {
    if (recheckRepository.count() > 0) {
      return;
    }
    List.of(
        recheck("2型糖尿病", "肝肾功能+空腹血糖", "服药2周后", "PERIODIC", "二甲双胍起始治疗后必查"),
        recheck("2型糖尿病", "糖化血红蛋白(HbA1c)", "3个月后", "PERIODIC", "评估血糖长期控制"),
        recheck("2型糖尿病", "眼底/足部/尿微量白蛋白", "每年", "PERIODIC", "并发症筛查"),
        recheck("高血压", "血压复查", "服药2周后", "PERIODIC", "评估降压效果"),
        recheck("高血压", "肝肾功能+电解质", "1-3个月后", "PERIODIC", "ACEI/利尿剂监测"),
        recheck("高血压", "心电图/心脏超声", "每年", "PERIODIC", "靶器官损害评估"),
        recheck("冠心病", "症状复查+心电图", "服药1-2周后", "PERIODIC", "评估治疗反应"),
        recheck("华法林", "INR凝血指标", "服药3-5天后", "PERIODIC", "调整剂量必查"),
        recheck("华法林", "INR凝血指标", "稳定后每月", "PERIODIC", "稳定期监测"))
        .forEach(recheckRepository::save);
    log.info("复查窗口知识库已装载: {} 条", recheckRepository.count());
  }

  /**
   * 医疗时间学规则：窗口期/节律/周期/季节，含结构化字段供 ChronoEngine 计算。
   * 每条规则携带 Timing Card 循证卡片（触发依据/错过代价/证据等级），
   * 使时间学触达从"提醒功能"升级为"可审计的循证资产"（对标 model card 实践）。
   * 单一数据源 CHRONO_RULES 同时驱动：空库全量装载 + 存量规则循证回填升级。
   */
  private record ChronoSeed(
      String dx, String type, String event, String triggerTime, String action,
      Integer startHour, Integer offsetDays, Integer periodDays, Integer targetMonth,
      String evidenceBasis, String missCost, String evidenceLevel) {
  }

  private static final List<ChronoSeed> CHRONO_RULES = List.of(
      // 窗口期：即时一次性高优先级触达
      new ChronoSeed("冠心病", "WINDOW", "心梗黄金救治窗口", "胸痛发作后120分钟内", "即时高优先级触达并引导急诊", null, null, null, null,
          "AHA/ACC STEMI指南：直接PCI应在首次医疗接触后120分钟内完成",
          "再灌注每延迟30分钟，1年死亡率相对风险增加约7.5%", "GUIDELINE"),
      // 节律性：每日固定时段主动询问
      new ChronoSeed("2型糖尿病", "RHYTHM", "夜间0-3点低血糖高发", "凌晨0-3点", "主动询问患者状态", 0, null, null, null,
          "ADA糖尿病诊疗标准：夜间0-3点为低血糖高发时段",
          "夜间低血糖未识别→无感知低血糖综合征，心血管事件风险倍增", "GUIDELINE"),
      new ChronoSeed("高血压", "RHYTHM", "凌晨血压晨峰", "凌晨4-6点", "主动询问晨起血压", 4, null, null, null,
          "AHA昼夜节律与心血管健康科学声明(2025)：清晨血压晨峰现象",
          "晨峰未控制→清晨心梗/卒中发生率为日间其他时段2-3倍", "GUIDELINE"),
      new ChronoSeed("哮喘", "RHYTHM", "夜间哮喘发作高峰", "凌晨3-5点", "主动询问呼吸状态", 3, null, null, null,
          "GINA全球哮喘处理和预防策略：夜间症状提示控制不佳",
          "夜间发作未干预→哮喘持续状态风险显著升高", "GUIDELINE"),
      // 周期性：偏移天数起算
      new ChronoSeed("2型糖尿病", "PERIODIC", "服药2周后复查肝肾功能", "服药后第14天", "主动提醒复查", null, 14, null, null,
          "中国2型糖尿病防治指南：二甲双胍起始治疗后需评估肝肾功能",
          "漏查→乳酸酸中毒高危因素未能及时发现", "GUIDELINE"),
      new ChronoSeed("2型糖尿病", "PERIODIC", "3个月后查糖化血红蛋白", "诊断后第90天", "主动提醒复查", null, 90, null, null,
          "ADA标准：治疗调整后每3个月评估HbA1c",
          "延迟评估→血糖失控窗口期延长，并发症累积风险增加", "GUIDELINE"),
      new ChronoSeed("高血压", "PERIODIC", "服药2周后血压复查", "服药后第14天", "主动提醒复查", null, 14, null, null,
          "中国高血压防治指南：起始降压治疗后2-4周需评估疗效",
          "漏查→药物无效或不耐受未及时调整，靶器官损害持续", "GUIDELINE"),
      new ChronoSeed("华法林", "PERIODIC", "每月复查INR", "稳定后每月", "主动提醒复查INR", null, 3, 30, null,
          "房颤抗凝指南：华法林INR达标稳定后每4周监测",
          "INR失控→出血或血栓事件风险倍增", "GUIDELINE"),
      // 季节性：目标月份触达
      new ChronoSeed("2型糖尿病", "SEASONAL", "换季血糖波动", "秋冬换季", "主动提醒血糖监测", null, null, null, 9,
          "人群纵向观察研究：秋冬季节血糖控制显著变差",
          "未增加监测→冬季并发症风险上升", "OBSERVATIONAL"),
      new ChronoSeed("高血压", "SEASONAL", "秋冬血压升高", "入秋/入冬", "主动提醒增加监测频次", null, null, null, 9,
          "AHA科学声明：冬季收缩压平均升高5-10mmHg",
          "未及时调整用药→冬季心脑血管事件高发期风险叠加", "OBSERVATIONAL"),
      new ChronoSeed("哮喘", "SEASONAL", "春季花粉诱发", "春季花粉季", "主动提醒预防用药", null, null, null, 3,
          "GINA策略：花粉季前2-4周启动预防性用药",
          "错过预防窗口→季节性哮喘发作失控", "GUIDELINE"));

  private void seedChronoRules() {
    if (chronoRuleRepository.count() == 0) {
      CHRONO_RULES.forEach(seed -> chronoRuleRepository.save(toRule(seed)));
      log.info("医疗时间学规则库已装载: {} 条（含Timing Card循证卡片）", chronoRuleRepository.count());
      return;
    }
    backfillTimingCards();
  }

  /** 存量规则回填：为缺失循证字段的旧规则按事件名匹配补齐 Timing Card（数据升级迁移）。 */
  private void backfillTimingCards() {
    List<ChronoRule> legacyRules = chronoRuleRepository.findByEvidenceBasisIsNull();
    if (legacyRules.isEmpty()) {
      return;
    }
    java.util.Map<String, ChronoSeed> byEvent = new java.util.HashMap<>();
    CHRONO_RULES.forEach(seed -> byEvent.put(seed.event(), seed));
    int backfilled = 0;
    for (ChronoRule legacy : legacyRules) {
      ChronoSeed seed = byEvent.get(legacy.getEvent());
      if (seed != null) {
        legacy.setEvidenceBasis(seed.evidenceBasis());
        legacy.setMissCost(seed.missCost());
        legacy.setEvidenceLevel(seed.evidenceLevel());
        chronoRuleRepository.save(legacy);
        backfilled++;
      }
    }
    log.info("Timing Card循证卡片回填升级: {}/{} 条存量规则", backfilled, legacyRules.size());
  }

  private ChronoRule toRule(ChronoSeed seed) {
    ChronoRule rule = new ChronoRule();
    rule.setDiagnosisKeyword(seed.dx());
    rule.setChronoType(seed.type());
    rule.setEvent(seed.event());
    rule.setTriggerTime(seed.triggerTime());
    rule.setAction(seed.action());
    rule.setStartHour(seed.startHour());
    rule.setOffsetDays(seed.offsetDays());
    rule.setPeriodDays(seed.periodDays());
    rule.setTargetMonth(seed.targetMonth());
    rule.setEvidenceBasis(seed.evidenceBasis());
    rule.setMissCost(seed.missCost());
    rule.setEvidenceLevel(seed.evidenceLevel());
    return rule;
  }

  private DrugLifestyleConflictRule row(String drug, String conflict, String risk, String severity, String advice) {
    DrugLifestyleConflictRule rule = new DrugLifestyleConflictRule();
    rule.setDrugKeyword(drug);
    rule.setConflictItem(conflict);
    rule.setRiskDescription(risk);
    rule.setSeverity(severity);
    rule.setAdvice(advice);
    return rule;
  }

  private ComplicationSignalRule signal(String dx, String symptom, String complication, String action, String urgency) {
    ComplicationSignalRule rule = new ComplicationSignalRule();
    rule.setDiagnosisKeyword(dx);
    rule.setSignalSymptom(symptom);
    rule.setComplication(complication);
    rule.setActionAdvice(action);
    rule.setUrgency(urgency);
    return rule;
  }

  private RecheckWindowRule recheck(String dx, String item, String timing, String chronoType, String advice) {
    RecheckWindowRule rule = new RecheckWindowRule();
    rule.setDiagnosisKeyword(dx);
    rule.setItem(item);
    rule.setTiming(timing);
    rule.setChronoType(chronoType);
    rule.setAdvice(advice);
    return rule;
  }
}
