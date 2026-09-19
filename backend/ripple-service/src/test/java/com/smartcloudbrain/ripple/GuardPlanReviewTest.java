package com.smartcloudbrain.ripple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcloudbrain.ripple.dto.DrugItem;
import com.smartcloudbrain.ripple.dto.RippleDeriveRequest;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.repository.ChronoTriggerRepository;
import com.smartcloudbrain.ripple.service.ChronoEngine;
import com.smartcloudbrain.ripple.service.RippleDeriveService;
import com.smartcloudbrain.ripple.service.RippleForecastService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第七轮创新单元测试：医生守护计划审定（人机共驾终审）+ 依从性沙盘。
 *
 * 审定的关键性质：
 * 1. APPROVE 通过后计划照常执行（reviewStatus=APPROVED、status 保持 ACTIVE）；
 * 2. ADJUST 改期后 nextTriggerAt 更新且调度恢复；
 * 3. VETO 否决后计划终止——从预报中剔除（suppressedVetoed 计数），且不可复活（幂等）；
 * 4. 非法决策/缺参被拒绝；
 * 5. 依从性沙盘：adherence=1 时非季节驱动全部消解，沙盘峰值 ≤ 当前峰值且严格更低（有非季节驱动时）；
 *    adherence=0 不附带沙盘。
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ripple_review_test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("test")
class GuardPlanReviewTest {

  @Autowired
  private RippleDeriveService rippleDeriveService;
  @Autowired
  private RippleForecastService forecastService;
  @Autowired
  private ChronoEngine chronoEngine;
  @Autowired
  private ChronoTriggerRepository triggerRepository;

