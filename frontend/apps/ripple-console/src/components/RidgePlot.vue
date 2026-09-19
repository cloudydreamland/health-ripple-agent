<script setup lang="ts">
import { computed } from "vue";
import type { RippleDimensions, RippleIntensity } from "../types";

/**
 * RII 山脊剖面（joyplot）：五环各成一条山脊，
 * 峰位 = 该环节点强度分布；前环（R1）在前、后环（R5）在后，
 * 直观呈现 e^(−0.22·(r−1)) 环衰减——越外环山脊越靠左、越矮。
 */
const props = defineProps<{
  dimensions: RippleDimensions;
  intensity: RippleIntensity | null | undefined;
}>();

const RING_NAMES: Record<number, string> = {
  1: "用药安全圈",
  2: "疾病进展圈",
  3: "复查窗口圈",
  4: "触达时机圈",
  5: "家庭影响圈",
};

const X0 = 132;
const X1 = 944;
const BASE_FRONT = 428;
const BAND = 62;
const AMP = 50;
const SIGMA = 6.2;
const RISK_LINE = 70;

const rings = computed(() => {
  const byRing = new Map<number, number[]>();
  for (const nodes of Object.values(props.dimensions)) {
    for (const n of nodes) {
      const ring = Number(n.ring) || 1;
      if (!byRing.has(ring)) byRing.set(ring, []);
      byRing.get(ring)!.push(Number(n.intensity) || 0);
    }
  }
  return [1, 2, 3, 4, 5].map((ring) => ({
    ring,
    name: RING_NAMES[ring] ?? `R${ring}`,
    values: byRing.get(ring) ?? [],
  }));
});

function xOf(v: number): number {
  return X0 + (Math.min(105, Math.max(-5, v)) / 100) * (X1 - X0);
}

