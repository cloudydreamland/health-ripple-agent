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
    "ring", "ringName", "intensity", "scoreBreakdown", "dimension", "angle", "x", "y", "r", "label",
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
  if (value >= 0.9) return "#bd4033";
  if (value >= 0.7) return "#c07a1d";
  return "#37808a";
}

const spineColor = computed(() => {
  const n = props.node;
  if (!n) return "#26231b";
  return n.intensity >= 70 ? "#bd4033" : n.intensity >= 40 ? "#c07a1d" : "#a8871a";
});
</script>

<template>
  <transition name="drawer">
    <aside v-if="node" class="drawer">
      <header class="drawer-head">
        <span class="annot mono">ANNOTATION · 节点标注</span>
        <button class="ghost close" @click="emit('close')">✕</button>
      </header>

      <div class="title-block" :style="{ borderLeftColor: spineColor }">
        <h3 class="drawer-title">{{ title }}</h3>
        <p class="hint">{{ node.ringName }}（R{{ node.ring }}）· 距健康事件第 {{ node.ring }} 环</p>
        <div class="chips">
          <span class="tag" :class="node.intensity >= 70 ? 'RED' : node.intensity >= 40 ? 'ORANGE' : 'YELLOW'">
            强度 {{ node.intensity }}
          </span>
          <span class="tag CYAN">{{ DIMENSION_META[node.dimension]?.label }}</span>
        </div>
      </div>

      <div class="rows">
        <div v-for="row in rows" :key="row.key" class="row">
          <span class="row-key">{{ row.key }}</span>
          <span class="row-value">{{ row.value }}</span>
        </div>
      </div>

      <div class="breakdown">
        <h4 class="mono">SCORE BREAKDOWN · 评分依据（可审计）</h4>
        <div v-for="row in breakdownRows" :key="row.key" class="score-row">
          <span class="score-key">{{ row.label }}</span>
          <div class="score-track"><div class="score-bar" :style="{ width: Math.min(100, row.value * 100) + '%', background: barColor(row.value) }" /></div>
          <span class="score-val mono">{{ row.value }}</span>
        </div>
        <p class="hint formula mono">intensity = 100 × S × U × A × decay</p>
      </div>
    </aside>
  </transition>
</template>

<style scoped>
.drawer {
  position: fixed;
  top: 14px;
  right: 14px;
  bottom: 14px;
  width: 372px;
  max-width: 92vw;
  background: var(--card);
  border: 1.5px solid var(--ink);
  border-radius: 14px;
  box-shadow: -10px 0 40px rgba(38, 33, 18, 0.18);
  padding: 14px 17px;
  overflow-y: auto;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.drawer-head { display: flex; justify-content: space-between; align-items: center; }
.annot { font-size: 9.5px; letter-spacing: 1.6px; color: var(--faint); }
.close { padding: 3px 10px; }
.title-block { border-left: 4px solid; padding-left: 12px; display: flex; flex-direction: column; gap: 5px; }
.drawer-title { margin: 0; font-size: 16.5px; line-height: 1.4; }
.title-block .hint { margin: 0; }
.chips { display: flex; gap: 6px; }
.rows { display: flex; flex-direction: column; }
.row { display: flex; flex-direction: column; gap: 2px; border-bottom: 1px dashed var(--line-strong); padding: 7px 0; }
.row-key { font-size: 10px; color: var(--muted); font-family: var(--font-mono); letter-spacing: 1px; }
.row-value { font-size: 13px; line-height: 1.55; }
.breakdown { border-top: 1px solid var(--line); padding-top: 10px; }
.breakdown h4 { margin: 0 0 9px; font-size: 9.5px; color: var(--muted); letter-spacing: 1.4px; font-weight: 600; }
.score-row { display: flex; align-items: center; gap: 9px; margin: 7px 0; }
.score-key { font-size: 11.5px; color: var(--ink-soft); width: 128px; flex: none; }
.score-track { flex: 1; height: 7px; background: var(--card-inset); border: 1px solid var(--line); border-radius: 4px; overflow: hidden; }
.score-bar { height: 100%; }
.score-val { width: 36px; text-align: right; color: var(--ink-soft); font-size: 11px; flex: none; }
.formula { margin-top: 7px; color: var(--faint); font-size: 10.5px; }
.drawer-enter-active, .drawer-leave-active { transition: transform 0.2s ease, opacity 0.2s ease; }
.drawer-enter-from, .drawer-leave-to { transform: translateX(30px); opacity: 0; }
</style>
