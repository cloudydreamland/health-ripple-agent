<script setup lang="ts">
import { computed } from "vue";
import type { RippleIntensity } from "../types";

const props = defineProps<{ intensity: RippleIntensity | null | undefined }>();

const LEVEL_COLOR: Record<string, string> = {
  RED: "#ff5d6c",
  ORANGE: "#ffab4a",
  YELLOW: "#ffd94a",
};

const index = computed(() => props.intensity?.index ?? 0);
const level = computed(() => props.intensity?.level ?? "YELLOW");
const color = computed(() => LEVEL_COLOR[level.value] ?? "#ffd94a");

// 半圆仪表：弧从 -180° 到 0°
const ARC_RADIUS = 86;
const cx = 110;
const cy = 108;
const startAngle = Math.PI;
const endAngle = 0;

function pointOnArc(ratio: number): { x: number; y: number } {
  const angle = startAngle + (endAngle - startAngle) * ratio;
  return { x: cx + ARC_RADIUS * Math.cos(angle), y: cy - ARC_RADIUS * Math.sin(angle) };
}

const arcPath = computed(() => {
  const start = pointOnArc(0);
  const end = pointOnArc(1);
  return `M ${start.x} ${start.y} A ${ARC_RADIUS} ${ARC_RADIUS} 0 0 1 ${end.x} ${end.y}`;
});

const valueArc = computed(() => {
  const ratio = Math.min(1, Math.max(0, index.value / 100));
  if (ratio <= 0.001) return "";
  const end = pointOnArc(ratio);
  const largeArc = ratio > 0.5 ? 1 : 0;
  const start = pointOnArc(0);
  return `M ${start.x} ${start.y} A ${ARC_RADIUS} ${ARC_RADIUS} 0 ${largeArc} 1 ${end.x} ${end.y}`;
});

const ticks = [0, 25, 45, 70, 100];
</script>

<template>
  <div class="rii-gauge">
    <svg viewBox="0 0 220 150" class="gauge-svg">
      <path :d="arcPath" fill="none" stroke="rgba(94,140,200,0.18)" stroke-width="14" stroke-linecap="round" />
      <path
        v-if="valueArc"
        :d="valueArc"
        fill="none"
        :stroke="color"
        stroke-width="14"
        stroke-linecap="round"
        class="value-arc"
      />
      <text :x="cx" :y="cy - 18" text-anchor="middle" class="gauge-value" :fill="color">
        {{ index.toFixed(1) }}
      </text>
      <text :x="cx" :y="cy + 2" text-anchor="middle" class="gauge-unit">RII 涟漪强度指数</text>
      <text v-for="t in ticks" :key="t" :x="pointOnArc(t / 100).x" :y="pointOnArc(t / 100).y + 14"
            text-anchor="middle" class="gauge-tick">{{ t }}</text>
    </svg>
    <div class="gauge-level">
      <span class="tag" :class="level">{{ intensity?.levelLabel ?? "无涟漪" }}</span>
      <span class="radius-chip">有效扩散半径 <b>{{ intensity?.radius ?? 0 }}</b> / 5 环</span>
    </div>
    <p class="model mono" :title="intensity?.model">RII = 100 × S × U × A × e^(−0.22·(ring−1))</p>

    <div class="top-risks">
      <h4>Top 风险节点（医生视线第一落点）</h4>
      <div v-for="(risk, i) in intensity?.topRisks ?? []" :key="i" class="risk-row">
        <span class="risk-rank">#{{ i + 1 }}</span>
        <div class="risk-info">
          <span class="risk-label">{{ risk.label }}</span>
          <span class="risk-ring">{{ risk.ringName }}</span>
        </div>
        <div class="risk-bar-track">
          <div class="risk-bar" :style="{ width: risk.intensity + '%', background: color }" />
        </div>
        <span class="risk-score mono">{{ risk.intensity }}</span>
      </div>
      <p v-if="!intensity?.topRisks?.length" class="hint">本次事件未推演出需要优先处置的节点。</p>
    </div>
  </div>
</template>

<style scoped>
.rii-gauge { display: flex; flex-direction: column; gap: 10px; }
.gauge-svg { width: 100%; max-width: 240px; margin: 0 auto; display: block; }
.value-arc { filter: drop-shadow(0 0 6px currentColor); }
.gauge-value { font-size: 40px; font-weight: 800; font-family: var(--font-mono); }
.gauge-unit { font-size: 10.5px; fill: var(--text-dim); }
.gauge-tick { font-size: 9px; fill: var(--text-faint); font-family: var(--font-mono); }
.gauge-level { display: flex; align-items: center; justify-content: center; gap: 10px; }
.radius-chip { font-size: 12px; color: var(--text-dim); }
.radius-chip b { color: var(--cyan); font-size: 15px; }
.model { text-align: center; color: var(--text-faint); margin: 0; }
.top-risks { border-top: 1px dashed var(--line); padding-top: 10px; }
.top-risks h4 { margin: 0 0 8px; font-size: 12px; color: var(--text-dim); font-weight: 600; }
.risk-row { display: flex; align-items: center; gap: 8px; padding: 5px 0; }
.risk-rank { font-family: var(--font-mono); color: var(--text-faint); font-size: 11px; width: 22px; }
.risk-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.risk-label { font-size: 12.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.risk-ring { font-size: 10.5px; color: var(--text-faint); }
.risk-bar-track { width: 64px; height: 5px; border-radius: 3px; background: rgba(94,140,200,0.15); overflow: hidden; }
.risk-bar { height: 100%; border-radius: 3px; }
.risk-score { width: 34px; text-align: right; color: var(--text-dim); }
</style>
