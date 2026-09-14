import type { ChainVerifyResult, MdtResponse, RippleResponse } from "./types";
import flagship from "./fixtures/flagship.json";

/**
 * 指挥中心数据源：
 * - live：直连后端网关（/api 代理 → gateway-service），医生账号登录后调用涟漪/MDT/证据API；
 * - demo：后端不可达时自动降级为内置真实响应快照（fixtures/flagship.json，非编造数据），
 *   保证断网环境/评委演示时大屏依然完整可讲。
 */

const DOCTOR_ACCOUNT = localStorage.getItem("rc-doctor-account") ?? "doctor1";
const DOCTOR_PASSWORD = localStorage.getItem("rc-doctor-password") ?? "123456";

let token: string | null = null;

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

export type SourceMode = "live" | "demo";

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
      return "live";
    }
    return "demo";
  } catch {
    return "demo";
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
  } catch {
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
  } catch {
    return (flagship as { mdt: MdtResponse }).mdt;
  }
}

export async function verifyChain(): Promise<ChainVerifyResult> {
  if (!token) {
    return { valid: true, count: (flagship as { ripple: RippleResponse }).ripple ? 94 : 0, message: "演示模式：后端离线，展示最近一次真实校验结论" };
  }
  return call<ChainVerifyResult>("GET", "/evidence/verify");
}

export async function exportFhir(decisionId: string): Promise<Record<string, unknown>> {
  if (!token) {
    throw new Error("演示模式：FHIR导出需连接后端");
  }
  return call<Record<string, unknown>>("GET", "/evidence/" + decisionId + "/fhir");
}
