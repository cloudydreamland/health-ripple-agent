<script setup lang="ts">
import { computed, ref, watch } from "vue";

/**
 * FIG.05 未来72小时涟漪预报 —— 从"看见涟漪"到"预见涟漪"。
 * 手写 SVG 面积图：每桶由驱动事件叠加而成，峰值标注驱动事件名；
 * 与消解闭环联动（已缓解剔除/未缓解加压），纯确定性可复算。
 *
 * 依从性沙盘（第七轮）：拖动"守护执行度"滑杆，服务端以同一引擎确定性重算
 * 非季节驱动贡献 ×(1−执行度) 的沙盘曲线（虚线叠加）——预演"守护被执行后浪有多高"。
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

export interface SandboxData {
  adherence: number;
  buckets: { hourOffset: number; intensity: number }[];
  peakHourOffset: number;
  peakIntensity: number;
  horizonAvg: number;
}

const props = defineProps<{ forecast: ForecastData | null; sandbox?: SandboxData | null }>();
const emit = defineEmits<{ adherence: [value: number] }>();

const W = 720;
const H = 200;
const PAD_L = 34;
const PAD_B = 26;
const PAD_T = 18;

const sliderValue = ref(0);
let debounceTimer = 0;

// 拖动中只更新本地显示值，停顿 260ms 后才向服务端请求确定性重算（防抖）
watch(sliderValue, () => {
  window.clearTimeout(debounceTimer);
  const target = sliderValue.value;
  debounceTimer = window.setTimeout(() => emit("adherence", target / 100), 260);
});

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

const sandboxPoints = computed(() => {
  const s = props.sandbox;
  const f = props.forecast;
  if (!s?.buckets?.length || !f?.buckets?.length) return [] as { x: number; y: number }[];
  // 与实际曲线同量纲（同一 maxIntensity 缩放），保证两条线可直接对比
  const maxIntensity = Math.max(30, ...f.buckets.map((b) => b.intensity));
  return s.buckets.map((b) => ({
    x: PAD_L + (b.hourOffset / (f.horizonHours - 1)) * (W - PAD_L - 12),
    y: H - PAD_B - (b.intensity / maxIntensity) * (H - PAD_B - PAD_T),
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

const sandboxLinePath = computed(() => {
  const pts = sandboxPoints.value;
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

const sandboxDelta = computed(() => {
  const s = props.sandbox;
  const f = props.forecast;
  if (!s || !f) return null;
  return {
    peak: s.peakIntensity,
    avg: s.horizonAvg,
    drop: Math.round((f.horizonAvg - s.horizonAvg) * 10) / 10,
  };
});
</script>

<template>
  <div class="forecast">
    <template v-if="forecast && forecast.buckets.length">
      <svg :viewBox="`0 0 ${W} ${H}`" class="fc-svg">
        <defs>
          <linearGradient id="fc-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#f4695c" stop-opacity="0.4" />
            <stop offset="100%" stop-color="#f4695c" stop-opacity="0.02" />
          </linearGradient>
        </defs>
        <!-- 日刻度线 -->
        <g v-for="t in dayTicks" :key="t.label">
          <line :x1="t.x" :x2="t.x" :y1="PAD_T" :y2="H - PAD_B" stroke="rgba(200,224,250,0.14)" stroke-width="1" stroke-dasharray="2 5" />
          <text :x="t.x" :y="H - PAD_B + 15" text-anchor="middle" class="fc-tick">{{ t.label }}</text>
        </g>
        <!-- 强度基线 -->
        <line :x1="PAD_L" :x2="W - 12" :y1="H - PAD_B" :y2="H - PAD_B" stroke="#7e93ab" stroke-width="1.2" />
        <text :x="PAD_L - 6" :y="PAD_T + 4" text-anchor="end" class="fc-tick">强</text>
        <text :x="PAD_L - 6" :y="H - PAD_B" text-anchor="end" class="fc-tick">0</text>
        <path :d="areaPath" fill="url(#fc-fill)" />
        <path :d="linePath" fill="none" stroke="#f4695c" stroke-width="2" />
        <!-- 依从性沙盘曲线：守护被执行后的确定性重算（虚线） -->
        <path v-if="sandboxLinePath" :d="sandboxLinePath" fill="none" stroke="#38cfe8" stroke-width="2" stroke-dasharray="5 4" />
        <circle v-if="peakMark" :cx="points[forecast.peak.hourOffset].x" :cy="points[forecast.peak.hourOffset].y" r="4" fill="#f4695c" stroke="#0a1420" stroke-width="1.5" />
        <g v-if="peakMark">
          <line :x1="peakMark.x" :x2="peakMark.x" :y1="peakMark.y - 4" :y2="peakMark.y + 4" stroke="#f4695c" stroke-width="1" />
          <text :x="peakMark.x + 8" :y="peakMark.y + 4" class="fc-peak">峰 +{{ peakMark.hour }}h · {{ peakMark.label }}</text>
        </g>
      </svg>

      <!-- 依从性沙盘控制条 -->
      <div class="sandbox-bar">
        <span class="sb-label mono">依从性沙盘 · 守护执行度</span>
        <input v-model.number="sliderValue" class="sb-slider" type="range" min="0" max="100" step="5" />
        <span class="sb-value mono">{{ sliderValue }}%</span>
        <span v-if="sandboxDelta" class="sb-delta mono">
          沙盘均值 {{ sandboxDelta.avg }}（{{ sandboxDelta.drop >= 0 ? "−" : "+" }}{{ Math.abs(sandboxDelta.drop) }}） · 沙盘峰 {{ sandboxDelta.peak }}
        </span>
        <span v-else class="sb-hint mono">拖动滑杆预演"守护被执行后浪有多高"（虚线，确定性重算）</span>
      </div>

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
.fc-tick { font-size: 10px; fill: #7e93ab; font-family: var(--font-mono); }
.fc-peak { font-size: 12px; fill: var(--red); font-weight: 700; font-family: var(--font-serif); }
.fc-note { margin: 0; font-size: 10.5px; color: var(--muted); line-height: 1.7; }
.fc-empty { margin: 0; font-size: 11px; color: var(--faint); }

.sandbox-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.sb-label { font-size: 10px; letter-spacing: 1.2px; color: var(--cyan); font-weight: 700; flex: none; }
.sb-slider { flex: 1; min-width: 160px; accent-color: #38cfe8; height: 4px; cursor: pointer; }
.sb-value { font-size: 13px; font-weight: 700; color: var(--cyan); width: 42px; text-align: right; flex: none; }
.sb-delta { font-size: 10.5px; color: var(--ink-soft); letter-spacing: 0.4px; }
.sb-hint { font-size: 10.5px; color: var(--faint); letter-spacing: 0.4px; }
</style>
