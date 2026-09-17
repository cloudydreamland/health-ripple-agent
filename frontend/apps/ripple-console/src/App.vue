<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { deriveRipple, consultMdt, probeBackend, type SourceMode } from "./api";
import { AGENT_META, type RippleNode, type RippleResponse, type MdtResponse } from "./types";
import RiiSummary from "./components/RiiSummary.vue";
import RidgePlot from "./components/RidgePlot.vue";
import RipplePond from "./components/RipplePond.vue";
import ActivityLog, { type LogEvent } from "./components/ActivityLog.vue";
import CounterfactualLattice from "./components/CounterfactualLattice.vue";
import ChronoTimeline from "./components/ChronoTimeline.vue";
import ChronoDial from "./components/ChronoDial.vue";
import EvidencePanel from "./components/EvidencePanel.vue";
import MdtChord from "./components/MdtChord.vue";
import NodeDetailDrawer from "./components/NodeDetailDrawer.vue";

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

const mode = ref<SourceMode>("demo");
const caseId = ref("flagship");
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
  const started = performance.now();
  const diagnosis = preset.id === "custom" ? customDiagnosis.value.trim() : preset.diagnosis;
  const drugs = preset.id === "custom"
    ? customDrugs.value.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    : preset.drugs;
  const pastHistory = preset.id === "custom" ? customHistory.value.trim() : preset.pastHistory;
  try {
    ripple.value = await deriveRipple({ diagnosis, drugs, pastHistory, patientId: 1 });
    latencyMs.value = Math.round(performance.now() - started);
  } finally {
    running.value = false;
  }
}

async function runMdt() {
  mdtLoading.value = true;
  try {
    mdt.value = await consultMdt(
      { diagnosis: currentCase.value.diagnosis, drugs: currentCase.value.drugs, pastHistory: currentCase.value.pastHistory, patientId: 1 },
      currentCase.value.chiefComplaint,
    );
  } finally {
    mdtLoading.value = false;
  }
}

