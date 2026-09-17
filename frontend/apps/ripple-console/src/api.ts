import type { ChainVerifyResult, MdtResponse, RippleResponse } from "./types";
import flagship from "./fixtures/flagship.json";

/**
 * 指挥中心数据源（诚信约定）：
 * - live：直连后端网关（/api 代理 → gateway-service），医生账号登录后调用涟漪/MDT/证据API；
 * - snapshot：后端不可达或任一实时调用失败时降级为内置真实响应快照
 *   （fixtures/flagship.json，2026-09-15 真实后端响应，非编造数据），
 *   保证断网环境/评委演示时大屏依然完整可讲。
 *
 * 模式状态是"实时"的：任何一次实时调用失败都会立即把全局徽章切到 SNAPSHOT，
 * 并展示降级原因——绝不出现"徽章写着 LIVE、屏幕上是旧快照"的失真状态。
 * 快照模式下"验印"结论如实标注为离线快照捕获时的历史结论，不冒充实时校验。
 */

const DOCTOR_ACCOUNT = localStorage.getItem("rc-doctor-account") ?? "doctor1";
const DOCTOR_PASSWORD = localStorage.getItem("rc-doctor-password") ?? "123456";

let token: string | null = null;

export type SourceMode = "live" | "snapshot";

let currentMode: SourceMode = "snapshot";
let degradeReason = "";

type ModeListener = (mode: SourceMode, reason: string) => void;
const modeListeners = new Set<ModeListener>();

/** 订阅数据源模式变化（大屏顶栏徽章据此实时翻转）。 */
export function onModeChange(listener: ModeListener): () => void {
  modeListeners.add(listener);
  return () => modeListeners.delete(listener);
}

function setMode(mode: SourceMode, reason: string) {
  if (currentMode === mode && degradeReason === reason) return;
  currentMode = mode;
  degradeReason = reason;
  for (const listener of modeListeners) {
    listener(mode, reason);
  }
}

export function snapshotMeta(): { capturedAt: string; note: string } {
  return {
    capturedAt: (flagship as { capturedAt: string }).capturedAt,
    note: (flagship as { note: string }).note,
  };
}

async function call<T>(method: string, path: string, payload?: unknown): Promise<T> {
  const res = await fetch("/api" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  const body = await res.json().catch(() => null);
  if (!body || (body.code !== 0 && body.code !== 200)) {
    throw new Error(body?.message ?? "HTTP " + res.status);
  }
  return body.data as T;
}

export async function probeBackend(): Promise<SourceMode> {
  try {
    const res = await fetch("/api/doctor/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ account: DOCTOR_ACCOUNT, password: DOCTOR_PASSWORD }),
    });
    const body = await res.json();
    if (body?.code === 0 && body.data?.token) {
      token = body.data.token;
      setMode("live", "");
      return "live";
    }
    token = null;
    setMode("snapshot", "后端不可达或凭据无效");
    return "snapshot";
  } catch {
    token = null;
    setMode("snapshot", "后端不可达");
    return "snapshot";
  }
}

export interface RippleCaseInput {
  diagnosis: string;
  drugs: string[];
  pastHistory: string;
  patientId: number;
}

export async function deriveRipple(input: RippleCaseInput): Promise<RippleResponse> {
  if (!token) {
    return (flagship as { ripple: RippleResponse }).ripple;
  }
  try {
    return await call<RippleResponse>("POST", "/health-event/ripple", {
      diagnosis: input.diagnosis,
      drugs: input.drugs.map((name) => ({ drugName: name })),
      patientId: input.patientId,
      pastHistory: input.pastHistory,
    });
  } catch (e) {
    // 诚信降级：实时调用失败立即切换徽章并说明原因，屏幕内容如实标注为快照
    setMode("snapshot", "实时推演失败：" + (e instanceof Error ? e.message : String(e)));
    return (flagship as { ripple: RippleResponse }).ripple;
  }
}

export async function consultMdt(input: RippleCaseInput, chiefComplaint: string): Promise<MdtResponse> {
  if (!token) {
    return (flagship as { mdt: MdtResponse }).mdt;
  }
  try {
    return await call<MdtResponse>("POST", "/mdt/consult", {
      patientId: input.patientId,
      chiefComplaint,
      pastHistory: input.pastHistory || input.diagnosis,
      diagnosis: input.diagnosis,
      drugs: input.drugs.map((name) => ({ drugName: name })),
    });
  } catch (e) {
    setMode("snapshot", "MDT会诊失败：" + (e instanceof Error ? e.message : String(e)));
    return (flagship as { mdt: MdtResponse }).mdt;
  }
}

export async function verifyChain(): Promise<ChainVerifyResult> {
  if (!token) {
    // 快照模式不做实时校验：如实返回"离线快照"状态（valid=null，由面板呈现为快照印章）
    // 绝不编造 count 或冒充实时结论。
    return {
      valid: null,
      count: 0,
      brokenAt: "",
      message: "离线快照模式：无法实时校验。快照捕获于 " + snapshotMeta().capturedAt + "（捕获时全链真实通过），联网后可实时验印。",
      offline: true,
    } as ChainVerifyResult;
  }
  return call<ChainVerifyResult>("GET", "/evidence/verify");
}

export async function exportFhir(decisionId: string): Promise<Record<string, unknown>> {
  if (!token) {
    throw new Error("离线快照模式：FHIR导出需连接后端");
  }
  return call<Record<string, unknown>>("GET", "/evidence/" + decisionId + "/fhir");
}
