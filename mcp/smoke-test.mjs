#!/usr/bin/env node
/**
 * MCP Server 冒烟测试：spawn 服务器子进程，走完整 JSON-RPC 握手 → 工具列表 → 实际调用。
 * 需要后端网关在线（默认 http://127.0.0.1:18080）。
 */
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const serverPath = join(dirname(fileURLToPath(import.meta.url)), "ripple-mcp-server.mjs");
const child = spawn(process.execPath, [serverPath], { stdio: ["pipe", "pipe", "pipe"] });

let nextId = 1;
const pending = new Map();

// 修正逐行解析：用 readline 按行收 JSON-RPC 应答
import { createInterface } from "node:readline";
const rl = createInterface({ input: child.stdout });
rl.on("line", (line) => {
  try {
    const msg = JSON.parse(line);
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg);
      pending.delete(msg.id);
    }
  } catch { /* 忽略非JSON行 */ }
});
child.stderr.on("data", (d) => process.stderr.write(d));

function rpc(method, params) {
  const id = nextId++;
  return new Promise((resolve, reject) => {
    pending.set(id, (msg) => {
      if (msg.error) {
        reject(new Error(msg.error.message));
      } else {
        resolve(msg.result);
      }
    });
    child.stdin.write(JSON.stringify({ jsonrpc: "2.0", id, method, params }) + "\n");
    setTimeout(() => {
      if (pending.has(id)) {
        pending.delete(id);
        reject(new Error("timeout: " + method));
      }
    }, 15000);
  });
}

let failed = 0;
function check(name, ok, detail = "") {
  console.log(`[${ok ? "PASS" : "FAIL"}] ${name}${detail ? " — " + detail : ""}`);
  if (!ok) failed++;
}

// 1. initialize 握手
const init = await rpc("initialize", { protocolVersion: "2024-11-05", capabilities: {}, clientInfo: { name: "smoke", version: "0" } });
check("MCP initialize 握手", init?.serverInfo?.name === "health-ripple-agent", init?.serverInfo?.name);

// 2. 工具列表
const tools = await rpc("tools/list", {});
const names = tools?.tools?.map((t) => t.name) ?? [];
check("tools/list 暴露6个涟漪工具",
  ["ripple_weather", "ripple_forecast", "ripple_derive", "ripple_guard_queue", "ripple_community_radar", "evidence_verify"]
    .every((n) => names.includes(n)), names.join(","));

// 3. 实际调用：今日健康气象
const weather = await rpc("tools/call", { name: "ripple_weather", arguments: { patientId: 1 } });
const weatherData = JSON.parse(weather.content[0].text);
check("ripple_weather 实调", ["SUNNY", "CLOUDY", "RAIN", "STORM"].includes(weatherData.weather),
  `${weatherData.weather}(${weatherData.index})`);

// 4. 实际调用：72h预报 + 沙盘
const forecast = await rpc("tools/call", { name: "ripple_forecast", arguments: { patientId: 1, adherence: 0.5 } });
const forecastData = JSON.parse(forecast.content[0].text);
check("ripple_forecast 实调（含沙盘）",
  Number(forecastData.horizonHours) === 72 && !!forecastData.sandbox,
  `均值${forecastData.horizonAvg} · 沙盘均值${forecastData.sandbox?.horizonAvg}`);

// 5. 实际调用：社区雷达
const radar = await rpc("tools/call", { name: "ripple_community_radar", arguments: {} });
const radarData = JSON.parse(radar.content[0].text);
check("ripple_community_radar 实调", typeof radarData.tideIndex === "number",
  `潮汐${radarData.tideIndex}(${radarData.tideLevel}) · ${radarData.patientsMonitored}名在管患者`);

// 6. 实际调用：印鉴链校验
const verify = await rpc("tools/call", { name: "evidence_verify", arguments: {} });
const verifyData = JSON.parse(verify.content[0].text);
check("evidence_verify 实调", verifyData.valid === true, `全链${verifyData.count}条校验通过`);

child.stdin.end();
child.kill();
console.log(failed === 0 ? "\nMCP冒烟 6/6 全部通过" : `\nMCP冒烟 ${6 - failed}/6 通过`);
process.exit(failed === 0 ? 0 : 1);
