<script setup lang="ts">
import { computed } from "vue";

/**
 * FIG.05 未来72小时涟漪预报 —— 从"看见涟漪"到"预见涟漪"。
 * 手写 SVG 面积图：每桶由驱动事件叠加而成，峰值标注驱动事件名；
 * 与消解闭环联动（已缓解剔除/未缓解加压），纯确定性可复算。
 */
export interface ForecastBucket {
  hourOffset: number;
  intensity: number;
  drivers: string[];
}

export interface ForecastData {
  horizonHours: number;
  buckets: ForecastBucket[];
  peak: { hourOffset: number; intensity: number; drivers: string[] };
  horizonAvg: number;
  activeTriggers: number;
}

const props = defineProps<{ forecast: ForecastData | null }>();

const W = 720;
const H = 200;
const PAD_L = 34;
const PAD_B = 26;
const PAD_T = 18;

const points = computed(() => {
  const f = props.forecast;
  if (!f?.buckets?.length) return [] as { x: number; y: number; b: ForecastBucket }[];
  const maxIntensity = Math.max(30, ...f.buckets.map((b) => b.intensity));
  return f.buckets.map((b) => ({
    x: PAD_L + (b.hourOffset / (f.horizonHours - 1)) * (W - PAD_L - 12),
    y: H - PAD_B - (b.intensity / maxIntensity) * (H - PAD_B - PAD_T),
    b,
  }));
});

const areaPath = computed(() => {
  const pts = points.value;
  if (!pts.length) return "";
  const line = pts.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");
  return `${line} L${pts[pts.length - 1].x.toFixed(1)},${H - PAD_B} L${pts[0].x.toFixed(1)},${H - PAD_B} Z`;
});

const linePath = computed(() => {
  const pts = points.value;
  if (!pts.length) return "";
  return pts.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");
});

/** 峰值标注（限制在画布内）。 */
const peakMark = computed(() => {
  const pts = points.value;
  const f = props.forecast;
  if (!pts.length || !f || f.peak.intensity <= 0) return null;
  const p = pts[f.peak.hourOffset];
  if (!p) return null;
  const label = f.peak.drivers.length ? f.peak.drivers[0] : "峰值";
  const x = Math.min(Math.max(p.x, PAD_L + 60), W - 130);
  return { x, y: Math.max(p.y - 10, PAD_T + 8), hour: f.peak.hourOffset, label };
});

const dayTicks = computed(() => {
  const f = props.forecast;
  if (!f) return [] as { x: number; label: string }[];
  return [0, 24, 48, 72].map((h) => ({
    x: PAD_L + (h / f.horizonHours) * (W - PAD_L - 12),
    label: h === 0 ? "现在" : `+${h}h`,
  }));
});
</script>

<template>
  <div class="forecast">
    <template v-if="forecast && forecast.buckets.length">
      <svg :viewBox="`0 0 ${W} ${H}`" class="fc-svg">
        <defs>
          <linearGradient id="fc-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#bd4033" stop-opacity="0.34" />
            <stop offset="100%" stop-color="#bd4033" stop-opacity="0.02" />
          </linearGradient>
        </defs>
        <!-- 日刻度线 -->
        <g v-for="t in dayTicks" :key="t.label">
          <line :x1="t.x" :x2="t.x" :y1="PAD_T" :y2="H - PAD_B" stroke="#d8d2be" stroke-width="1" stroke-dasharray="2 5" />
          <text :x="t.x" :y="H - PAD_B + 15" text-anchor="middle" class="fc-tick">{{ t.label }}</text>
        </g>
        <!-- 强度基线 -->
        <line :x1="PAD_L" :x2="W - 12" :y1="H - PAD_B" :y2="H - PAD_B" stroke="#8f8a75" stroke-width="1.2" />
        <text :x="PAD_L - 6" :y="PAD_T + 4" text-anchor="end" class="fc-tick">强</text>
        <text :x="PAD_L - 6" :y="H - PAD_B" text-anchor="end" class="fc-tick">0</text>
        <path :d="areaPath" fill="url(#fc-fill)" />
        <path :d="linePath" fill="none" stroke="#bd4033" stroke-width="2" />
        <circle v-if="peakMark" :cx="points[forecast.peak.hourOffset].x" :cy="points[forecast.peak.hourOffset].y" r="4" fill="#bd4033" stroke="#f6f3e8" stroke-width="1.5" />
        <g v-if="peakMark">
          <line :x1="peakMark.x" :x2="peakMark.x" :y1="peakMark.y - 4" :y2="peakMark.y + 4" stroke="#bd4033" stroke-width="1" />
          <text :x="peakMark.x + 8" :y="peakMark.y + 4" class="fc-peak">峰 +{{ peakMark.hour }}h · {{ peakMark.label }}</text>
        </g>
      </svg>
      <p class="fc-note">
        未来 {{ forecast.horizonHours }} 小时确定性预报：由 {{ forecast.activeTriggers }} 项活跃守护触达的 RII 强度按时间学语义叠加，
        均值 {{ forecast.horizonAvg }}。每缓解一项触达，对应峰值即从曲线消失——预报与消解闭环联动。
      </p>
    </template>
    <p v-else class="fc-empty">72小时预报需实时连接后端（由患者真实触达计划计算，快照模式不做臆造预报）。</p>
  </div>
</template>

<style scoped>
.forecast { display: flex; flex-direction: column; gap: 8px; }
.fc-svg { width: 100%; }
.fc-tick { font-size: 10px; fill: #8f8a75; font-family: var(--font-mono); }
.fc-peak { font-size: 12px; fill: var(--red); font-weight: 700; font-family: var(--font-serif); }
.fc-note { margin: 0; font-size: 10.5px; color: var(--muted); line-height: 1.7; }
.fc-empty { margin: 0; font-size: 11px; color: var(--faint); }
</style>
