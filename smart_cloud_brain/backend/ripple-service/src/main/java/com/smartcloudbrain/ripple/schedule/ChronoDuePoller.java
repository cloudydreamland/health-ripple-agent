package com.smartcloudbrain.ripple.schedule;

import com.smartcloudbrain.common.event.DomainEventNames;
import com.smartcloudbrain.ripple.entity.ChronoTrigger;
import com.smartcloudbrain.ripple.event.RippleEventPublisher;
import com.smartcloudbrain.ripple.service.ChronoEngine;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 时间学到期轮询器（后端侧主动触达，与 DuMate 定时任务双通道）。
 *
 * 每分钟扫描到期的时间学触达计划（夜间低血糖询问/复查提醒/换季监测），
 * 发布 chrono.trigger.due 领域事件 → 通知服务生成患者触达消息。
 * 与 DuMate 定时任务轮询 /api/chrono/due 形成"平台定时任务 + 后端事件驱动"双通道，
 * 任一通道可用即保证主动守护不缺席（高可用设计）。
 */
@Component
public class ChronoDuePoller {

  private static final Logger log = LoggerFactory.getLogger(ChronoDuePoller.class);

  private final ChronoEngine chronoEngine;
  private final RippleEventPublisher eventPublisher;

  public ChronoDuePoller(ChronoEngine chronoEngine, RippleEventPublisher eventPublisher) {
    this.chronoEngine = chronoEngine;
    this.eventPublisher = eventPublisher;
  }

  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  public void pollDueTriggers() {
    try {
      for (ChronoTrigger trigger : chronoEngine.due(LocalDateTime.now())) {
        eventPublisher.publishChronoTriggerDue(
            trigger.getId(), trigger.getPatientId(), trigger.getChronoType(),
            trigger.getEvent(), trigger.getAction());
        log.info("时间学触达到期: type={} event={} patientId={}",
            trigger.getChronoType(), trigger.getEvent(), trigger.getPatientId());
      }
    } catch (Exception e) {
      log.warn("时间学到期轮询失败: {}", e.getMessage());
    }
  }
}
