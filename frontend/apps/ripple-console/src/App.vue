<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { deriveRipple, consultMdt, probeBackend, onModeChange, snapshotMeta, fetchForecast, fetchGuardQueue, fetchForecastWithAdherence, fetchCommunityRadar, fetchPatientEvidence, type SourceMode } from "./api";
import { AGENT_META, type RippleNode, type RippleResponse, type MdtResponse } from "./types";
import RiiSummary from "./components/RiiSummary.vue";
import RidgePlot from "./components/RidgePlot.vue";
import RipplePond from "./components/RipplePond.vue";
import OrganMap from "./components/OrganMap.vue";
import ActivityLog, { type LogEvent } from "./components/ActivityLog.vue";
import CounterfactualLattice from "./components/CounterfactualLattice.vue";
import ChronoTimeline from "./components/ChronoTimeline.vue";
import ChronoDial from "./components/ChronoDial.vue";
import EvidencePanel from "./components/EvidencePanel.vue";
import MdtChord from "./components/MdtChord.vue";
import CommunityRadar, { type RadarData } from "./components/CommunityRadar.vue";
import PatientOverviewCard from "./components/PatientOverviewCard.vue";
import AIInsightsPanel from "./components/AIInsightsPanel.vue";
import CarePathway from "./components/CarePathway.vue";
import NodeDetailDrawer from "./components/NodeDetailDrawer.vue";
import ForecastChart, { type ForecastData } from "./components/ForecastChart.vue";
import GuardQueuePanel, { type QueueRow } from "./components/GuardQueuePanel.vue";

interface CasePreset {
  id: string;
  label: string;
  diagnosis: string;
  drugs: string[];
  pastHistory: string;
  chiefComplaint: string;
  mdtReady: boolean;
}

const CASES: CasePreset[] = [
  { id: "flagship", label: "旗舰：2型糖尿病 + 二甲双胍（高血压既往）", diagnosis: "2型糖尿病", drugs: ["二甲双胍"], pastHistory: "高血压", chiefComplaint: "多饮多尿3个月伴胸闷", mdtReady: false },
  { id: "cad", label: "冠心病（心梗黄金窗口 WINDOW）", diagnosis: "冠心病", drugs: [], pastHistory: "2型糖尿病,高血压", chiefComplaint: "胸闷气短3天", mdtReady: true },
  { id: "af", label: "房颤华法林抗凝（出血/血栓双向风险）", diagnosis: "房颤（口服华法林抗凝）", drugs: ["华法林"], pastHistory: "", chiefComplaint: "心悸1周", mdtReady: false },
  { id: "asthma", label: "哮喘（夜间节律 + 花粉季）", diagnosis: "哮喘", drugs: [], pastHistory: "", chiefComplaint: "夜间喘息发作", mdtReady: false },
  { id: "multi", label: "多病共存：糖尿病+高血压+慢性肾病", diagnosis: "2型糖尿病", drugs: ["二甲双胍"], pastHistory: "高血压,慢性肾病", chiefComplaint: "胸闷气短3天", mdtReady: true },
];

const mode = ref<SourceMode>("snapshot");
const modeReason = ref("");
const caseId = ref("flagship");
const patientId = ref(Number(localStorage.getItem("rc-patient-id") ?? 1) || 1);
const customDiagnosis = ref("");
const customDrugs = ref("");
const customHistory = ref("");
const running = ref(false);
const latencyMs = ref<number | null>(null);
const ripple = ref<RippleResponse | null>(null);
const mdt = ref<MdtResponse | null>(null);
const mdtLoading = ref(false);
const selectedNode = ref<RippleNode | null>(null);
const clock = ref("");
const forecast = ref<ForecastData | null>(null);
const guardQueue = ref<QueueRow[]>([]);
const sandbox = ref<SandboxData | null>(null);
const adherence = ref(0);
const extraEvents = ref<LogEvent[]>([]);
const radar = ref<RadarData | null>(null);
const hasReviewAny = computed(() => (ripple.value?.chronoTriggers ?? []).some((t) => t.reviewStatus));
const activeTriggerCount = computed(() => (ripple.value?.chronoTriggers ?? []).filter((t) => t.status === "ACTIVE").length);
const resolvedTriggerCount = computed(() => (ripple.value?.chronoTriggers ?? []).filter((t) => t.feedbackStatus === "RESOLVED").length);
const reviewRecords = ref<{ decisionId: string; timestamp: string; inputs: Record<string, unknown>; agentId: string }[]>([]);

/** 直播 ticker：最新 6 条事件串成实况条（活动日志的镜像，广播感） */
const tickerText = computed(() => {
  const events = [...extraEvents.value, ...activityEvents.value].slice(0, 6);
  if (!events.length) {
    return "等待落石 · 点击右上「落石 · 推演涟漪」开始 ";
  }
  return events.map((e) => `${e.time} ${e.code}｜${e.text}`).join("  ◆  ");
});

