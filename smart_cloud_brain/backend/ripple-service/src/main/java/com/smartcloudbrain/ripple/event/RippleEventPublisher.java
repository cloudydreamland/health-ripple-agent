package com.smartcloudbrain.ripple.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcloudbrain.common.event.DomainEventEnvelope;
import com.smartcloudbrain.common.event.DomainEventNames;
import com.smartcloudbrain.common.event.RabbitTopology;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 涟漪守护领域事件发布器（事件驱动涟漪闭环）。
 *
 * - ripple.derived：推演完成 → 通知服务自动生成医生/患者通知
 * - mdt.consulted：MDT 会诊完成 → 通知服务触达
 * - audit.log：每次涟漪推演/会诊/证据查询的审计事件（安全合规）
 *
 * 发布失败不阻断主流程（try-catch + 日志），保证诊疗链路可用性优先。
 */
@Component
public class RippleEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(RippleEventPublisher.class);
  private static final String SOURCE = "ripple-service";

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public RippleEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
  }

  public void publishRippleDerived(Long rippleEventId, Long patientId, String diagnosis,
                                   int highRiskCount, int totalNodes) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("rippleEventId", rippleEventId);
    payload.put("patientId", patientId == null ? 0 : patientId);
    payload.put("diagnosis", diagnosis);
    payload.put("highRiskCount", highRiskCount);
    payload.put("totalNodes", totalNodes);
    publish(DomainEventNames.RIPPLE_DERIVED, RabbitTopology.ROUTING_RIPPLE_DISPATCH, payload);
  }

  public void publishMdtConsulted(String mdtId, Long patientId, String chiefComplaint, int agentCount) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("mdtId", mdtId);
    payload.put("patientId", patientId == null ? 0 : patientId);
    payload.put("chiefComplaint", chiefComplaint);
    payload.put("agentCount", agentCount);
    publish(DomainEventNames.MDT_CONSULTED, RabbitTopology.ROUTING_NOTIFICATION_DISPATCH, payload);
  }

  /** 时间学触达到期：通知服务生成患者主动触达消息（夜间询问/复查提醒/换季监测）。 */
  public void publishChronoTriggerDue(Long triggerId, Long patientId, String chronoType,
                                       String event, String action) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("triggerId", triggerId);
    payload.put("patientId", patientId == null ? 0 : patientId);
    payload.put("chronoType", chronoType);
    payload.put("event", event);
    payload.put("action", action);
    publish(DomainEventNames.CHRONO_TRIGGER_DUE, RabbitTopology.ROUTING_NOTIFICATION_DISPATCH, payload);
  }

  /** 审计事件：payload 仅含非 PHI 字段（ID/类型/动作），不含自由文本病情。 */
  public void publishAudit(String action, Long patientId, String decisionType, String decisionId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("action", action);
    payload.put("service", SOURCE);
    payload.put("patientId", patientId == null ? 0 : patientId);
    payload.put("decisionType", decisionType);
    payload.put("decisionId", decisionId);
    publish("audit." + action, RabbitTopology.ROUTING_AUDIT_LOG, payload);
  }

  private void publish(String eventType, String routingKey, Object payload) {
    try {
      DomainEventEnvelope envelope = new DomainEventEnvelope(
          UUID.randomUUID().toString(), eventType, Instant.now(), SOURCE, "", payload);
      rabbitTemplate.convertAndSend(RabbitTopology.DOMAIN_EXCHANGE, routingKey,
          objectMapper.writeValueAsString(envelope));
    } catch (Exception e) {
      log.warn("领域事件发布失败 eventType={} : {}", eventType, e.getMessage());
    }
  }
}
