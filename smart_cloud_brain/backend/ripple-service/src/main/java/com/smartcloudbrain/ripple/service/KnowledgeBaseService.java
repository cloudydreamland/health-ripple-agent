package com.smartcloudbrain.ripple.service;

import com.smartcloudbrain.ripple.entity.ChronoRule;
import com.smartcloudbrain.ripple.entity.ComplicationSignalRule;
import com.smartcloudbrain.ripple.entity.DrugLifestyleConflictRule;
import com.smartcloudbrain.ripple.entity.RecheckWindowRule;
import com.smartcloudbrain.ripple.repository.ChronoRuleRepository;
import com.smartcloudbrain.ripple.repository.ComplicationSignalRuleRepository;
import com.smartcloudbrain.ripple.repository.DrugLifestyleConflictRuleRepository;
import com.smartcloudbrain.ripple.repository.RecheckWindowRuleRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 涟漪知识库服务：从数据库规则表加载知识并做关键词双向包含匹配。
 *
 * 匹配策略（与 Skill 内置降级知识库一致）：
 * keyword in term 或 term in keyword —— 兼容"二甲双胍片"命中"二甲双胍"。
 * 规则表为低频变更数据，加载后缓存于内存，种子写入时失效。
 */
@Service
public class KnowledgeBaseService {

  private final DrugLifestyleConflictRuleRepository conflictRepository;
  private final ComplicationSignalRuleRepository signalRepository;
  private final RecheckWindowRuleRepository recheckRepository;
  private final ChronoRuleRepository chronoRuleRepository;

  private volatile List<DrugLifestyleConflictRule> conflictCache;
  private volatile List<ComplicationSignalRule> signalCache;
  private volatile List<RecheckWindowRule> recheckCache;
  private volatile List<ChronoRule> chronoCache;

  public KnowledgeBaseService(
      DrugLifestyleConflictRuleRepository conflictRepository,
      ComplicationSignalRuleRepository signalRepository,
      RecheckWindowRuleRepository recheckRepository,
      ChronoRuleRepository chronoRuleRepository) {
    this.conflictRepository = conflictRepository;
    this.signalRepository = signalRepository;
    this.recheckRepository = recheckRepository;
    this.chronoRuleRepository = chronoRuleRepository;
  }

  public List<DrugLifestyleConflictRule> findDrugConflicts(String drugName) {
    List<DrugLifestyleConflictRule> result = new ArrayList<>();
    if (drugName == null || drugName.isBlank()) {
      return result;
    }
    for (DrugLifestyleConflictRule rule : allConflicts()) {
      if (matches(rule.getDrugKeyword(), drugName)) {
        result.add(rule);
      }
    }
    return result;
  }

  public List<ComplicationSignalRule> findComplicationSignals(String diagnosis) {
    List<ComplicationSignalRule> result = new ArrayList<>();
    if (diagnosis == null || diagnosis.isBlank()) {
      return result;
    }
    for (ComplicationSignalRule rule : allSignals()) {
      if (matches(rule.getDiagnosisKeyword(), diagnosis)) {
        result.add(rule);
      }
    }
    return result;
  }

  public List<RecheckWindowRule> findRecheckWindows(String diagnosis) {
    List<RecheckWindowRule> result = new ArrayList<>();
    if (diagnosis == null || diagnosis.isBlank()) {
      return result;
    }
    for (RecheckWindowRule rule : allRechecks()) {
      if (matches(rule.getDiagnosisKeyword(), diagnosis)) {
        result.add(rule);
      }
    }
    return result;
  }

  public List<ChronoRule> findChronoRules(String diagnosis) {
    List<ChronoRule> result = new ArrayList<>();
    if (diagnosis == null || diagnosis.isBlank()) {
      return result;
    }
    for (ChronoRule rule : allChronoRules()) {
      if (matches(rule.getDiagnosisKeyword(), diagnosis)) {
        result.add(rule);
      }
    }
    return result;
  }

  /** 供 /api/drug/lifestyle-conflict 直接查询。 */
  public List<DrugLifestyleConflictRule> allConflicts() {
    if (conflictCache == null) {
      conflictCache = conflictRepository.findAll();
    }
    return conflictCache;
  }

  public List<ComplicationSignalRule> allSignals() {
    if (signalCache == null) {
      signalCache = signalRepository.findAll();
    }
    return signalCache;
  }

  public List<RecheckWindowRule> allRechecks() {
    if (recheckCache == null) {
      recheckCache = recheckRepository.findAll();
    }
    return recheckCache;
  }

  public List<ChronoRule> allChronoRules() {
    if (chronoCache == null) {
      chronoCache = chronoRuleRepository.findAll();
    }
    return chronoCache;
  }

  public void invalidateCache() {
    conflictCache = null;
    signalCache = null;
    recheckCache = null;
    chronoCache = null;
  }

  static boolean matches(String keyword, String term) {
    if (keyword == null || term == null) {
      return false;
    }
    String k = keyword.trim();
    String t = term.trim();
    return !k.isEmpty() && !t.isEmpty() && (k.contains(t) || t.contains(k));
  }
}