interface SandboxData {
  adherence: number;
  buckets: { hourOffset: number; intensity: number }[];
  peakHourOffset: number;
  peakIntensity: number;
  horizonAvg: number;
}

// 数据源模式实时联动：任何一次实时调用失败，api 层立即把徽章切到 SNAPSHOT 并说明原因
// （绝不出现"徽章 LIVE、屏幕是旧快照"的失真状态）
const unwatchMode = onModeChange((m, reason) => {
  mode.value = m;
  modeReason.value = reason;
});
onBeforeUnmount(unwatchMode);

function persistPatientId() {
  localStorage.setItem("rc-patient-id", String(patientId.value || 1));
}

const timer = window.setInterval(() => {
  clock.value = new Date().toTimeString().slice(0, 8);
}, 1000);
onBeforeUnmount(() => window.clearInterval(timer));

const currentCase = computed(() => CASES.find((c) => c.id === caseId.value) ?? CASES[0]);
const intensity = computed(() => ripple.value?.rippleIntensity ?? ripple.value?.summary?.rippleIntensity ?? null);
const dimensions = computed(() => ripple.value?.dimensions ?? {
  drugLifestyleConflicts: [], recheckWindows: [], complicationSignals: [],
  familyAttentions: [], chronoTriggers: [],
});
const flaggedCount = computed(
  () => ripple.value?.counterfactualTree?.alternativePaths.filter((p) => p.guardrailVerdict === "FLAGGED").length ?? 0,
);

async function run(caseOverride?: CasePreset) {
  const preset = caseOverride ?? currentCase.value;
  running.value = true;
  mdt.value = null;
  selectedNode.value = null;
  extraEvents.value = [];
  sandbox.value = null;
  adherence.value = 0;
  const started = performance.now();
  const diagnosis = preset.id === "custom" ? customDiagnosis.value.trim() : preset.diagnosis;
  const drugs = preset.id === "custom"
    ? customDrugs.value.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    : preset.drugs;
  const pastHistory = preset.id === "custom" ? customHistory.value.trim() : preset.pastHistory;
  persistPatientId();
  try {
    ripple.value = await deriveRipple({ diagnosis, drugs, pastHistory, patientId: patientId.value });
    latencyMs.value = mode.value === "live" ? Math.round(performance.now() - started) : null;
  } finally {
    running.value = false;
  }
  // 预报与守护队列跟随推演刷新（live 模式；快照模式保持诚实空态）
  const f = await fetchForecast(patientId.value);
  forecast.value = (f as ForecastData) ?? null;
  guardQueue.value = (await fetchGuardQueue()) as QueueRow[] | null ?? [];
  radar.value = (await fetchCommunityRadar()) as RadarData | null;
  await loadReviewRecords();
}

/** 医生审定记录（印鉴链在案的 GUARD_PLAN_REVIEW，按患者维度可视化"谁改了守护计划"）。 */
async function loadReviewRecords() {
  const all = (await fetchPatientEvidence(patientId.value)) ?? [];
  reviewRecords.value = all
    .filter((e) => e.decisionType === "GUARD_PLAN_REVIEW")
    .map((e) => ({
      decisionId: String(e.decisionId ?? ""),
      timestamp: String(e.timestamp ?? ""),
      inputs: (e.inputs ?? {}) as Record<string, unknown>,
      agentId: String(e.agentId ?? ""),
    }));
}

/** 医生审定完成：本地状态即时上丁 + 刷新预报（被否决的触达从预报/天气消失）。 */
async function onReviewed(info: { triggerId: number; decision: string; reviewStatus: string; event: string; note: string }) {
  const t = ripple.value?.chronoTriggers?.find((x) => x.triggerId === info.triggerId);
  if (t) {
    t.reviewStatus = info.reviewStatus;
    t.reviewer = "当前医生";
  }
  const verdict = info.decision === "APPROVE" ? "通过" : info.decision === "ADJUST" ? "改期" : "否决";
  extraEvents.value = [{
    time: new Date().toTimeString().slice(0, 8),
    code: "审定",
    color: "#e5c15c",
    text: `医生${verdict}守护计划「${info.event}」${info.note ? " · " + info.note : ""} · 已入印鉴链`,
  }, ...extraEvents.value];
  await loadSandbox(adherence.value);
  radar.value = (await fetchCommunityRadar()) as RadarData | null;
  await loadReviewRecords();
}

/** 依从性沙盘：拖动滑杆即时重算（服务端同一引擎确定性重算，可复算）。 */
async function loadSandbox(a: number) {
  adherence.value = a;
  if (a <= 0) {
    sandbox.value = null;
    await refreshForecast();
    return;
  }
  const f = await fetchForecastWithAdherence(patientId.value, a);
  if (f) {
    forecast.value = f as ForecastData;
    sandbox.value = ((f as Record<string, unknown>).sandbox as SandboxData) ?? null;
  }
}

async function refreshForecast() {
  const f = await fetchForecast(patientId.value);
  forecast.value = (f as ForecastData) ?? null;
}

