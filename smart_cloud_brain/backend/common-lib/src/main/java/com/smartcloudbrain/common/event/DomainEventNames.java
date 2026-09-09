package com.smartcloudbrain.common.event;

public final class DomainEventNames {

  public static final String PRESCRIPTION_CHECKED = "prescription.checked";
  public static final String NOTIFICATION_CREATED = "notification.created";

  // 健康事件涟漪守护领域事件（DuMate 参赛核心创新）
  public static final String RIPPLE_DERIVED = "ripple.derived";
  public static final String MDT_CONSULTED = "mdt.consulted";
  public static final String CHRONO_TRIGGER_DUE = "chrono.trigger.due";

  private DomainEventNames() {
  }
}
