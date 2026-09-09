package com.smartcloudbrain.notification.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcloudbrain.common.event.DomainEventNames;
import com.smartcloudbrain.common.event.RabbitTopology;
import com.smartcloudbrain.notification.dto.NotificationCreateRequest;
import com.smartcloudbrain.notification.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionCheckedConsumer {

  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;

  public PrescriptionCheckedConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
    this.objectMapper = objectMapper;
    this.notificationService = notificationService;
  }

  @RabbitListener(queues = RabbitTopology.NOTIFICATION_DISPATCH_QUEUE)
  public void onMessage(String message) throws Exception {
    Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() {
    });
    String eventType = String.valueOf(envelope.get("eventType"));
    // 健康事件涟漪守护领域事件分发
    if (DomainEventNames.CHRONO_TRIGGER_DUE.equals(eventType)) {
      onChronoTriggerDue(castMap(envelope.get("payload")));
      return;
    }
    if (DomainEventNames.MDT_CONSULTED.equals(eventType)) {
      onMdtConsulted(castMap(envelope.get("payload")));
      return;
    }
    if (!DomainEventNames.PRESCRIPTION_CHECKED.equals(eventType)) {
      return;
    }
    Map<String, Object> payload = castMap(envelope.get("payload"));
    String riskLevel = asString(payload.get("riskLevel"));
    if (!List.of("HIGH", "MEDIUM").contains(riskLevel)) {
      return;
    }
    notificationService.create(new NotificationCreateRequest(
        asLong(payload.get("doctorId")),
        asLong(payload.get("patientId")),
        zeroToNull(asLong(payload.get("prescriptionId"))),
        asString(payload.getOrDefault("type", "PRESCRIPTION_HIGH_RISK")),
        asString(payload.getOrDefault("title", "AI prescription risk alert")),
        asString(payload.get("suggestions")),
        riskLevel
    ));
  }

  /** 时间学触达到期 → 患者主动触达通知（夜间询问/复查提醒/换季监测）。 */
  private void onChronoTriggerDue(Map<String, Object> payload) {
    long patientId = asLong(payload.get("patientId"));
    if (patientId <= 0) {
      return;
    }
    String event = asString(payload.get("event"));
    String action = asString(payload.get("action"));
    String chronoType = asString(payload.get("chronoType"));
    try {
      notificationService.create(new NotificationCreateRequest(
          null,
          patientId,
          null,
          "CHRONO_TRIGGER",
          "健康守护主动提醒：" + event,
          "医疗时间学触达（" + chronoType + "）已到期：" + action,
          "WINDOW".equals(chronoType) ? "HIGH" : "MEDIUM"));
    } catch (Exception e) {
      // 通知失败不阻断主流程
    }
  }

  /** MDT 会诊完成 → 医生会诊纪要通知。 */
  private void onMdtConsulted(Map<String, Object> payload) {
    long patientId = asLong(payload.get("patientId"));
    String mdtId = asString(payload.get("mdtId"));
    String complaint = asString(payload.get("chiefComplaint"));
    try {
      notificationService.create(new NotificationCreateRequest(
          null,
          patientId > 0 ? patientId : null,
          null,
          "MDT_CONSULTATION",
          "多智能体MDT会诊纪要已生成：" + mdtId,
          "疑难病例（主诉：" + complaint + "）五Agent多视角会诊完成，请医生查看会诊纪要。",
          "MEDIUM"));
    } catch (Exception e) {
      // 通知失败不阻断主流程
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  private Long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }

  private Long zeroToNull(Long value) {
    return value == null || value == 0 ? null : value;
  }

  private String asString(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
