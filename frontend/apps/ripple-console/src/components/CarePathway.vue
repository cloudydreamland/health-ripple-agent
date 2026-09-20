<script setup lang="ts">
import { computed } from "vue";

/**
 * 护理路径时间线（Care Pathway，参照 VitaSphere 底部路径条）：
 * 健康事件 → 涟漪推演 → 护栏审计 → 医生终审 → 触达守护 → 回执消解 → 印鉴存证。
 * 当前进度高亮 + 微光流动（数据流动感）。
 */
const props = defineProps<{
  hasRipple: boolean;
  hasGuardrail: boolean;
  hasEvidence: boolean;
  hasReview: boolean;
  activeTriggers: number;
  resolvedCount: number;
}>();

const STAGES = [
  { key: "event", label: "健康事件", en: "EVENT" },
  { key: "derive", label: "涟漪推演", en: "DERIVE" },
  { key: "guardrail", label: "护栏审计", en: "GUARDRAIL" },
  { key: "review", label: "医生终审", en: "REVIEW" },
  { key: "guard", label: "触达守护", en: "GUARD" },
  { key: "resolve", label: "回执消解", en: "RESOLVE" },
  { key: "seal", label: "印鉴存证", en: "SEAL" },
];

const currentIdx = computed(() => {
  if (!props.hasRipple) return -1;
  let idx = 1; // 推演完成
  if (props.hasGuardrail) idx = 2;
  if (props.hasEvidence) idx = 3;
  if (props.hasReview) idx = 4;
  if (props.activeTriggers > 0) idx = 5;
  if (props.resolvedCount > 0) idx = 6;
  return Math.min(idx, 6);
});

const progressPct = computed(() => ((currentIdx.value + 1) / STAGES.length) * 100);
</script>

<template>
  <div class="pathway">
    <div class="pw-head">
      <span class="pw-title">护理路径 · CARE PATHWAY</span>
      <span class="mono pw-stage">当前阶段：<b>{{ currentIdx >= 0 ? STAGES[currentIdx].label : "等待落石" }}</b></span>
    </div>
    <div class="pw-track">
      <div class="pw-line">
        <div class="pw-line-fill" :style="{ width: progressPct + '%' }" />
        <div class="pw-line-glow" :style="{ width: progressPct + '%' }" />
      </div>
      <div class="pw-nodes">
        <div v-for="(s, i) in STAGES" :key="s.key" class="pw-node"
             :class="{ done: i <= currentIdx, current: i === currentIdx }">
          <span class="dot"><i /></span>
          <span class="lbl">{{ s.label }}</span>
          <span class="en mono">{{ s.en }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pathway { display: flex; flex-direction: column; gap: 12px; }
.pw-head { display: flex; align-items: baseline; justify-content: space-between; }
.pw-title { font-family: var(--font-serif); font-weight: 700; letter-spacing: 1px; font-size: 14px; }
.pw-stage { font-size: 10px; color: var(--muted); letter-spacing: 1px; }
.pw-stage b { color: #38bdf8; }

.pw-track { position: relative; padding: 0 12px; }
.pw-line {
  position: absolute; left: 24px; right: 24px; top: 9px;
  height: 2px; background: var(--line); border-radius: 2px;
}
.pw-line-fill {
  position: absolute; left: 0; top: 0; bottom: 0;
  background: linear-gradient(90deg, rgba(56, 191, 248, 0.35), #38bdf8);
  border-radius: 2px;
  transition: width 0.8s cubic-bezier(0.22, 0.9, 0.3, 1);
}
.pw-line-glow {
  position: absolute; right: 0; top: -2px; bottom: -2px;
  background: linear-gradient(90deg, transparent, rgba(56, 191, 248, 0.8));
  width: 60px; border-radius: 4px;
  filter: blur(4px);
  animation: flow 1.8s ease-in-out infinite alternate;
}
@keyframes flow { from { opacity: 0.35; } to { opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .pw-line-glow { animation: none; } }

.pw-nodes { position: relative; display: flex; justify-content: space-between; }
.pw-node { display: flex; flex-direction: column; align-items: center; gap: 5px; width: 84px; }
.pw-node .dot {
  width: 18px; height: 18px; border-radius: 50%;
  border: 2px solid var(--line-strong);
  background: var(--card-inset);
  display: grid; place-items: center;
  transition: all 0.3s ease;
}
.pw-node .dot i { width: 6px; height: 6px; border-radius: 50%; background: var(--faint); }
.pw-node.done .dot { border-color: #38bdf8; }
.pw-node.done .dot i { background: #38bdf8; }
.pw-node.current .dot {
  box-shadow: 0 0 0 4px rgba(56, 191, 248, 0.18), 0 0 18px rgba(56, 191, 248, 0.5);
}
.pw-node.current .dot i { background: #7dd3fc; }
.pw-node .lbl { font-size: 11px; font-weight: 600; color: var(--muted); }
.pw-node.done .lbl, .pw-node.current .lbl { color: var(--ink); }
.pw-node.current .lbl { color: #7dd3fc; }
.pw-node .en { font-size: 7.5px; letter-spacing: 1.4px; color: var(--faint); }
</style>
