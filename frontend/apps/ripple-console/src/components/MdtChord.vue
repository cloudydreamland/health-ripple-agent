<script setup lang="ts">
import { computed } from "vue";
import { AGENT_META, type MdtResponse } from "../types";

/**
 * MDT 会诊弦图：五个学科 Agent 均布圆周，
 * 十条弦 = 两两辩论交换；会诊完成后中心显示共识数。
 */
const props = defineProps<{ mdt: MdtResponse | null }>();

const CX = 168;
const CY = 170;
const R = 112;

const AGENT_KEYS = ["triageView", "prescriptionView", "recordView", "followupView", "rippleView"] as const;

const agents = computed(() =>
  AGENT_KEYS.map((key, i) => {
    const meta = AGENT_META[key];
    const start = i * 72 + 7;
    const end = i * 72 + 65;
    return { key, meta, start, end, mid: i * 72 + 36 };
  }),
);

function polar(r: number, angleDeg: number): { x: number; y: number } {
  const a = ((angleDeg - 90) * Math.PI) / 180;
  return { x: CX + r * Math.cos(a), y: CY + r * Math.sin(a) };
}

function arcPath(start: number, end: number): string {
  const s = polar(R, end);
  const e = polar(R, start);
  return `M ${s.x.toFixed(1)} ${s.y.toFixed(1)} A ${R} ${R} 0 0 0 ${e.x.toFixed(1)} ${e.y.toFixed(1)}`;
}

const chords = computed(() => {
  const out: Array<{ d: string; key: string }> = [];
  for (let i = 0; i < agents.value.length; i++) {
    for (let j = i + 1; j < agents.value.length; j++) {
      const a = polar(R - 4, agents.value[i].mid);
      const b = polar(R - 4, agents.value[j].mid);
      out.push({ d: `M ${a.x.toFixed(1)} ${a.y.toFixed(1)} Q ${CX} ${CY} ${b.x.toFixed(1)} ${b.y.toFixed(1)}`, key: `${i}-${j}` });
    }
  }
  return out;
});

const agentDots = computed(() =>
  agents.value.map((a) => {
    const p = polar(R + 20, a.mid);
    const lp = polar(R + 42, a.mid);
    const anchor = Math.abs(Math.cos(((a.mid - 90) * Math.PI) / 180)) < 0.35 ? "middle" : Math.cos(((a.mid - 90) * Math.PI) / 180) > 0 ? "start" : "end";
    return { ...a, x: p.x, y: p.y, lx: lp.x, ly: lp.y + 3, anchor };
  }),
);

const views = computed(() => {
  if (!props.mdt?.consultation) return [];
  return Object.entries(props.mdt.consultation).map(([key, value]) => ({
    key,
    meta: AGENT_META[key] ?? { label: key, en: key, char: "?", color: "#7e93ab" },
    value,
  }));
});

const consensusNotes = computed(() => props.mdt?.consensusNotes ?? []);
</script>