/** 守护报告：一键生成可打印的诊后守护摘要（基层落地最后一公里——打印随病历交给患者）。
 *  全部取自当前推演/预报/审定/印鉴链的真实数据，不新增任何臆造内容。 */
function generateReport() {
  const r = ripple.value;
  if (!r) {
    return;
  }
  const esc = (s: unknown) => String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const ri = intensity.value;
  const he = r.healthEvent;
  const plans = (r.chronoTriggers ?? []).map((t) => {
    const review = t.reviewStatus === "APPROVED" ? "医生已通过"
      : t.reviewStatus === "ADJUSTED" ? "医生已改期"
      : t.reviewStatus === "VETOED" ? "医生已否决" : "待医生审定";
    return `<tr>
      <td>${esc(t.chronoType === "WINDOW" ? "窗口期" : t.chronoType === "RHYTHM" ? "节律" : t.chronoType === "PERIODIC" ? "周期" : t.chronoType === "SEASONAL" ? "季节" : t.chronoType)}</td>
      <td><b>${esc(t.event)}</b><br><span class="muted">${esc(t.action)}</span></td>
      <td>${esc(t.triggerTime)}</td>
      <td>${esc((t as { timingCard?: { evidenceBasis?: string } }).timingCard?.evidenceBasis ?? "—")}</td>
      <td><span class="pill">${esc(review)}</span></td>
    </tr>`;
  }).join("");
  const risks = (ri?.topRisks ?? []).map((risk, i) =>
    `<tr><td>#${i + 1}</td><td>${esc(risk.label)}</td><td>${esc(risk.ringName)}（R${risk.ring}）</td><td class="num">${risk.intensity.toFixed(1)}</td></tr>`
  ).join("");
  const ev = r.evidenceChain;
  const flagged = r.counterfactualTree?.guardrailSummary;
  const html = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8" />
<title>守护报告 · 患者${patientId.value} · ${esc(he.diagnosis)}</title>
<style>
  body { font-family: "Noto Serif SC","Source Han Serif SC","SimSun",serif; color: #26231b; margin: 36px auto; max-width: 780px; padding: 0 24px; line-height: 1.7; }
  h1 { font-size: 22px; letter-spacing: 2px; border-bottom: 2.5px solid #26231b; padding-bottom: 10px; }
  h2 { font-size: 15px; margin: 22px 0 8px; }
  .seal { display: inline-block; background: #bd4033; color: #dbe9f9; padding: 4px 12px; border-radius: 6px; transform: rotate(-2deg); font-size: 13px; }
  .meta { color: #55503f; font-size: 12.5px; margin: 6px 0 0; }
  table { width: 100%; border-collapse: collapse; font-size: 12.5px; margin-top: 6px; }
  th, td { border: 1px solid #cdc5a9; padding: 6px 9px; text-align: left; vertical-align: top; }
  th { background: #f2eee1; font-size: 11.5px; letter-spacing: 1px; }
  .num { text-align: right; font-variant-numeric: tabular-nums; }
  .muted { color: #8f8a75; font-size: 11px; }
  .pill { border: 1px solid #cdc5a9; border-radius: 999px; padding: 1px 9px; font-size: 11px; }
  .box { border: 1.5px solid #bd4033; border-radius: 8px; padding: 10px 14px; font-size: 13px; margin: 8px 0; }
  .rii { font-size: 34px; font-weight: 800; color: #bd4033; }
  .foot { margin-top: 26px; border-top: 1px dashed #cdc5a9; padding-top: 10px; color: #8f8a75; font-size: 11px; }
  .hash { font-family: Consolas, monospace; font-size: 10.5px; word-break: break-all; }
  @media print { body { margin: 10mm auto; } }
</style></head><body>
<h1><span class="seal">守</span> 诊后守护报告 <span style="font-size:12px;color:#8f8a75;letter-spacing:1px;">GUARDIAN REPORT</span></h1>
<p class="meta">患者 PID ${patientId.value} · 诊断「${esc(he.diagnosis)}」${he.drugs.length ? " · 用药 " + esc(he.drugs.join("/")) : ""}${he.pastHistory ? " · 既往史 " + esc(he.pastHistory) : ""}<br/>生成时间 ${new Date().toLocaleString("zh-CN")} · 数据来源：${mode.value === "live" ? "实时后端推演" : "内置真实快照（" + esc(snapshotMeta().capturedAt) + "捕获）"}</p>

<h2>一、涟漪强度总览</h2>
<div class="box"><span class="rii">${ri ? ri.index.toFixed(1) : "—"}</span>
  <b>${esc(ri?.levelLabel ?? "等待推演")}</b> · 有效扩散半径 ${ri?.radius ?? 0}/5 环<br/>
  <span class="muted">RII = 100 × 严重度 × 紧迫度 × 可干预度 × e^(−0.22×(环数−1))，事件指数取 Top5 节点均值，逐项可复算</span></div>

<h2>二、TOP 风险节点</h2>
<table><tr><th>#</th><th>风险</th><th>所在环</th><th>强度</th></tr>${risks || '<tr><td colspan="4">本次事件无优先处置节点</td></tr>'}</table>

<h2>三、守护计划与医生审定</h2>
<table><tr><th>类型</th><th>守护事项 / 行动</th><th>触达时机</th><th>循证依据</th><th>审定</th></tr>${plans || '<tr><td colspan="5">无</td></tr>'}</table>

<h2>四、安全与证据</h2>
<p>反事实路径 ${r.counterfactualTree?.counterfactualCount ?? 0} 条，护栏锁定高危 <b>${flagged?.flaggedPaths ?? 0}</b> 条（禁止下发）。
${ev ? `本报告对应决策已入印鉴链：<br/><span class="hash">DECISION ${esc(ev.decisionId)}<br/>HASH ${esc(ev.hash)}</span>` : ""}</p>
<p class="foot">本报告由健康事件涟漪守护智能体生成，仅用于诊后守护参考；智能体不开方、不下诊断、不改治疗方案——终审权在医生。证据链可验印/导出 HL7 FHIR R4 Provenance。</p>
<script>window.onload = function () { window.print(); }<\/script>
</body></html>`;
  const win = window.open("", "_blank", "width=860,height=980");
  if (!win) {
    window.alert("浏览器拦截了报告窗口，请允许弹窗后重试。");
    return;
  }
  win.document.write(html);
  win.document.close();
}

async function runMdt() {
  mdtLoading.value = true;
  persistPatientId();
  try {
    mdt.value = await consultMdt(
      { diagnosis: currentCase.value.diagnosis, drugs: currentCase.value.drugs, pastHistory: currentCase.value.pastHistory, patientId: patientId.value },
      currentCase.value.chiefComplaint,
    );
  } finally {
    mdtLoading.value = false;
  }
}

/** 守护活动日志：一次推演的完整事件流（倒序呈现）。
 * 时间轴诚信约定：live 模式用真实墙钟（now - 事件在流中的实际间隔仅作展示近似，
 * 首条为推演完成时刻）；snapshot 模式以快照捕获时刻为基准回放——时间不是编造的"现在"。 */
const activityEvents = computed<LogEvent[]>(() => {
  const r = ripple.value;
  if (!r) return [];
  const evts: LogEvent[] = [];
  const isSnapshot = mode.value !== "live";
  const captured = new Date(snapshotMeta().capturedAt).getTime();
  const base = Number.isFinite(captured) && isSnapshot ? captured : Date.now();
  let offset = 0;
  const at = () => new Date(base - offset++ * 900).toTimeString().slice(0, 8);

  const he = r.healthEvent;
  evts.push({ time: at(), code: "事件", color: "#5ba7f7", text: `健康事件接收：${he.diagnosis}${he.drugs.length ? " · 用药 " + he.drugs.join("/") : ""}${he.pastHistory ? " · 既往史 " + he.pastHistory : ""}` });
  if (r.proactiveAssessment?.isProactive) {
    evts.push({ time: at(), code: "主动", color: "#4fd1a5", text: `主动守护评估：${r.proactiveAssessment.proactiveAction}` });
  }
  const ri = intensity.value;
  evts.push({ time: at(), code: "强度", color: "#f4695c", text: `涟漪推演完成：${r.summary.totalNodes} 节点 / 五环${ri ? ` · RII=${ri.index}（${ri.levelLabel}）· 半径 ${ri.radius}/5 环` : ""}` });
  const gs = r.counterfactualTree?.guardrailSummary;
  if (gs) {
    evts.push({ time: at(), code: "护栏", color: "#f4695c", text: `护栏审计：${gs.auditedPaths} 条反事实路径，FLAGGED ${gs.flaggedPaths} 条已锁定禁止下发` });
  }
  if (r.chronoTriggers?.length) {
    const byType: Record<string, string> = { WINDOW: "#f4695c", RHYTHM: "#9d8cff", PERIODIC: "#38cfe8", SEASONAL: "#4fd1a5" };
    for (const t of r.chronoTriggers) {
      evts.push({ time: at(), code: t.chronoType, color: byType[t.chronoType] ?? "#7e93ab", text: `触达注册：${t.event}（${t.triggerTime} · ${t.action}）` });
    }
  }
  const ev = r.evidenceChain;
  if (ev) {
    evts.push({ time: at(), code: "存证", color: "#4fd1a5", text: `决策入印鉴链：${ev.decisionId} · SHA-256 链式存证` });
  }
  if (r.degraded) {
    evts.push({ time: at(), code: "降级", color: "#f0a45c", text: r.degradedReason ?? "结构化降级：内置知识库兜底" });
  }
  if (mdt.value) {
    evts.push({ time: at(), code: "会诊", color: "#9d8cff", text: `五Agent会诊收敛：${mdt.value.consensusNotes?.length ?? 0} 条共识要点入纪要` });
  }
  return [...extraEvents.value, ...evts];
});

onMounted(async () => {
  clock.value = new Date().toTimeString().slice(0, 8);
  mode.value = await probeBackend();
  await run();
});
</script>

<template>
  <div class="console">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="brand">
        <div class="brand-seal">守</div>
        <div>
          <h1>涟漪守护指挥中心<span class="en">INKP RIPPLE GUARDIAN</span></h1>
          <p>夜航墨 · 水墨涟漪 —— 健康事件涟漪守护智能体</p>
        </div>
      </div>
      <div class="topbar-spacer" />
      <div class="clock">{{ clock }}<small>LOCAL / 24H</small></div>
      <span class="live-pill" :class="{ demo: mode !== 'live' }"
            :title="mode === 'live' ? '实时连接后端网关' : '快照模式：' + (modeReason || '后端不可达')">
        <span class="dot" />{{ mode === "live" ? "LIVE" : "SNAPSHOT" }}
      </span>
      <label class="pid-box" title="患者ID（越权校验随患者角色生效）">
        PID <input v-model.number="patientId" type="number" min="1" @change="run()" />
      </label>
      <select v-model="caseId" @change="run()">
        <option v-for="c in CASES" :key="c.id" :value="c.id">{{ c.label }}</option>
      </select>
      <button class="ghost big" :disabled="!ripple" @click="generateReport()">守护报告</button>
      <button class="big" :disabled="running" @click="run()">{{ running ? "推演中 …" : "落石 · 推演涟漪" }}</button>
    </header>

    <!-- 直播 ticker：守护实况（滚动，hover 暂停） -->
    <div class="ticker" aria-hidden="true">
      <div class="ticker-track">
        <span class="ticker-group"><b>LIVE 守护实况</b><span class="sep">◆</span>{{ tickerText }}<span class="sep">◆</span></span>
        <span class="ticker-group"><b>LIVE 守护实况</b><span class="sep">◆</span>{{ tickerText }}<span class="sep">◆</span></span>
      </div>
    </div>

    <!-- 快照模式诚实横幅：屏幕内容是何时捕获的真实响应，一眼可查 -->
    <div v-if="mode !== 'live'" class="panel snapshot-banner">
      <div class="panel-body snapshot-banner-body">
        <b>SNAPSHOT 模式</b>
        <span>
          当前展示 {{ snapshotMeta().capturedAt }} 捕获的真实后端响应（{{ snapshotMeta().note }}）。
          原因：{{ modeReason || "后端不可达" }}。连接后端后自动恢复实时推演。
        </span>
      </div>
    </div>

    <!-- 自定义病例输入 -->
    <div v-if="caseId === 'custom'" class="panel">
      <div class="panel-body custom-bar">
        <input v-model="customDiagnosis" type="text" placeholder="诊断（如 2型糖尿病）" />
        <input v-model="customDrugs" type="text" placeholder="用药（逗号分隔，如 二甲双胍,华法林）" />
        <input v-model="customHistory" type="text" placeholder="既往史（逗号分隔）" />
        <button :disabled="running" @click="run()">推演</button>
      </div>
    </div>

    <!-- 指标长条：一块墨板五格数据（巨型数字分级，RII 为英雄数字） -->
    <div class="stat-strip corner-ticks">
      <div class="stat-cell accent-red hero">
        <span class="label"><span>RII 涟漪强度指数</span><span class="idx">壹</span></span>
        <span class="value" :style="{ color: intensity?.level === 'RED' ? 'var(--red)' : intensity?.level === 'ORANGE' ? 'var(--orange)' : 'var(--yellow)' }">
          {{ intensity ? intensity.index.toFixed(1) : "—" }}
        </span>
        <span class="sub">{{ intensity?.levelLabel ?? "等待推演" }}</span>
      </div>
      <div class="stat-cell">
        <span class="label"><span>涟漪节点 / NODES</span><span class="idx">贰</span></span>
        <span class="value">{{ ripple?.summary?.totalNodes ?? "—" }}</span>
        <span class="sub">高风险 <b class="down">{{ ripple?.summary?.highRiskCount ?? 0 }}</b> · 五环全展开</span>
      </div>
      <div class="stat-cell">
        <span class="label"><span>反事实路径 / COUNTERFACTUAL</span><span class="idx">叁</span></span>
        <span class="value">{{ ripple?.counterfactualTree?.counterfactualCount ?? "—" }}</span>
        <span class="sub">FLAGGED 锁定 <b class="down">{{ flaggedCount }}</b> · 禁止下发</span>
      </div>
      <div class="stat-cell accent-green">
        <span class="label"><span>有效扩散半径 / RADIUS</span><span class="idx">肆</span></span>
        <span class="value">{{ intensity?.radius ?? 0 }}<small> / 5 环</small></span>
        <div class="seg-meter">
          <i v-for="n in 5" :key="n" :class="{ on: n <= (intensity?.radius ?? 0) }" />
        </div>
      </div>
      <div class="stat-cell">
        <span class="label"><span>推演耗时 / LATENCY</span><span class="idx">伍</span></span>
        <span class="value">{{ latencyMs == null ? "—" : latencyMs }}<small> ms</small></span>
        <span class="sub">{{ mode === "live" ? "在线推演 · 后端网关" : "演示快照 · 断网兜底" }}</span>
      </div>
    </div>

    <!-- 英雄三栏：左患者概览+AI洞察 ｜ 中·涟漪池主视觉 ｜ 右·RII+活动日志 -->
    <div class="hero-row">
      <div class="hero-left">
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--blue)" />
            <h2>患者概览</h2>
            <span class="spacer" />
            <span class="fig">CASE</span>
          </header>
          <div class="panel-body">
            <PatientOverviewCard
              :patient-id="patientId"
              :health-event="ripple?.healthEvent ?? null"
              :mode="mode"
              @run="run()"
            />
          </div>
        </section>
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--cyan)" />
            <h2>AI 洞察</h2>
            <span class="spacer" />
            <span class="fig">INSIGHTS</span>
          </header>
          <div class="panel-body">
            <AIInsightsPanel
              :proactive="ripple?.proactiveAssessment ?? null"
              :top-risks="intensity?.topRisks ?? []"
              :guardrail="ripple?.counterfactualTree?.guardrailSummary ?? null"
              :forecast-peak="forecast?.peak ?? null"
            />
          </div>
        </section>
      </div>

      <section class="panel corner-ticks pond-hero">
        <header class="sec-head">
          <span class="dot" style="background: var(--violet)" />
          <h2>器官涟漪图 · 人体映射</h2>
          <span class="en">ORGAN RIPPLE MAP · LIVE</span>
          <span class="spacer" />
          <span v-if="ripple?.proactiveAssessment" class="tag GREEN">{{ ripple.proactiveAssessment.proactiveAction }}</span>
          <span class="fig">FIG.01</span>
        </header>
        <div class="panel-body pond-body">
          <OrganMap
            :dimensions="dimensions"
            :health-event="ripple?.healthEvent ?? { diagnosis: '', drugs: [], pastHistory: '' }"
            :intensity="intensity"
            :selected="selectedNode"
            @select="selectedNode = $event"
          />
        </div>
      </section>

      <div class="hero-right">
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--red)" />
            <h2>涟漪强度</h2>
            <span class="spacer" />
            <span class="fig">FIG.02</span>
          </header>
          <div class="panel-body">
            <RiiSummary :intensity="intensity" />
          </div>
        </section>
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--green)" />
            <h2>守护活动日志</h2>
            <span class="spacer" />
            <span class="fig">{{ String(activityEvents.length).padStart(3, "0") }}</span>
          </header>
          <div class="panel-body">
            <ActivityLog :events="activityEvents" />
          </div>
        </section>
      </div>
    </div>

    <!-- 护理路径 -->
    <div class="grid-path">
      <section class="panel">
        <div class="panel-body">
          <CarePathway
            :has-ripple="!!ripple"
            :has-guardrail="!!ripple?.counterfactualTree?.guardrailSummary"
            :has-evidence="!!ripple?.evidenceChain"
            :has-review="hasReviewAny"
            :active-triggers="activeTriggerCount"
            :resolved-count="resolvedTriggerCount"
          />
        </div>
      </section>
    </div>

    <!-- 主视觉：RII 山脊剖面（技术图纸角标） -->
    <div class="grid-hero">
      <section class="panel corner-ticks">
        <header class="sec-head">
          <span class="dot" style="background: var(--red)" />
          <h2>涟漪强度山脊剖面</h2>
          <span class="en">RII RIDGE PROFILE / RING DECAY e^(&minus;0.22·(r&minus;1))</span>
          <span class="spacer" />
          <span class="fig">FIG.02</span>
        </header>
        <div class="panel-body ridge-layout">
          <RidgePlot :dimensions="dimensions" :intensity="intensity" />
        </div>
      </section>
    </div>

    <!-- 活水涟漪池（五维图谱，降级为全景视图） -->
    <div class="grid-path">
      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--violet)" />
          <h2>活水涟漪池 · 五维图谱</h2>
          <span class="en">RIPPLE POND / {{ ripple?.healthEvent?.diagnosis ?? "&mdash;" }}</span>
          <span class="spacer" />
          <span class="fig">FIG.03</span>
        </header>
        <div class="panel-body">
          <RipplePond
            :dimensions="dimensions"
            :health-event="ripple?.healthEvent ?? { diagnosis: '', drugs: [], pastHistory: '' }"
            :intensity="intensity"
            :selected="selectedNode"
            @select="selectedNode = $event"
          />
        </div>
      </section>
    </div>
    <!-- 反事实晶格 + MDT弦图 -->
    <div class="grid-b">
      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--red)" />
          <h2>反事实决策晶格 × 护栏</h2>
          <span class="en">COUNTERFACTUAL LATTICE / GUARDRAIL GATE</span>
          <span class="spacer" />
          <span class="fig">FIG.04</span>
        </header>
        <div class="panel-body">
          <CounterfactualLattice :tree="ripple?.counterfactualTree ?? null" />
        </div>
      </section>

      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--blue)" />
          <h2>五Agent MDT 会诊弦图</h2>
          <span class="en">MDT CHORD / 5 AGENTS · 10 EDGES</span>
          <span class="spacer" />
          <button class="ghost" :disabled="mdtLoading" @click="runMdt()">
            {{ mdtLoading ? "会诊中 …" : "发起会诊" }}
          </button>
          <span class="fig">FIG.05</span>
        </header>
        <div class="panel-body">
          <MdtChord :mdt="mdt" />
        </div>
      </section>
    </div>

    <!-- 时辰守护盘 + 时间学列表 -->
    <div class="grid-c">
      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--violet)" />
          <h2>医疗时间学 · 时辰守护盘</h2>
          <span class="en">CHRONO / WINDOW · RHYTHM · PERIODIC · SEASONAL</span>
          <span class="spacer" />
          <span class="fig">TIMING CARDS ×{{ ripple?.chronoTriggers?.length ?? 0 }}</span>
        </header>
        <div class="panel-body chrono-layout">
          <ChronoDial :triggers="ripple?.chronoTriggers ?? []" />
          <div class="chrono-col">
            <ChronoTimeline :triggers="ripple?.chronoTriggers ?? []" :live="mode === 'live'" @reviewed="onReviewed" />
            <!-- 医生审定记录：谁在何时以什么理由改了守护计划（印鉴链在案） -->
            <div v-if="reviewRecords.length" class="review-log">
              <p class="mono review-title">DOCTOR REVIEW · 医生审定记录（印鉴链在案 {{ reviewRecords.length }} 条）</p>
              <div v-for="rec in [...reviewRecords].reverse().slice(0, 4)" :key="rec.decisionId" class="review-line">
                <span class="mono rv-time">{{ rec.timestamp.replace("T", " ").slice(5, 16) }}</span>
                <span class="rv-text">{{ rec.inputs.event }} —
                  <b :class="rec.inputs.decision === 'VETO' ? 'rv-v' : rec.inputs.decision === 'APPROVE' ? 'rv-a' : 'rv-m'">{{ rec.inputs.decision === "VETO" ? "否决" : rec.inputs.decision === "APPROVE" ? "通过" : "改期" }}</b>
                  · {{ rec.inputs.note || "无备注" }}（{{ rec.agentId }}）</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--red)" />
          <h2>朱砂印鉴链 · FHIR</h2>
          <span class="en">EVIDENCE SEAL CHAIN / SHA-256 · HL7 FHIR R4</span>
          <span class="spacer" />
          <span class="fig">APPEND-ONLY</span>
        </header>
        <div class="panel-body">
          <EvidencePanel :evidence="ripple?.evidenceChain ?? null" />
        </div>
      </section>
    </div>

    <!-- 72小时预报 + 今日守护队列 -->
    <div class="grid-d">
      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--red)" />
          <h2>未来72小时涟漪预报 · 依从性沙盘</h2>
          <span class="en">RIPPLE FORECAST / DETERMINISTIC SUPERPOSITION</span>
          <span class="spacer" />
          <span v-if="forecast" class="tag RED">峰 +{{ forecast.peak.hourOffset }}h · {{ forecast.peak.intensity }}</span>
          <span class="fig">FIG.06</span>
        </header>
        <div class="panel-body">
          <ForecastChart :forecast="forecast" :sandbox="sandbox" @adherence="loadSandbox($event)" />
        </div>
      </section>

      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--orange, #ffab4a)" />
          <h2>今日守护队列</h2>
          <span class="en">GUARD QUEUE / WHO TO GUARD FIRST</span>
          <span class="spacer" />
          <span class="fig">FIG.07</span>
        </header>
        <div class="panel-body">
          <GuardQueuePanel :rows="guardQueue" />
        </div>
      </section>
    </div>

    <!-- 社区涟漪雷达：个体涟漪汇成社区潮汐 -->
    <div class="grid-e">
      <section class="panel corner-ticks">
        <header class="sec-head">
          <span class="dot" style="background: var(--gold)" />
          <h2>社区涟漪雷达</h2>
          <span class="en">COMMUNITY RIPPLE RADAR / CROSS-PATIENT SIGNALS · 7D</span>
          <span class="spacer" />
          <span v-if="radar" class="tag" :class="radar.tideLevel === 'HIGH' ? 'RED' : radar.tideLevel === 'MID' ? 'ORANGE' : 'YELLOW'">潮汐 {{ radar.tideIndex }} · {{ radar.tideLevel }}</span>
          <span class="fig">FIG.08</span>
        </header>
        <div class="panel-body">
          <CommunityRadar :radar="radar" />
        </div>
      </section>
    </div>

    <!-- 五Agent角色卡条 -->
    <div class="agent-strip">
      <div
        v-for="(meta, key) in AGENT_META"
        :key="key"
        class="agent-card"
        :style="{ borderColor: mdt ? meta.color : 'var(--line-strong)' }"
      >
        <div class="agent-avatar" :style="{ borderColor: meta.color, color: mdt ? '#dbe9f9' : meta.color, background: mdt ? meta.color : 'var(--card-inset)' }">{{ meta.char }}</div>
        <div class="agent-info">
          <b>{{ meta.label }}</b>
          <span class="mono">{{ meta.en }} VIEW</span>
          <span class="agent-status">
            <i :style="{ background: mdt ? meta.color : 'var(--faint)' }" />
            {{ mdt ? "已发言 · 纪要已收敛" : "待命 · STANDBY" }}
          </span>
        </div>
      </div>
    </div>

    <NodeDetailDrawer :node="selectedNode" @close="selectedNode = null" />
  </div>
</template>

<style scoped>
.custom-bar { display: flex; gap: 10px; flex-wrap: wrap; }
.custom-bar input { flex: 1; min-width: 200px; }

.snapshot-banner { margin: 0; border-color: rgba(229, 157, 60, 0.55); }
.snapshot-banner-body { display: flex; gap: 12px; align-items: baseline; font-size: 12px; color: var(--orange); }
.snapshot-banner-body b { font-family: var(--font-serif); letter-spacing: 2px; flex: none; }

.pid-box {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 11px; color: var(--muted); letter-spacing: 1px;
}
.pid-box input {
  width: 64px; padding: 7px 9px;
  border: 1px solid var(--line-strong); border-radius: 8px;
  background: var(--card); color: var(--ink);
  font-family: var(--font-mono); font-size: 12px;
}

.hero-row { display: grid; grid-template-columns: 300px 1fr 320px; gap: 14px; align-items: stretch; }
@media (max-width: 1500px) { .hero-row { grid-template-columns: 280px 1fr; }
  .hero-right { grid-column: 1 / -1; display: grid; grid-template-columns: 1fr 1fr; gap: 14px; } }
@media (max-width: 1100px) { .hero-row { grid-template-columns: 1fr; } .hero-right { grid-template-columns: 1fr; } }
.hero-left, .hero-right { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.hero-right .panel-body { max-height: 320px; overflow-y: auto; }
.pond-hero { position: relative; }
.pond-hero::before {
  content: "";
  position: absolute;
  left: 50%; top: 56%;
  width: 560px; height: 560px;
  transform: translate(-50%, -50%);
  background: conic-gradient(from 0deg,
    rgba(56, 191, 248, 0.07), transparent 25%,
    rgba(157, 140, 255, 0.06) 40%, transparent 60%,
    rgba(79, 209, 165, 0.05) 78%, transparent 90%);
  border-radius: 50%;
  filter: blur(6px);
  pointer-events: none;
  animation: pond-spin 36s linear infinite;
}
@keyframes pond-spin { to { transform: translate(-50%, -50%) rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .pond-hero::before { animation: none; } }
.pond-hero .pond-body { position: relative; display: flex; justify-content: center; }
.grid-path { display: grid; grid-template-columns: 1fr; gap: 14px; }
.ridge-layout { display: grid; grid-template-columns: 1fr; gap: 18px; }
@media (max-width: 1180px) { .ridge-layout { grid-template-columns: 1fr; } }

.chrono-layout { display: grid; grid-template-columns: 300px 1fr; gap: 16px; align-items: start; }
@media (max-width: 1400px) { .chrono-layout { grid-template-columns: 1fr; } }

.chrono-col { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.review-log { border: 1px dashed var(--gold); border-radius: 9px; padding: 9px 12px; background: var(--gold-bg); }
.review-title { margin: 0 0 6px; font-size: 9.5px; letter-spacing: 1.6px; color: var(--gold); font-weight: 700; }
.review-line { display: flex; gap: 10px; align-items: baseline; padding: 3px 0; font-size: 11.5px; color: var(--ink-soft); line-height: 1.5; }
.rv-time { color: var(--faint); flex: none; font-size: 10px; }
.rv-text b { font-family: var(--font-mono); }
.rv-text .rv-v { color: var(--red); }
.rv-text .rv-a { color: var(--green); }
.rv-text .rv-m { color: var(--yellow); }

.grid-e { display: grid; grid-template-columns: 1fr; gap: 14px; }

.agent-card {
  border: 1.5px solid var(--line-strong);
  border-radius: 10px;
  background: var(--card);
  box-shadow: var(--shadow-card);
  padding: 11px 12px;
  display: flex;
  gap: 10px;
  align-items: center;
  transition: border-color 0.25s ease;
}
.agent-avatar {
  width: 40px; height: 40px; flex: none;
  border: 1.5px solid; border-radius: 8px;
  display: grid; place-items: center;
  font-family: var(--font-serif);
  font-size: 19px; font-weight: 800;
  transition: all 0.25s ease;
}
.agent-info { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.agent-info b { font-size: 13px; }
.agent-info .mono { font-size: 9.5px; color: var(--faint); letter-spacing: 1px; }
.agent-status { display: inline-flex; align-items: center; gap: 5px; font-size: 10.5px; color: var(--muted); }
.agent-status i { width: 6px; height: 6px; border-radius: 50%; }
</style>
