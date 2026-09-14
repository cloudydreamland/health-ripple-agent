<script setup lang="ts">
import { computed } from "vue";
import { DIMENSION_META, type RippleNode } from "../types";

const props = defineProps<{ node: RippleNode | null }>();
const emit = defineEmits<{ close: [] }>();

const title = computed(() => {
  const n = props.node;
  if (!n) return "";
  return String(n.conflict ?? n.complication ?? n.item ?? n.event ?? n.attention ?? "");
});

const rows = computed(() => {
  const n = props.node;
  if (!n) return [];
  // skip：RII标注字段（单独展示）+ 图谱布局内部字段 + 维度键（用于标签展示）
  const skip = new Set([
    "ring", "ringName", "intensity", "scoreBreakdown", "dimension", "angle", "x", "y", "r",
    "drug", "conflict", "diagnosis", "complication", "item", "event", "attention",
  ]);
  const zh: Record<string, string> = {
    risk: "风险", severity: "风险等级", advice: "处置建议", counterfactualNote: "反事实提示",
    signal: "早期信号", action: "立即行动", urgency: "紧迫度", timing: "时机", chronoType: "时间学类型",
    triggerTime: "触达时间",
  };
  return Object.entries(n)
    .filter(([k, v]) => !skip.has(k) && v != null && String(v).trim() !== "")
    .map(([k, v]) => ({ key: zh[k] ?? k, value: String(v) }));
});

const BREAKDOWN_ORDER: Array<[string, string]> = [
  ["severity", "严重度 S"],
  ["urgency", "紧迫度 U"],
  ["actionability", "可干预度 A"],
  ["decay", "环衰减 e^(−λ(r−1))"],
];

const breakdownRows = computed(() => {
  if (!props.node?.scoreBreakdown) return [];
  return BREAKDOWN_ORDER
    .map(([key, label]) => ({ key, label, value: Number(props.node!.scoreBreakdown[key] ?? 0) }))
    .filter((row) => Number.isFinite(row.value));
});

function barColor(value: number): string {
  if (value >= 0.9) return "#ff5d6c";
  if (value >= 0.7) return "#ffab4a";
  return "#3fd8f2";
}
</script>

<template>
  <transition name="drawer">
    <aside v-if="node" class="drawer">
      <header class="drawer-head">
        <div>
          <span class="tag" :class="node.intensity >= 70 ? 'RED' : node.intensity >= 40 ? 'ORANGE' : 'YELLOW'">
            强度 {{ node.intensity }}
          </span>
          <span class="tag CYAN" style="margin-left: 6px">{{ DIMENSION_META[node.dimension]?.label }}</span>
        </div>
        <button class="ghost close" @click="emit('close')">✕</button>
      </header>

      <h3 class="drawer-title">{{ DIMENSION_META[node.dimension]?.icon }} {{ title }}</h3>
      <p class="hint">{{ node.ringName }}（R{{ node.ring }}）· 距健康事件第 {{ node.ring }} 环</p>

      <div class="rows">
        <div v-for="row in rows" :key="row.key" class="row">
          <span class="row-key">{{ row.key }}</span>
          <span class="row-value">{{ row.value }}</span>
        </div>
      </div>

      <div class="breakdown">
        <h4>评分依据（可审计）</h4>
        <div v-for="row in breakdownRows" :key="row.key" class="score-row">
          <span class="score-key">{{ row.label }}</span>
          <div class="score-track"><div class="score-bar" :style="{ width: Math.min(100, row.value * 100) + '%', background: barColor(row.value) }" /></div>
          <span class="score-val mono">{{ row.value }}</span>
        </div>
        <p class="hint formula mono">intensity = 100 × S × U × A × 衰减</p>
      </div>
    </aside>
  </transition>
</template>

<style scoped>
.drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 360px;
  max-width: 92vw;
  background: var(--bg-panel-solid);
  border-left: 1px solid var(--line-strong);
  box-shadow: -18px 0 50px rgba(0, 0, 0, 0.5);
  padding: 16px 18px;
  overflow-y: auto;
  z-index: 50;
}
.drawer-head { display: flex; justify-content: space-between; align-items: center; }
.close { padding: 4px 10px; }
.drawer-title { margin: 12px 0 4px; font-size: 16px; line-height: 1.4; }
.rows { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.row { display: flex; flex-direction: column; gap: 2px; border-bottom: 1px dashed var(--line); padding-bottom: 7px; }
.row-key { font-size: 11px; color: var(--text-faint); }
.row-value { font-size: 13px; line-height: 1.55; }
.breakdown { margin-top: 14px; border-top: 1px solid var(--line); padding-top: 10px; }
.breakdown h4 { margin: 0 0 8px; font-size: 12.5px; color: var(--text-dim); }
.score-row { display: flex; align-items: center; gap: 8px; margin: 6px 0; }
.score-key { font-size: 11.5px; color: var(--text-dim); width: 118px; }
.score-track { flex: 1; height: 6px; background: rgba(94, 140, 200, 0.15); border-radius: 3px; overflow: hidden; }
.score-bar { height: 100%; border-radius: 3px; }
.score-val { width: 34px; text-align: right; color: var(--text-dim); }
.formula { margin-top: 6px; }
.drawer-enter-active, .drawer-leave-active { transition: transform 0.2s ease; }
.drawer-enter-from, .drawer-leave-to { transform: translateX(100%); }
</style>
