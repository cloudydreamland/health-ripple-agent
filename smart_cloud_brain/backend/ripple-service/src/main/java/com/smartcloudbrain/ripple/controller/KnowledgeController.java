package com.smartcloudbrain.ripple.controller;

import com.smartcloudbrain.common.result.Result;
import com.smartcloudbrain.ripple.service.KnowledgeBaseService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 涟漪知识库查询 API（Skill conflict/complication 动作的后端实现）。 */
@RestController
@RequestMapping("/api")
public class KnowledgeController {

  private final KnowledgeBaseService knowledgeBaseService;

  public KnowledgeController(KnowledgeBaseService knowledgeBaseService) {
    this.knowledgeBaseService = knowledgeBaseService;
  }

  /** GET /api/drug/lifestyle-conflict?drug=二甲双胍 — 药物-生活冲突知识查询。 */
  @GetMapping("/drug/lifestyle-conflict")
  public Result<?> drugConflicts(@RequestParam String drug) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (var rule : knowledgeBaseService.findDrugConflicts(drug)) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("conflict", rule.getConflictItem());
      item.put("risk", rule.getRiskDescription());
      item.put("severity", rule.getSeverity());
      item.put("advice", rule.getAdvice());
      result.add(item);
    }
    return Result.success(result);
  }

  /** GET /api/complication/signal?diagnosis=2型糖尿病 — 并发症早期信号知识查询。 */
  @GetMapping("/complication/signal")
  public Result<?> complicationSignals(@RequestParam String diagnosis) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (var rule : knowledgeBaseService.findComplicationSignals(diagnosis)) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("signal", rule.getSignalSymptom());
      item.put("complication", rule.getComplication());
      item.put("action", rule.getActionAdvice());
      item.put("urgency", rule.getUrgency());
      result.add(item);
    }
    return Result.success(result);
  }
}
