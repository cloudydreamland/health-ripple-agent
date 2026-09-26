import { statusText } from "@smart-cloud-brain/shared-api";

export function formatPatientDate(value: unknown): string {
  const raw = String(value ?? "").trim();
  const timestamp = Date.parse(raw);
  if (!raw || !Number.isFinite(timestamp)) return raw || "-";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(timestamp);
}

export function patientStatusText(value: unknown, fallback = "状态待确认"): string {
  const raw = String(value ?? "").trim();
  if (!raw) return fallback;
  const patientLabels: Record<string, string> = {
    CHECKED_IN: "已签到",
    ASSIGNED: "已分配医生",
    ACTIVE: "有效",
    FINISHED: "已完成",
  };
  const upper = raw.toUpperCase();
  if (patientLabels[upper]) return patientLabels[upper];
  const translated = statusText(raw, fallback);
  return translated === raw && /^[A-Z][A-Z0-9_]*$/i.test(raw) ? fallback : translated;
}