  private List<ChronoTrigger> triggersOf(long patientId) {
    return triggerRepository.findByPatientIdOrderByNextTriggerAtAsc(patientId).stream()
        .filter(t -> t.getNextTriggerAt() != null && t.getNextTriggerAt().isAfter(LocalDateTime.now()))
        .toList();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void review_approveKeepsPlanActive() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        401L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), ""));
    ChronoTrigger trigger = triggersOf(401L).get(0);

    ChronoTrigger reviewed = chronoEngine.review(trigger.getId(), "APPROVE", "按指南执行", "张医生", null);
    assertEquals("APPROVED", reviewed.getReviewStatus());
    assertEquals("ACTIVE", reviewed.getStatus());
    assertEquals("张医生", reviewed.getReviewer());
    assertNotNull(reviewed.getReviewAt());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void review_adjustReschedulesAndKeepsActive() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        402L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), ""));
    ChronoTrigger trigger = triggersOf(402L).get(0);
    LocalDateTime newTime = LocalDateTime.now().plusHours(24);

    ChronoTrigger reviewed = chronoEngine.review(trigger.getId(), "ADJUST", "改期至明天", "张医生", newTime);
    assertEquals("ADJUSTED", reviewed.getReviewStatus());
    assertEquals("ACTIVE", reviewed.getStatus());
    assertEquals(newTime.withSecond(0), reviewed.getNextTriggerAt().withSecond(0),
        "调整后触达时点应为医生指定时间");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void review_adjustWithoutNextAtRejected() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        403L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), ""));
    ChronoTrigger trigger = triggersOf(403L).get(0);
    assertThrows(IllegalArgumentException.class,
        () -> chronoEngine.review(trigger.getId(), "ADJUST", "", "张医生", null));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void review_illegalDecisionRejected() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        404L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), ""));
    ChronoTrigger trigger = triggersOf(404L).get(0);
    assertThrows(IllegalArgumentException.class,
        () -> chronoEngine.review(trigger.getId(), "MAYBE", "", "张医生", null));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void review_vetoRemovesFromForecastAndIsIrrevocable() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        405L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), ""));
    List<ChronoTrigger> triggers = triggersOf(405L);
    assertFalse(triggers.isEmpty());
    Map<String, Object> before = forecastService.forecast(405L);
    int beforeActive = ((Number) before.get("activeTriggers")).intValue();
    double beforePeak = ((Number) ((Map<?, ?>) before.get("peak")).get("intensity")).doubleValue();

    // 医生否决其中一条（选未来 30 小时内有节律峰的低血糖触达，保证它对预报有贡献）
    ChronoTrigger target = triggers.stream()
        .filter(t -> String.valueOf(t.getEvent()).contains("低血糖"))
        .findFirst()
        .orElse(triggers.get(0));
    ChronoTrigger vetoed = chronoEngine.review(target.getId(), "VETO", "患者已住院监测，暂停自动触达", "张医生", null);
    assertEquals("VETOED", vetoed.getReviewStatus());
    assertEquals("VETOED", vetoed.getStatus());
    assertNull(vetoed.getNextTriggerAt(), "否决后不应再有下次触达时点");

    Map<String, Object> after = forecastService.forecast(405L);
    assertEquals(beforeActive - 1, ((Number) after.get("activeTriggers")).intValue(),
        "否决后活跃触达数应减一");
    assertEquals(1, ((Number) after.get("suppressedVetoed")).intValue(),
        "预报应如实报告被否决剔除的触达数");
    double afterPeak = ((Number) ((Map<?, ?>) after.get("peak")).get("intensity")).doubleValue();
    assertTrue(afterPeak <= beforePeak, "否决守护触达后预报峰值不应升高");

    // 幂等：已否决的计划不可复活（重复否决返回同一状态）
    ChronoTrigger again = chronoEngine.review(target.getId(), "APPROVE", "试图复活", "李医生", null);
    assertEquals("VETOED", again.getStatus(), "已否决计划不可通过重审复活");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void forecast_sandboxScalesNonSeasonalDriversDeterministically() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        406L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), ""));

    Map<String, Object> base = forecastService.forecast(406L);
    assertFalse(base.containsKey("sandbox"), "adherence=0 时不附带沙盘");

    Map<String, Object> half = forecastService.forecast(406L, 0.5);
    Map<String, Object> sandbox = (Map<String, Object>) half.get("sandbox");
    assertNotNull(sandbox, "adherence>0 应返回沙盘");
    assertEquals(0.5, ((Number) sandbox.get("adherence")).doubleValue(), 0.0001);

    double baseAvg = ((Number) base.get("horizonAvg")).doubleValue();
    double sandboxAvg = ((Number) sandbox.get("horizonAvg")).doubleValue();
    assertTrue(sandboxAvg < baseAvg, "依从度 0.5 时沙盘均值应严格低于当前预报（存在非季节驱动）");

    Map<String, Object> full = forecastService.forecast(406L, 1.0);
    Map<String, Object> fullSandbox = (Map<String, Object>) full.get("sandbox");
    double fullAvg = ((Number) fullSandbox.get("horizonAvg")).doubleValue();
    // 季节基线不折减（糖尿病推演含换季触达且当前在9月），全额执行后仅剩环境基线
    assertTrue(fullAvg >= 0.0 && fullAvg < baseAvg, "全额执行后仅剩季节环境基线，均值应低于当前预报");
    // 确定性：同参数两次计算结果一致
    Map<String, Object> again = forecastService.forecast(406L, 0.5);
    assertEquals(sandbox.get("horizonAvg"), ((Map<?, ?>) again.get("sandbox")).get("horizonAvg"),
        "沙盘为确定性重算，同参数结果一致");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void forecast_sandboxUsesSameCappedBasisAsActual() {
    // 多次推演叠加大量触达（模拟真实患者600+触达）：原始桶远超100，
    // 沙盘与实际必须同用封顶口径——否则沙盘均值会"高于"当前预报，违反单调性直觉
    for (int i = 0; i < 5; i++) {
      rippleDeriveService.derive(new RippleDeriveRequest(
          407L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), "高血压"));
    }
    Map<String, Object> base = forecastService.forecast(407L);
    Map<String, Object> sandboxed = forecastService.forecast(407L, 0.8);
    Map<String, Object> sandbox = (Map<String, Object>) sandboxed.get("sandbox");
    double baseAvg = ((Number) base.get("horizonAvg")).doubleValue();
    double sandboxAvg = ((Number) sandbox.get("horizonAvg")).doubleValue();
    assertTrue(sandboxAvg <= baseAvg,
        "守护执行度0.8的沙盘均值（封顶口径）不得高于当前预报均值: " + sandboxAvg + " vs " + baseAvg);
    double basePeak = ((Number) ((Map<?, ?>) base.get("peak")).get("intensity")).doubleValue();
    double sandboxPeak = ((Number) sandbox.get("peakIntensity")).doubleValue();
    assertTrue(sandboxPeak <= basePeak, "沙盘峰值不得高于当前峰值: " + sandboxPeak + " vs " + basePeak);
  }
}
