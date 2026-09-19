#!/usr/bin/env node
/**
 * 健康事件涟漪守护 · MCP Server（Model Context Protocol, stdio 传输）
 *
 * 把涟漪守护能力暴露为标准 MCP 工具——任何支持 MCP 的 AI（Claude / DuMate /
 * 自研 Agent）都可以直接调用，不必理解 REST 细节：
 *
 *   - ripple_weather(patientId)                  今日健康气象
 *   - ripple_forecast(patientId, adherence)      72小时预报（可选依从性沙盘）
 *   - ripple_derive(diagnosis, drugs, ...)       涟漪推演（含反事实+护栏+证据入链）
 *   - ripple_guard_queue()                       今日守护队列（医生视角）
 *   - ripple_community_radar()                   社区涟漪雷达（群体信号）
 *   - evidence_verify()                          印鉴链全链校验
 *
 * 安全边界：服务器以**医生身份**对接网关（只读工具为主；ripple_derive 为推演动作，
 * 不开方、不下诊断）。凭据从环境变量读取，绝不硬编码：
 *   RIPPLE_BASE_URL   默认 http://127.0.0.1:18080
 *   DOCTOR_ACCOUNT    默认 doctor1
 *   DOCTOR_PASSWORD   默认 123456（生产环境必须覆盖）
 *
 * 协议：JSON-RPC 2.0 over stdio（MCP 规范的传输方式），逐行收发。
 */
import { createInterface } from "node:readline";

const BASE_URL = process.env.RIPPLE_BASE_URL ?? "http://127.0.0.1:18080";
const DOCTOR_ACCOUNT = process.env.DOCTOR_ACCOUNT ?? "doctor1";
const DOCTOR_PASSWORD = process.env.DOCTOR_PASSWORD ?? "123456";

let token = null;

async function api(method, path, payload) {
  if (!token) {
    const res = await fetch(`${BASE_URL}/api/doctor/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ account: DOCTOR_ACCOUNT, password: DOCTOR_PASSWORD }),
    });
    const body = await res.json();
    if (body?.code !== 0 || !body?.data?.token) {
      throw new Error("网关登录失败: " + JSON.stringify(body));
    }
    token = body.data.token;
  }
  const res = await fetch(BASE_URL + path, {
    method,
    headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  const body = await res.json();
  if (body?.code !== 0 && body?.code !== 200) {
    throw new Error(body?.message ?? "HTTP " + res.status);
  }
  return body.data;
}

const TOOLS = [
  {
    name: "ripple_weather",
    description: "查询患者今日健康气象（晴/多云/大雨/暴雨 + 守护指数 + 家属须知）。主动守护的第一入口。",
    inputSchema: {
      type: "object",
      properties: { patientId: { type: "number", description: "患者ID" } },
      required: ["patientId"],
    },
  },
  {
    name: "ripple_forecast",
    description: "查询患者未来72小时涟漪强度预报（确定性叠加，逐桶含驱动事件）。adherence∈[0,1] 时返回依从性沙盘。",
    inputSchema: {
      type: "object",
      properties: {
        patientId: { type: "number", description: "患者ID" },
        adherence: { type: "number", description: "守护执行度0~1（可选），>0时附带沙盘曲线" },
      },
      required: ["patientId"],
    },
  },
  {
    name: "ripple_derive",
    description: "对新诊断/新处方做健康事件涟漪推演：五维连锁影响+RII强度+反事实路径+护栏审计+印鉴链存证。",
    inputSchema: {
      type: "object",
      properties: {
        diagnosis: { type: "string", description: "诊断（如 2型糖尿病）" },
        drugs: { type: "array", items: { type: "string" }, description: "用药名列表" },
        pastHistory: { type: "string", description: "既往史（逗号分隔）" },
        patientId: { type: "number", description: "患者ID" },
      },
      required: ["diagnosis", "patientId"],
    },
  },
  {
    name: "ripple_guard_queue",
    description: "今日守护队列：跨患者按'升级就医>未缓解>今日到期'排序，回答'今天该先管谁'。",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "ripple_community_radar",
    description: "社区涟漪雷达：跨患者守护信号聚合（个体涟漪汇成社区潮汐），含潮汐指数与TOP信号。",
    inputSchema: { type: "object", properties: {} },
  },
  {
    name: "evidence_verify",
    description: "印鉴链全链校验：逐条重算SHA-256哈希链，篡改任意一条从此处起全部校验失败。",
    inputSchema: { type: "object", properties: {} },
  },
];

async function callTool(name, args) {
  switch (name) {
    case "ripple_weather":
      return api("GET", `/api/health-weather/daily?patientId=${args.patientId}`);
    case "ripple_forecast": {
      const a = typeof args.adherence === "number" ? `&adherence=${args.adherence}` : "";
      return api("GET", `/api/health-event/ripple/forecast?patientId=${args.patientId}${a}`);
    }
    case "ripple_derive":
      return api("POST", "/api/health-event/ripple", {
        diagnosis: args.diagnosis,
        drugs: (args.drugs ?? []).map((drugName) => ({ drugName })),
        pastHistory: args.pastHistory ?? "",
        patientId: args.patientId,
      });
    case "ripple_guard_queue":
      return api("GET", "/api/health-event/guard-queue");
    case "ripple_community_radar":
      return api("GET", "/api/health-event/community-radar");
    case "evidence_verify":
      return api("GET", "/api/evidence/verify");
    default:
      throw new Error("未知工具: " + name);
  }
}

function send(msg) {
  process.stdout.write(JSON.stringify(msg) + "\n");
}

const rl = createInterface({ input: process.stdin });
rl.on("line", async (line) => {
  let msg;
  try {
    msg = JSON.parse(line);
  } catch {
    return;
  }
  const { id, method, params } = msg;
  try {
    if (method === "initialize") {
      send({
        jsonrpc: "2.0", id,
        result: {
          protocolVersion: params?.protocolVersion ?? "2024-11-05",
          capabilities: { tools: {} },
          serverInfo: { name: "health-ripple-agent", version: "1.0.0" },
        },
      });
      return;
    }
    if (method === "tools/list") {
      send({ jsonrpc: "2.0", id, result: { tools: TOOLS } });
      return;
    }
    if (method === "tools/call") {
      const data = await callTool(params.name, params.arguments ?? {});
      send({
        jsonrpc: "2.0", id,
        result: { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] },
      });
      return;
    }
    if (method === "ping") {
      send({ jsonrpc: "2.0", id, result: {} });
      return;
    }
    // 通知类（notifications/initialized 等）无需应答
  } catch (e) {
    if (id !== undefined) {
      send({ jsonrpc: "2.0", id, error: { code: -32000, message: String(e.message ?? e) } });
    }
  }
});
