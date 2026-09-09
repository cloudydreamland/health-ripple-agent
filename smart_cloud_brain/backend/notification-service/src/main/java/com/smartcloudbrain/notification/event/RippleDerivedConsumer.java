package com.smartcloudbrain.notification.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcloudbrain.common.event.DomainEventNames;
import com.smartcloudbrain.common.event.RabbitTopology;
import com.smartcloudbrain.notification.dto.NotificationCreateRequest;
import com.smartcloudbrain.notification.service.NotificationService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 涟漪守护事件消费者：ripple.derived → 生成医生/患者通知。
 *
 * 事件驱动涟漪闭环：涟漪推演完成（ripple-service）→ RabbitMQ →
 * 本消费者生成"涟漪守护计划已生成"通知 → WebSocket 实时推送 + 落库。
 */
@Component
public class RippleDerivedConsumer {

  private static final Logger log = LoggerFactory.getLogger(RippleDerivedConsumer.class);

  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;

  public RippleDerivedConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
  }

  @RabbitListener(queues = RabbitTopology.RIPPLE_DISPATCH_QUEUE)
  public void onMessage(String message) throws Exception {
    Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() {
    });
    if (!DomainEventNames.RIPPLE_DERIVED.equals(envelope.get("eventType"))) {
      return;
    }
    Map<String, Object> payload = castMap(envelope.get("payload"));
    long patientId = asLong(payload.get("patientId"));
    int highRiskCount = asInt(payload.get("highRiskCount"));
    String diagnosis = asString(payload.get("diagnosis"));

    String title = highRiskCount > 0
        ? "健康事件涟漪守护计划已生成（" + highRiskCount + "项高风险）"
        : "健康事件涟漪守护计划已生成";
    String content = "患者健康事件「" + diagnosis + "」已生成涟漪守护计划："
        + "药物-生活冲突/复查窗口/并发症信号/家属注意/时间学触达五维推演完成，"
        + "高风险节点 " + highRiskCount + " 个，请医生关注并开展患者教育。";
    try {
      notificationService.create(new NotificationCreateRequest(
          null,
          patientId > 0 ? patientId : null,
          null,
          "RIPPLE_GUARD_PLAN",
          title,
          content,
          highRiskCount >= 2 ? "HIGH" : "MEDIUM"));
      log.info("涟漪守护通知已生成: patientId={} highRisk={}", patientId, highRiskCount);
    } catch (Exception e) {
      log.warn("涟漪守护通知生成失败: {}", e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  private long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (Exception e) {
      return 0L;
    }
  }

  private int asInt(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception e) {
      return 0;
    }
  }

  private String asString(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
