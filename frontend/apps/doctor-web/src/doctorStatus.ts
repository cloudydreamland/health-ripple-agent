const labels: Record<string, string> = {
  CREATED: "待接诊", CHECKED_IN: "已签到", CONFIRMED: "已确认", COMPLETED: "已完成",
  CANCELLED: "已取消", PENDING: "待处理", DRAFT: "草稿", DRAFT_READY: "草稿就绪",
  GENERATING: "生成中", IDLE: "待生成", FAILED: "失败", LOW: "低风险",
  MEDIUM: "中风险", HIGH: "高风险", UNREVIEWED: "未审核", MANUAL_REQUIRED: "待人工复核",
  READ: "已读", UNREAD: "未读", INFO: "信息", CLOSED: "已关闭",
  AVAILABLE: "可预约", ENABLED: "已启用", DISABLED: "已停用", PUBLISHED: "已发布",
};

export function doctorStatusText(value: unknown, fallback = "状态待确认") {
  const raw = String(value ?? "").trim();
  if (!raw) return fallback;
  if (/^[\u3400-\u9fff]/u.test(raw)) return raw;
  return labels[raw.toUpperCase()] ?? fallback;
}
