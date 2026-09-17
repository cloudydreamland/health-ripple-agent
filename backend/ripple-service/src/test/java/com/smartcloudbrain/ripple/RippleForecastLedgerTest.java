package com.smartcloudbrain.ripple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcloudbrain.ripple.dto.DrugItem;
import com.smartcloudbrain.ripple.dto.RippleDeriveRequest;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.service.ChronoEngine;
import com.smartcloudbrain.ripple.service.GuardianLedgerService;
import com.smartcloudbrain.ripple.service.RippleDeriveService;
import com.smartcloudbrain.ripple.service.RippleForecastService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第六轮创新单元测试：72小时涟漪预报 + 守护价值账本。
 *
 * 预报的关键性质（全部确定性，可复算）：
 * 1. 糖尿病推演后，72小时轴上必然出现"夜间0-3点低血糖"节律峰；
 * 2. 冠心病推演后，黄金窗口 WINDOW 在即时桶产生最高峰；
 * 3. RESOLVED 回执后该触达从预报中剔除（预报与消解闭环联动）；
 * 4. 空患者全零（诚实空态）；两次调用结果一致（确定性）。
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ripple_test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.rabbitmq.listener.simple.auto-startup=false"
})
@ActiveProfiles("test")
class RippleForecastLedgerTest {

  @Autowired
  private RippleDeriveService rippleDeriveService;
  @Autowired
  private RippleForecastService forecastService;
  @Autowired
  private GuardianLedgerService ledgerService;
  @Autowired
  private ChronoEngine chronoEngine;
  @Autowired
  private com.smartcloudbrain.ripple.repository.ChronoTriggerRepository triggerRepository;

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> buckets(Map<String, Object> forecast) {
    return (List<Map<String, Object>>) forecast.get("buckets");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void forecast_showsRhythmPeakForDiabetesAndIsDeterministic() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        301L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), ""));

    Map<String, Object> forecast = forecastService.forecast(301L);
    assertEquals(72, ((Number) forecast.get("horizonHours")).intValue());

    // 节律峰：下一次"夜间0-3点低血糖"峰必在未来30小时内出现（墙钟无关——无论几点跑测试）
    boolean rhythmPeakFound = buckets(forecast).stream()
        .limit(31)
        .anyMatch(b -> ((Number) b.get("intensity")).doubleValue() > 0
            && String.valueOf(b.get("drivers")).contains("低血糖"));
    assertTrue(rhythmPeakFound, "未来30小时内应出现下一次夜间低血糖节律峰（预报与节律触达联动）");

    // 季节基线：9月推演 → 换季触达在整条轴上维持低幅基线（桶值>0但远低于峰值）
    Map<String, Object> peakView = (Map<String, Object>) forecast.get("peak");
    double peakIntensity = ((Number) peakView.get("intensity")).doubleValue();
    double horizonAvg = ((Number) forecast.get("horizonAvg")).doubleValue();
    assertTrue(peakIntensity > 0 && horizonAvg > 0, "72小时轴应有实际预报内容");
    assertTrue(peakIntensity < 100.0, "预报强度封顶100");

    // 确定性：同一时刻两次调用结果完全一致（无随机数）
    Map<String, Object> again = forecastService.forecast(301L);
    assertEquals(forecast.get("horizonAvg"), again.get("horizonAvg"), "确定性预报：两次调用均值一致");
    assertEquals(buckets(forecast).stream().map(b -> b.get("intensity")).toList(),
        buckets(again).stream().map(b -> b.get("intensity")).toList(), "确定性预报：逐桶一致");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void forecast_windowSpikeNearNowAndResolvedRemoved() {
    // 冠心病 → WINDOW 即时尖峰
    rippleDeriveService.derive(new RippleDeriveRequest(
        302L, "冠心病", null, ""));
    Map<String, Object> forecast = forecastService.forecast(302L);
    Map<String, Object> firstBucket = buckets(forecast).get(0);
    assertTrue(((Number) firstBucket.get("intensity")).doubleValue() > 0
        && String.valueOf(firstBucket.get("drivers")).contains("心梗黄金救治窗口"),
        "黄金窗口应在即时桶产生尖峰");

    // 全部 RESOLVED 后 → 预报清零（消解闭环联动）
    for (ChronoTrigger trigger : triggerRepository.findByPatientIdOrderByNextTriggerAtAsc(302L)) {
      if (trigger.getNextTriggerAt() != null && trigger.getNextTriggerAt().isAfter(java.time.LocalDateTime.now())) {
        trigger.setNextTriggerAt(java.time.LocalDateTime.now().minusMinutes(1));
        triggerRepository.save(trigger);
      }
      chronoEngine.feedback(trigger.getId(), "RESOLVED", "测试消解");
    }
    Map<String, Object> calm = forecastService.forecast(302L);
    double maxRemaining = buckets(calm).stream()
        .mapToDouble(b -> ((Number) b.get("intensity")).doubleValue())
        .max().orElse(0);
    assertEquals(0.0, maxRemaining, 0.0001, "全部缓解后预报应清零（RESOLVED剔除）");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void forecast_emptyPatientIsHonestZero() {
    Map<String, Object> forecast = forecastService.forecast(999301L);
    assertEquals(0, ((Number) forecast.get("activeTriggers")).intValue());
    assertEquals(0.0, ((Number) forecast.get("horizonAvg")).doubleValue(), 0.0001, "空患者预报应诚实全零");
    assertNotNull(forecast.get("model"));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void ledger_countsEvidenceAndLockedPaths() {
    rippleDeriveService.derive(new RippleDeriveRequest(
        303L, "2型糖尿病", List.of(new DrugItem("二甲双胍")), "高血压"));

    Map<String, Object> ledger = ledgerService.ledger(303L);
    assertEquals("PATIENT", ledger.get("scope"));
    assertTrue(((Number) ledger.get("totalDecisions")).intValue() >= 1, "账本应包含推演证据");
    assertTrue(((Number) ledger.get("rippleDerivations")).intValue() >= 1);
    assertTrue(((Number) ledger.get("flaggedPathsLocked")).intValue() >= 1,
        "糖尿病高危推演必然有FLAGGED路径入账本");
    assertTrue(String.valueOf(ledger.get("narrative")).contains("印鉴链在案"));
  }
}
