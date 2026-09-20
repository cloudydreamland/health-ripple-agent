<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import type { RippleIntensity } from "../types";

const props = defineProps<{ intensity: RippleIntensity | null | undefined }>();

const LEVEL_COLOR: Record<string, string> = {
  RED: "#f4695c",
  ORANGE: "#f0a45c",
  YELLOW: "#e5c15c",
  GREEN: "#4fd1a5",
};

const color = computed(() => LEVEL_COLOR[props.intensity?.level ?? "YELLOW"] ?? "#e5c15c");

// RII 数字滚动：新推演到达时从当前值缓动至目标值
const display = ref(0);
let raf = 0;
watch(
  () => props.intensity?.index ?? 0,
  (target) => {
    cancelAnimationFrame(raf);
    const from = display.value;
    const start = performance.now();
    const dur = 950;
    const step = (now: number) => {
      const p = Math.min(1, (now - start) / dur);
      const eased = 1 - Math.pow(1 - p, 3);
      display.value = from + (target - from) * eased;
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
  },
  { immediate: true },
);
onBeforeUnmount(() => cancelAnimationFrame(raf));
</script>

<template>
  <div class="rii-summary">
    <div class="big-index">
      <span class="label">RII / 涟漪强度指数</span>
      <b :style="{ color }">{{ display.toFixed(1) }}</b>
      <span class="tag" :class="intensity?.level ?? 'YELLOW'">{{ intensity?.levelLabel ?? "等待推演" }}</span>
    </div>

    <p class="formula mono">RII = 100 × S × U × A × e^(−0.22·(ring−1))<br /><span>事件指数 = Top5 节点强度均值 · 每项可复算</span></p>

    <div class="radius-row">
      <span class="label">有效扩散半径</span>
      <span class="mono"><b>{{ intensity?.radius ?? 0 }}</b> / 5 环</span>
    </div>

    <div class="top-risks">
      <span class="label">TOP 风险节点 · 医生视线第一落点</span>
      <div v-for="(risk, i) in intensity?.topRisks ?? []" :key="i" class="risk-row">
        <span class="rank mono">#{{ i + 1 }}</span>
        <div class="risk-info">
          <span class="risk-label">{{ risk.label }}</span>
          <span class="risk-ring mono">{{ risk.ringName }} · R{{ risk.ring }}</span>
        </div>
        <div class="risk-bar-track">
          <div class="risk-bar" :style="{ width: risk.intensity + '%', background: color }" />
        </div>
        <span class="score mono">{{ risk.intensity.toFixed(1) }}</span>
      </div>
      <p v-if="!intensity?.topRisks?.length" class="hint">本次事件未推演出需要优先处置的节点。</p>
    </div>
  </div>
</template>

<style scoped>
.rii-summary { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.label {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 1.2px;
  color: var(--muted);
}
.big-index { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; border-bottom: 1px solid var(--line); padding-bottom: 12px; }
.big-index b { font-family: var(--font-mono); font-size: 38px; line-height: 1; letter-spacing: -1.5px; font-variant-numeric: tabular-nums; }
.formula { margin: 0; color: var(--ink-soft); line-height: 1.7; }
.formula span { color: var(--faint); font-size: 10.5px; }
.radius-row { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--line); padding-bottom: 10px; }
.radius-row .mono b { font-size: 17px; }
.top-risks { display: flex; flex-direction: column; gap: 8px; }
.risk-row { display: flex; align-items: center; gap: 8px; }
.rank { color: var(--faint); font-size: 10.5px; width: 20px; flex: none; }
.risk-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.risk-label { font-size: 12.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.risk-ring { font-size: 9.5px; color: var(--faint); }
.risk-bar-track { width: 56px; height: 5px; border-radius: 3px; background: var(--card-inset); border: 1px solid var(--line); overflow: hidden; flex: none; }
.risk-bar { height: 100%; }
.score { width: 34px; text-align: right; color: var(--ink-soft); font-size: 11px; flex: none; }
</style>