function ridgePath(values: number[], baseline: number): string {
  if (!values.length) return "";
  let maxSum = 1e-6;
  const xs: number[] = [];
  const sums: number[] = [];
  for (let v = -5; v <= 105; v += 1.5) {
    let s = 0;
    for (const val of values) s += Math.exp(-((v - val) * (v - val)) / (2 * SIGMA * SIGMA));
    xs.push(v);
    sums.push(s);
    if (s > maxSum) maxSum = s;
  }
  const pts = xs.map((v, i) => {
    const x = xOf(v);
    const y = baseline - (sums[i] / maxSum) * AMP;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  return `M ${X0},${baseline} L ${pts.join(" L ")} L ${X1},${baseline} Z`;
}

function baselineOf(ring: number): number {
  return BASE_FRONT - (ring - 1) * BAND;
}

const topRisk = computed(() => props.intensity?.topRisks?.[0] ?? null);

const axisTicks = [0, 20, 40, 60, 80, 100];
</script>

<template>
  <div class="ridge">
    <svg viewBox="0 0 980 470" class="ridge-svg" preserveAspectRatio="xMidYMid meet">
      <defs>
        <pattern id="risk-hatch" width="7" height="7" patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
          <rect width="7" height="7" fill="rgba(224,90,71,0.07)" />
          <line x1="0" y1="0" x2="0" y2="7" stroke="rgba(224,90,71,0.3)" stroke-width="1.4" />
        </pattern>
        <linearGradient id="ridge-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#2b251a" />
          <stop offset="100%" stop-color="#191510" />
        </linearGradient>
      </defs>

      <!-- 高风险区（≥70）：红色斜纹带 -->
      <rect :x="xOf(RISK_LINE)" y="112" :width="X1 - xOf(RISK_LINE)" height="330" fill="url(#risk-hatch)" />
      <line :x1="xOf(RISK_LINE)" y1="112" :x2="xOf(RISK_LINE)" y2="442" stroke="#e05a47" stroke-width="1.2" stroke-dasharray="4 4" opacity="0.8" />
      <text :x="xOf(RISK_LINE) + 8" y="434" class="risk-zone-label">RISK ZONE ≥ 70</text>

      <!-- 纵向网格 -->
      <g v-for="t in axisTicks" :key="'grid-' + t">
        <line :x1="xOf(t)" y1="112" :x2="xOf(t)" y2="442" stroke="#ece7d6" stroke-opacity="0.06" stroke-width="1" />
        <text :x="xOf(t)" y="458" text-anchor="middle" class="axis">{{ t }}</text>
      </g>

      <!-- 山脊：R5 后 → R1 前 -->
      <g v-for="r in [...rings].reverse()" :key="'ridge-' + r.ring">
        <path v-if="r.values.length" :d="ridgePath(r.values, baselineOf(r.ring))" class="ridge-layer" />
        <text :x="X0 - 12" :y="baselineOf(r.ring) - 4" text-anchor="end" class="ring-name">
          R{{ r.ring }} {{ r.name }}
        </text>
        <text :x="X0 - 12" :y="baselineOf(r.ring) + 10" text-anchor="end" class="ring-count">
          {{ r.values.length ? `n=${r.values.length}` : "NO NODE" }}
        </text>
        <circle :cx="X0 - 5" :cy="baselineOf(r.ring)" r="2.4" fill="#958d74" />
      </g>

      <!-- Top1 风险标记 -->
      <g v-if="topRisk">
        <line
          :x1="xOf(topRisk.intensity)" y1="118"
          :x2="xOf(topRisk.intensity)" :y2="baselineOf(topRisk.ring)"
          stroke="#e05a47" stroke-width="1.3" stroke-dasharray="3 4"
        />
        <g :transform="`translate(${Math.min(xOf(topRisk.intensity), X1 - 212)}, 132)`">
          <rect x="0" y="-14" width="200" height="22" rx="5" fill="#241f16" stroke="#e05a47" stroke-width="1.2" />
          <circle cx="10" cy="-3" r="3" fill="#e05a47" />
          <text x="18" y="1" class="top-chip">{{ topRisk.label }} · {{ topRisk.intensity.toFixed(1) }}</text>
        </g>
      </g>

      <!-- 基线与角标 -->
      <line :x1="X0" y1="442" :x2="X1" y2="442" stroke="#ece7d6" stroke-opacity="0.35" stroke-width="1.2" />
      <text :x="X1" y="104" text-anchor="end" class="axis axis-unit">NODE INTENSITY (RII 节点强度)</text>
      <g class="crosshair" opacity="0.5">
        <path d="M 128 112 h 8 M 132 108 v 8" stroke="#958d74" stroke-width="1" fill="none" />
        <path d="M 940 112 h 8 M 944 108 v 8" stroke="#958d74" stroke-width="1" fill="none" />
        <path d="M 128 442 h 8 M 132 438 v 8" stroke="#958d74" stroke-width="1" fill="none" />
        <path d="M 940 442 h 8 M 944 438 v 8" stroke="#958d74" stroke-width="1" fill="none" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.ridge { min-width: 0; }
.ridge-svg { width: 100%; height: auto; max-height: clamp(320px, 44vh, 460px); display: block; margin: 0 auto; }
.ridge-layer { filter: drop-shadow(0 0 10px rgba(216, 210, 192, 0.16)); }
.ridge-layer {
  fill: url(#ridge-fill);
  fill-opacity: 0.94;
  stroke: #d8d2c0;
  stroke-width: 1.1;
  stroke-opacity: 0.85;
}
.ring-name { font-size: 11.5px; fill: #c4bca4; font-weight: 600; font-family: var(--font-sans); }
.ring-count { font-size: 9px; fill: #7d7660; font-family: var(--font-mono); letter-spacing: 1px; }
.axis { font-size: 9.5px; fill: #7d7660; font-family: var(--font-mono); }
.axis-unit { letter-spacing: 1px; }
.risk-zone-label { font-size: 9.5px; fill: #e05a47; font-family: var(--font-mono); letter-spacing: 1.4px; }
.top-chip { font-size: 10.5px; fill: #ece7d6; font-family: var(--font-mono); }
.crosshair path { opacity: 0.7; }
</style>
