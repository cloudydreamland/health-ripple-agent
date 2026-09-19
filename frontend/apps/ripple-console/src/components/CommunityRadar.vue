<script setup lang="ts">
import { computed } from "vue";

/**
 * FIG.07 社区涟漪雷达 —— 个体的涟漪汇成社区的潮汐。
 * 跨患者聚合近7天守护信号：当多位患者在同一守护事项上同时未缓解/升级就医，
 * 那就是社区级早期预警（换季血糖潮、服药不依从潮）。计数型口径可复算。
 */
export interface RadarSignal {
  event: string;
  patientCount: number;
  activeCount: number;
  unresolved7d: number;
  escalated7d: number;
  signalScore: number;
}

export interface RadarData {
  patientsMonitored: number;
  totalActive: number;
  unresolved7d: number;
  escalated7d: number;
  tideIndex: number;
  tideLevel: string;
  signals: RadarSignal[];
}

const props = defineProps<{ radar: RadarData | null }>();

const TIDE_LABEL: Record<string, string> = {
  HIGH: "潮 · HIGH",
  MID: "涨 · MID",
  LOW: "平 · LOW",
  CALM: "静 · CALM",
};
const TIDE_COLOR: Record<string, string> = {
  HIGH: "#e05a47",
  MID: "#e59d3c",
  LOW: "#d9b84c",
  CALM: "#5cad85",
};

const maxScore = computed(() => Math.max(1, ...(props.radar?.signals ?? []).map((s) => s.signalScore)));
</script>

<template>
  <div v-if="radar" class="radar">
    <div class="radar-head">
      <div class="tide">
        <span class="tide-num mono" :style="{ color: TIDE_COLOR[radar.tideLevel] ?? 'var(--yellow)' }">{{ radar.tideIndex }}</span>
        <span class="tide-label">潮汐指数 · <b :style="{ color: TIDE_COLOR[radar.tideLevel] ?? 'var(--yellow)' }">{{ TIDE_LABEL[radar.tideLevel] ?? radar.tideLevel }}</b></span>
      </div>
      <div class="radar-meta mono">
        <span>在管患者 <b>{{ radar.patientsMonitored }}</b></span>
        <span>活跃触达 <b>{{ radar.totalActive }}</b></span>
        <span class="warn">未缓解7d <b>{{ radar.unresolved7d }}</b></span>
        <span class="danger">升级7d <b>{{ radar.escalated7d }}</b></span>
      </div>
    </div>

    <div class="signal-list">
      <div v-for="s in radar.signals" :key="s.event" class="signal-row">
        <span class="sig-name" :title="s.event">{{ s.event }}</span>
        <div class="sig-bar-track">
          <div class="sig-bar" :style="{ width: Math.max(4, s.signalScore / maxScore * 100) + '%' }" />
        </div>
        <span class="sig-counts mono">{{ s.patientCount }}人 · 未缓{{ s.unresolved7d }} · 升级{{ s.escalated7d }}</span>
        <span class="sig-score mono">{{ s.signalScore }}</span>
      </div>
      <p v-if="!radar.signals.length" class="hint">暂无社区信号。</p>
    </div>
    <p class="radar-note mono">信号强度=患者数+3×未缓解+5×升级就医（近7天）· 计数型口径可复算</p>
  </div>
  <p v-else class="hint">社区雷达需实时连接后端（跨患者聚合，仅医生角色可读）。</p>
</template>

<style scoped>
.radar { display: flex; flex-direction: column; gap: 12px; }
.radar-head { display: flex; align-items: baseline; gap: 22px; flex-wrap: wrap; }
.tide { display: flex; align-items: baseline; gap: 10px; }
.tide-num { font-size: 46px; font-weight: 700; line-height: 1; letter-spacing: -1px; font-variant-numeric: tabular-nums; }
.tide-label { font-size: 12px; color: var(--muted); }
.tide-label b { font-family: var(--font-mono); letter-spacing: 1px; }
.radar-meta { display: flex; gap: 18px; flex-wrap: wrap; font-size: 10.5px; color: var(--muted); letter-spacing: 1px; margin-left: auto; }
.radar-meta b { color: var(--ink); font-size: 13px; margin-left: 4px; }
.radar-meta .warn b { color: var(--yellow); }
.radar-meta .danger b { color: var(--red); }

.signal-list { display: flex; flex-direction: column; gap: 6px; }
.signal-row { display: flex; align-items: center; gap: 10px; min-width: 0; }
.sig-name { width: 220px; flex: none; font-size: 12.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sig-bar-track { flex: 1; height: 10px; border: 1px solid var(--line); border-radius: 3px; background: var(--card-inset); overflow: hidden; min-width: 60px; }
.sig-bar { height: 100%; background: linear-gradient(90deg, var(--gold), var(--yellow)); opacity: 0.85; }
.sig-counts { flex: none; width: 210px; font-size: 10px; color: var(--muted); letter-spacing: 0.4px; text-align: right; }
.sig-score { flex: none; width: 36px; text-align: right; font-size: 13px; font-weight: 700; color: var(--gold); }
.radar-note { margin: 0; font-size: 9.5px; color: var(--faint); letter-spacing: 0.6px; }
</style>