/** 守护活动日志：一次推演的完整事件流（倒序呈现）。 */
const activityEvents = computed<LogEvent[]>(() => {
  const r = ripple.value;
  if (!r) return [];
  const evts: LogEvent[] = [];
  const now = Date.now();
  let offset = 0;
  const at = () => new Date(now - offset++ * 900).toTimeString().slice(0, 8);

  const he = r.healthEvent;
  evts.push({ time: at(), code: "事件", color: "#33628f", text: `健康事件接收：${he.diagnosis}${he.drugs.length ? " · 用药 " + he.drugs.join("/") : ""}${he.pastHistory ? " · 既往史 " + he.pastHistory : ""}` });
  if (r.proactiveAssessment?.isProactive) {
    evts.push({ time: at(), code: "主动", color: "#41795f", text: `主动守护评估：${r.proactiveAssessment.proactiveAction}` });
  }
  const ri = intensity.value;
  evts.push({ time: at(), code: "强度", color: "#bd4033", text: `涟漪推演完成：${r.summary.totalNodes} 节点 / 五环${ri ? ` · RII=${ri.index}（${ri.levelLabel}）· 半径 ${ri.radius}/5 环` : ""}` });
  const gs = r.counterfactualTree?.guardrailSummary;
  if (gs) {
    evts.push({ time: at(), code: "护栏", color: "#bd4033", text: `护栏审计：${gs.auditedPaths} 条反事实路径，FLAGGED ${gs.flaggedPaths} 条已锁定禁止下发` });
  }
  if (r.chronoTriggers?.length) {
    const byType: Record<string, string> = { WINDOW: "#bd4033", RHYTHM: "#7a63a8", PERIODIC: "#37808a", SEASONAL: "#41795f" };
    for (const t of r.chronoTriggers) {
      evts.push({ time: at(), code: t.chronoType, color: byType[t.chronoType] ?? "#8f8a75", text: `触达注册：${t.event}（${t.triggerTime} · ${t.action}）` });
    }
  }
  const ev = r.evidenceChain;
  if (ev) {
    evts.push({ time: at(), code: "存证", color: "#41795f", text: `决策入印鉴链：${ev.decisionId} · SHA-256 链式存证` });
  }
  if (r.degraded) {
    evts.push({ time: at(), code: "降级", color: "#c07a1d", text: r.degradedReason ?? "结构化降级：内置知识库兜底" });
  }
  if (mdt.value) {
    evts.push({ time: at(), code: "会诊", color: "#7a63a8", text: `五Agent会诊收敛：${mdt.value.consensusNotes?.length ?? 0} 条共识要点入纪要` });
  }
  return evts;
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
          <p>数字宣纸 · 水墨涟漪 —— 健康事件涟漪守护智能体</p>
        </div>
      </div>
      <div class="topbar-spacer" />
      <div class="clock">{{ clock }}<small>LOCAL / 24H</small></div>
      <span class="live-pill" :class="{ demo: mode !== 'live' }">
        <span class="dot" />{{ mode === "live" ? "LIVE" : "DEMO" }}
      </span>
      <select v-model="caseId" @change="run()">
        <option v-for="c in CASES" :key="c.id" :value="c.id">{{ c.label }}</option>
      </select>
      <button class="big" :disabled="running" @click="run()">{{ running ? "推演中 …" : "落石 · 推演涟漪" }}</button>
    </header>

    <!-- 自定义病例输入 -->
    <div v-if="caseId === 'custom'" class="panel">
      <div class="panel-body custom-bar">
        <input v-model="customDiagnosis" type="text" placeholder="诊断（如 2型糖尿病）" />
        <input v-model="customDrugs" type="text" placeholder="用药（逗号分隔，如 二甲双胍,华法林）" />
        <input v-model="customHistory" type="text" placeholder="既往史（逗号分隔）" />
        <button :disabled="running" @click="run()">推演</button>
      </div>
    </div>

    <!-- 指标条 -->
    <div class="stat-strip">
      <div class="stat-card accent-red">
        <span class="label"><span>RII 涟漪强度指数</span><span class="idx">壹</span></span>
        <span class="value" :style="{ color: intensity?.level === 'RED' ? 'var(--red)' : intensity?.level === 'ORANGE' ? 'var(--orange)' : 'var(--yellow)' }">
          {{ intensity ? intensity.index.toFixed(1) : "—" }}
        </span>
        <span class="sub">{{ intensity?.levelLabel ?? "等待推演" }}</span>
      </div>
      <div class="stat-card">
        <span class="label"><span>涟漪节点 / NODES</span><span class="idx">贰</span></span>
        <span class="value">{{ ripple?.summary?.totalNodes ?? "—" }}</span>
        <span class="sub">高风险 <b class="down">{{ ripple?.summary?.highRiskCount ?? 0 }}</b> · 五环全展开</span>
      </div>
      <div class="stat-card">
        <span class="label"><span>反事实路径 / COUNTERFACTUAL</span><span class="idx">叁</span></span>
        <span class="value">{{ ripple?.counterfactualTree?.counterfactualCount ?? "—" }}</span>
        <span class="sub">FLAGGED 锁定 <b class="down">{{ flaggedCount }}</b> · 禁止下发</span>
      </div>
      <div class="stat-card accent-green">
        <span class="label"><span>有效扩散半径 / RADIUS</span><span class="idx">肆</span></span>
        <span class="value">{{ intensity?.radius ?? 0 }}<small> / 5 环</small></span>
        <div class="seg-meter">
          <i v-for="n in 5" :key="n" :class="{ on: n <= (intensity?.radius ?? 0) }" />
        </div>
      </div>
      <div class="stat-card">
        <span class="label"><span>推演耗时 / LATENCY</span><span class="idx">伍</span></span>
        <span class="value">{{ latencyMs == null ? "—" : latencyMs }}<small> ms</small></span>
        <span class="sub">{{ mode === "live" ? "在线推演 · 后端网关" : "演示快照 · 断网兜底" }}</span>
      </div>
    </div>

    <!-- 主视觉：RII 山脊剖面 -->
    <div class="grid-hero">
      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--red)" />
          <h2>涟漪强度山脊剖面</h2>
          <span class="en">RII RIDGE PROFILE / RING DECAY e^(−0.22·(r−1))</span>
          <span class="spacer" />
          <span class="fig">FIG.01</span>
        </header>
        <div class="panel-body ridge-layout">
          <RiiSummary :intensity="intensity" />
          <RidgePlot :dimensions="dimensions" :intensity="intensity" />
        </div>
      </section>
    </div>

    <!-- 活水涟漪池 + 活动日志 -->
    <div class="grid-a">
      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--violet)" />
          <h2>活水涟漪池 · 五维图谱</h2>
          <span class="en">RIPPLE POND / {{ ripple?.healthEvent?.diagnosis ?? "—" }}</span>
          <span class="spacer" />
          <span v-if="ripple?.proactiveAssessment" class="tag GREEN">{{ ripple.proactiveAssessment.proactiveAction }}</span>
          <span class="fig">FIG.02</span>
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

      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--green)" />
          <h2>守护活动日志</h2>
          <span class="en">ACTIVITY LOG</span>
          <span class="spacer" />
          <span class="fig">{{ String(activityEvents.length).padStart(3, "0") }} EVENTS</span>
        </header>
        <div class="panel-body">
          <ActivityLog :events="activityEvents" />
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
          <span class="fig">FIG.03</span>
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
          <span class="fig">FIG.04</span>
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
          <ChronoTimeline :triggers="ripple?.chronoTriggers ?? []" />
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

    <!-- 五Agent角色卡条 -->
    <div class="agent-strip">
      <div
        v-for="(meta, key) in AGENT_META"
        :key="key"
        class="agent-card"
        :style="{ borderColor: mdt ? meta.color : 'var(--line-strong)' }"
      >
        <div class="agent-avatar" :style="{ borderColor: meta.color, color: mdt ? '#f6f3e8' : meta.color, background: mdt ? meta.color : 'var(--card-inset)' }">{{ meta.char }}</div>
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

.ridge-layout { display: grid; grid-template-columns: 250px 1fr; gap: 18px; }
@media (max-width: 1180px) { .ridge-layout { grid-template-columns: 1fr; } }

.chrono-layout { display: grid; grid-template-columns: 300px 1fr; gap: 16px; align-items: start; }
@media (max-width: 1400px) { .chrono-layout { grid-template-columns: 1fr; } }

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
