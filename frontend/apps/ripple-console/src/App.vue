<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { deriveRipple, consultMdt, probeBackend, type SourceMode } from "./api";
import type { RippleNode, RippleResponse, MdtResponse } from "./types";
import RiiGauge from "./components/RiiGauge.vue";
import RippleGraph from "./components/RippleGraph.vue";
import CounterfactualTree from "./components/CounterfactualTree.vue";
import ChronoTimeline from "./components/ChronoTimeline.vue";
import EvidencePanel from "./components/EvidencePanel.vue";
import MdtPanel from "./components/MdtPanel.vue";
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

const currentCase = computed(() => CASES.find((c) => c.id === caseId.value) ?? CASES[0]);
const intensity = computed(() => ripple.value?.rippleIntensity ?? ripple.value?.summary?.rippleIntensity ?? null);
const dimensions = computed(() => ripple.value?.dimensions ?? {
  drugLifestyleConflicts: [], recheckWindows: [], complicationSignals: [],
  familyAttentions: [], chronoTriggers: [],
});

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

onMounted(async () => {
  mode.value = await probeBackend();
  await run();
});
</script>

<template>
  <div class="console">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="brand">
        <div class="brand-mark">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="2.4" fill="#3fd8f2" />
            <circle cx="12" cy="12" r="6" stroke="#3fd8f2" stroke-opacity="0.55" stroke-width="1.2" fill="none" />
            <circle cx="12" cy="12" r="9.6" stroke="#3fd8f2" stroke-opacity="0.25" stroke-width="1" fill="none" />
          </svg>
        </div>
        <div>
          <h1>涟漪守护指挥中心</h1>
          <p>健康事件涟漪守护智能体 · Ripple Console</p>
        </div>
      </div>
      <div class="topbar-spacer" />
      <span class="conn" :class="mode">
        <span class="dot" />
        {{ mode === "live" ? "后端已连接 · 在线推演" : "演示模式 · 真实数据快照" }}
      </span>
      <select v-model="caseId" @change="run()">
        <option v-for="c in CASES" :key="c.id" :value="c.id">{{ c.label }}</option>
      </select>
      <button class="big" :disabled="running" @click="run()">{{ running ? "推演中…" : "▶ 推演涟漪" }}</button>
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

    <!-- 主区：RII总览 + 涟漪图谱 -->
    <div class="grid-main">
      <section class="panel">
        <header class="panel-header">
          <div class="panel-title">
            <h2>涟漪强度总览</h2>
            <p>Ripple Intensity Index · 事件级量化</p>
          </div>
        </header>
        <div class="panel-body">
          <RiiGauge :intensity="intensity" />
          <hr class="divider" />
          <div class="stats">
            <div class="stat"><span>涟漪节点</span><b>{{ ripple?.summary?.totalNodes ?? 0 }}</b></div>
            <div class="stat"><span>高风险节点</span><b class="danger">{{ ripple?.summary?.highRiskCount ?? 0 }}</b></div>
            <div class="stat"><span>反事实路径</span><b>{{ ripple?.counterfactualTree?.counterfactualCount ?? 0 }}</b></div>
            <div class="stat"><span>推演耗时</span><b>{{ latencyMs == null ? "—" : latencyMs + "ms" }}</b></div>
          </div>
          <div v-if="ripple?.degraded" class="degraded">⚠ {{ ripple.degradedReason }}</div>
        </div>
      </section>

      <section class="panel">
        <header class="panel-header">
          <div class="panel-title">
            <h2>五维涟漪图谱</h2>
            <p>{{ ripple?.healthEvent?.diagnosis }}<template v-if="ripple?.healthEvent?.drugs?.length"> · 用药：{{ ripple?.healthEvent?.drugs.join("、") }}</template><template v-if="ripple?.healthEvent?.pastHistory"> · 既往：{{ ripple?.healthEvent?.pastHistory }}</template></p>
          </div>
          <div class="spacer" />
          <span v-if="ripple?.proactiveAssessment" class="tag CYAN">{{ ripple.proactiveAssessment.proactiveAction }}</span>
        </header>
        <div class="panel-body">
          <RippleGraph
            :dimensions="dimensions"
            :health-event="ripple?.healthEvent ?? { diagnosis: '', drugs: [], pastHistory: '' }"
            :intensity="intensity"
            :selected="selectedNode"
            @select="selectedNode = $event"
          />
        </div>
      </section>
    </div>

    <!-- 副区：反事实树 + 时间学 -->
    <div class="grid-sub">
      <section class="panel">
        <header class="panel-header">
          <div class="panel-title">
            <h2>反事实决策树 × 护栏审计</h2>
            <p>记录"如果选了别的会怎样"，高危路径锁定</p>
          </div>
        </header>
        <div class="panel-body">
          <CounterfactualTree :tree="ripple?.counterfactualTree ?? null" />
        </div>
      </section>

      <section class="panel">
        <header class="panel-header">
          <div class="panel-title">
            <h2>医疗时间学触达</h2>
            <p>窗口期 / 节律 / 周期 / 季节 · Timing Card 循证卡片</p>
          </div>
        </header>
        <div class="panel-body">
          <ChronoTimeline :triggers="ripple?.chronoTriggers ?? []" />
        </div>
      </section>
    </div>

    <!-- 底区：证据链 + MDT -->
    <div class="grid-bottom">
      <section class="panel">
        <header class="panel-header">
          <div class="panel-title">
            <h2>哈希链证据 · FHIR 导出</h2>
            <p>决策级防篡改存证 · HL7 FHIR R4 Provenance</p>
          </div>
        </header>
        <div class="panel-body">
          <EvidencePanel :evidence="ripple?.evidenceChain ?? null" />
        </div>
      </section>

      <section class="panel">
        <header class="panel-header">
          <div class="panel-title">
            <h2>五Agent MDT会诊</h2>
            <p>分诊/处方/病历/随访/涟漪守护 · 学科化辩论收敛</p>
          </div>
          <div class="spacer" />
          <button class="ghost" :disabled="mdtLoading" @click="runMdt()">
            {{ mdtLoading ? "会诊中…" : "发起MDT会诊" }}
          </button>
        </header>
        <div class="panel-body">
          <MdtPanel :mdt="mdt" />
        </div>
      </section>
    </div>

    <NodeDetailDrawer :node="selectedNode" @close="selectedNode = null" />
  </div>
</template>

<style scoped>
.stats { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.stat { border: 1px solid var(--line); border-radius: 10px; padding: 8px 12px; background: var(--bg-inset); display: flex; flex-direction: column; gap: 2px; }
.stat span { font-size: 11px; color: var(--text-faint); }
.stat b { font-size: 19px; font-family: var(--font-mono); }
.stat .danger { color: var(--red); }
.degraded { margin-top: 10px; font-size: 12px; color: var(--orange); border: 1px dashed rgba(255,171,74,0.4); border-radius: 8px; padding: 7px 10px; }
.custom-bar { display: flex; gap: 10px; flex-wrap: wrap; }
.custom-bar input { flex: 1; min-width: 200px; }
</style>
