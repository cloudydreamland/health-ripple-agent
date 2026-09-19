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

/** 未来72小时涟漪预报（快照模式返回 null——预报必须由真实触达计划计算，不臆造）。 */
export async function fetchForecast(patientId: number): Promise<Record<string, unknown> | null> {
  if (!token) {
    return null;
  }
  try {
    return await call<Record<string, unknown>>("GET", "/health-event/ripple/forecast?patientId=" + patientId);
  } catch (e) {
    setMode("snapshot", "预报获取失败：" + (e instanceof Error ? e.message : String(e)));
    return null;
  }
}

/** 今日守护队列（医生视角跨患者聚合；快照模式返回 null）。 */
export async function fetchGuardQueue(): Promise<Record<string, unknown>[] | null> {
  if (!token) {
    return null;
  }
  try {
    return await call<Record<string, unknown>[]>("GET", "/health-event/guard-queue");
  } catch (e) {
    setMode("snapshot", "守护队列获取失败：" + (e instanceof Error ? e.message : String(e)));
    return null;
  }
}

/** 医生审定守护计划（人机共驾终审）：APPROVE通过 / ADJUST改期 / VETO否决。审定入印鉴链。 */
export async function reviewTrigger(
  triggerId: number,
  decision: "APPROVE" | "ADJUST" | "VETO",
  note = "",
  nextAt?: string,
): Promise<Record<string, unknown> | null> {
  if (!token) {
    throw new Error("离线快照模式：审定需连接后端（审定将入印鉴链，不接受演示写操作）");
  }
  const params = new URLSearchParams({ decision, note });
  if (nextAt) {
    params.set("nextAt", nextAt);
  }
  return call<Record<string, unknown>>("POST", "/chrono/trigger/" + triggerId + "/review?" + params.toString());
}

/** 依从性沙盘：带守护执行度折减的确定性预报重算（同一引擎，可复算）。 */
export async function fetchForecastWithAdherence(patientId: number, adherence: number): Promise<Record<string, unknown> | null> {
  if (!token) {
    return null;
  }
  try {
    return await call<Record<string, unknown>>(
      "GET", "/health-event/ripple/forecast?patientId=" + patientId + "&adherence=" + adherence);
  } catch (e) {
    setMode("snapshot", "沙盘重算失败：" + (e instanceof Error ? e.message : String(e)));
    return null;
  }
}

/** 社区涟漪雷达：跨患者守护信号聚合（个体涟漪汇成社区潮汐；仅医生可读）。 */
export async function fetchCommunityRadar(): Promise<Record<string, unknown> | null> {
  if (!token) {
    return null;
  }
  try {
    return await call<Record<string, unknown>>("GET", "/health-event/community-radar");
  } catch (e) {
    setMode("snapshot", "社区雷达获取失败：" + (e instanceof Error ? e.message : String(e)));
    return null;
  }
}

/** 患者全部证据记录（用于医生审定记录可视化）。 */
export async function fetchPatientEvidence(patientId: number): Promise<Record<string, unknown>[] | null> {
  if (!token) {
    return null;
  }
  try {
    return await call<Record<string, unknown>[]>("GET", "/evidence/patient/" + patientId);
  } catch (e) {
    setMode("snapshot", "审定记录获取失败：" + (e instanceof Error ? e.message : String(e)));
    return null;
  }
}