<template>
  <div class="mdt-chord">
    <div class="chord-side">
      <svg viewBox="0 0 336 340" class="chord-svg">
        <!-- 弦（辩论交换） -->
        <path v-for="c in chords" :key="c.key" :d="c.d" class="chord" :class="{ active: !!mdt }" />
        <!-- 弧 -->
        <g v-for="a in agents" :key="a.key">
          <path :d="arcPath(a.start, a.end)" fill="none" :stroke="a.meta.color" stroke-width="7" stroke-linecap="round" :stroke-opacity="mdt ? 0.95 : 0.4" />
        </g>
        <!-- Agent 圆点 + 标签 -->
        <g v-for="a in agentDots" :key="'d' + a.key">
          <circle :cx="a.x" :cy="a.y" r="12" fill="#101f33" :stroke="a.meta.color" stroke-width="2" />
          <text :x="a.x" :y="a.y + 4" text-anchor="middle" class="agent-char" :fill="a.meta.color">{{ a.meta.char }}</text>
          <text :x="a.lx" :y="a.ly" :text-anchor="a.anchor" class="agent-label" :fill="mdt ? a.meta.color : '#7e93ab'">{{ a.meta.label.replace(" Agent", "") }}</text>
        </g>
        <!-- 中心 -->
        <text :x="CX" :y="CY - 6" text-anchor="middle" class="center-num">{{ mdt ? (mdt.consensusNotes?.length ?? 0) : "—" }}</text>
        <text :x="CX" :y="CY + 12" text-anchor="middle" class="center-cap">{{ mdt ? "CONSENSUS" : "STANDBY" }}</text>
        <text :x="CX" :y="CY + 30" text-anchor="middle" class="center-sub">5 AGENTS · 10 EDGES</text>
      </svg>
      <div class="chord-stats mono">
        <div><span>AGENTS</span><b>5 / 5</b></div>
        <div><span>EDGES</span><b>10</b></div>
        <div><span>CONSENSUS</span><b>{{ mdt?.consensusNotes?.length ?? 0 }}</b></div>
        <div><span>BOUNDARY</span><b class="green">ADVISORY</b></div>
      </div>
    </div>

    <div class="mdt-detail">
      <template v-if="mdt">
        <p class="list-title mono">// 会诊共识纪要 · CONSULTATION NOTES</p>
        <div class="note-row" v-for="(note, i) in consensusNotes" :key="i">
          <span class="note-idx mono">{{ String(i + 1).padStart(2, "0") }}</span>
          <span class="note-text">{{ note }}</span>
        </div>
        <div class="agent-views">
          <div v-for="view in views" :key="view.key" class="view-item" :style="{ borderColor: view.meta.color }">
            <b :style="{ color: view.meta.color }">{{ view.meta.char }} {{ view.meta.label }}</b>
            <p v-for="(value, key) in view.value" :key="key" class="view-line">
              <span class="k">{{ key }}：</span>{{ value }}
            </p>
          </div>
        </div>
        <p class="safety mono">safetyBoundary: advisoryOnly=true · 终审权在医生 · 智能体不做处方/诊断终审</p>
      </template>
      <p v-else class="hint standby-hint">
        五个学科 Agent 已就位（分诊 / 处方 / 病历 / 随访 / 涟漪守护）。<br />
        点击右上「发起会诊」，观察五 Agent 以专科身份辩论并收敛为会诊纪要。
      </p>
    </div>
  </div>
</template>

<style scoped>
.mdt-chord { display: grid; grid-template-columns: 320px 1fr; gap: 16px; }
@media (max-width: 1400px) { .mdt-chord { grid-template-columns: 1fr; } }
.chord-side { display: flex; flex-direction: column; gap: 8px; align-items: center; }
.chord-svg { width: 100%; max-width: 330px; display: block; }
.chord { fill: none; stroke: rgba(200, 224, 250, 0.09); stroke-width: 1.1; }
.chord.active { stroke: rgba(200, 224, 250, 0.17); }
.agent-char { font-size: 11px; font-weight: 800; font-family: var(--font-sans); }
.agent-label { font-size: 10.5px; font-weight: 600; }
.center-num { font-size: 30px; font-weight: 700; font-family: var(--font-mono); fill: #dbe9f9; }
.center-cap { font-size: 9px; letter-spacing: 2px; fill: #7e93ab; font-family: var(--font-mono); }
.center-sub { font-size: 8.5px; letter-spacing: 1.4px; fill: #5a708c; font-family: var(--font-mono); }
.chord-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 18px; width: 100%; max-width: 300px; font-size: 10px; letter-spacing: 1px; color: #7e93ab; }
.chord-stats div { display: flex; justify-content: space-between; border-bottom: 1px dashed var(--line); padding: 3px 0; }
.chord-stats b { color: #dbe9f9; font-size: 12px; }
.chord-stats .green { color: var(--green); }

.mdt-detail { min-width: 0; display: flex; flex-direction: column; gap: 7px; max-height: 460px; overflow-y: auto; padding-right: 4px; }
.list-title { margin: 0; font-size: 10px; color: var(--faint); letter-spacing: 1px; }
.note-row { display: flex; gap: 10px; align-items: baseline; border-bottom: 1px dashed var(--line); padding: 4px 2px; }
.note-idx { color: var(--faint); font-size: 10px; flex: none; }
.note-text { font-size: 12.5px; line-height: 1.55; color: var(--ink); }
.agent-views { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 8px; margin-top: 6px; }
.view-item { border: 1px solid; border-left-width: 3px; border-radius: 8px; padding: 8px 10px; background: var(--card-inset); }
.view-item b { font-size: 11.5px; }
.view-line { margin: 4px 0 0; font-size: 10.5px; color: var(--ink-soft); line-height: 1.5; }
.view-line .k { color: var(--muted); }
.safety { font-size: 9.5px; color: var(--green); letter-spacing: 0.4px; margin: 8px 0 0; }
.standby-hint { align-self: center; margin: auto; text-align: center; }
</style>
