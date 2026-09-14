<script setup lang="ts">
import type { CounterfactualTree } from "../types";

defineProps<{ tree: CounterfactualTree | null }>();
</script>

<template>
  <div v-if="tree" class="cft">
    <div class="chosen">
      <span class="tag GREEN">✓ 已选路径</span>
      <p class="chosen-text">{{ tree.chosenPath }}</p>
    </div>

    <div class="alt-header">
      <h4>反事实替代路径（如果选了别的会怎样）</h4>
      <span class="hint">共 {{ tree.counterfactualCount }} 条 · 护栏逐条审计</span>
    </div>

    <div class="alt-list">
      <div
        v-for="(path, i) in tree.alternativePaths"
        :key="i"
        class="alt-item"
        :class="path.guardrailVerdict === 'FLAGGED' ? 'flagged' : 'safe'"
      >
        <div class="alt-top">
          <span :class="['verdict', path.guardrailVerdict === 'FLAGGED' ? 'v-flagged' : 'v-safe']">
            {{ path.guardrailVerdict === "FLAGGED" ? "🔒 FLAGGED" : "✓ SAFE" }}
          </span>
          <span class="risk" :class="'risk-' + path.riskIfChosen">风险 {{ path.riskIfChosen }}</span>
          <span class="path-label">{{ path.path }}</span>
        </div>
        <p class="outcome">若选择 → {{ path.counterfactualOutcome }}</p>
        <p class="evidence mono">{{ path.evidence }}</p>
        <p v-if="path.guardrailNote" class="note">{{ path.guardrailNote }}</p>
      </div>
    </div>

    <div v-if="tree.guardrailSummary" class="guardrail">
      <span class="tag CYAN">护栏审计</span>
      <span>
        已审计 <b>{{ tree.guardrailSummary.auditedPaths }}</b> 条 ·
        FLAGGED <b class="flagged-num">{{ tree.guardrailSummary.flaggedPaths }}</b> 条（禁止作为建议下发）
      </span>
    </div>
  </div>
  <p v-else class="hint">尚无反事实决策树。</p>
</template>

<style scoped>
.chosen { border: 1px solid rgba(62, 230, 164, 0.4); background: var(--green-bg); border-radius: 10px; padding: 10px 14px; }
.chosen-text { margin: 8px 0 0; font-size: 13.5px; font-weight: 600; }
.alt-header { display: flex; align-items: baseline; justify-content: space-between; margin: 14px 0 8px; }
.alt-header h4 { margin: 0; font-size: 13px; color: var(--text-dim); }
.alt-list { display: flex; flex-direction: column; gap: 8px; max-height: 420px; overflow-y: auto; padding-right: 4px; }
.alt-item { border: 1px solid var(--line); border-radius: 10px; padding: 9px 12px; background: var(--bg-inset); }
.alt-item.flagged { border-color: rgba(255, 93, 108, 0.45); background: var(--red-bg); }
.alt-item.safe { opacity: 0.88; }
.alt-top { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.verdict { font-size: 11px; font-weight: 700; letter-spacing: 0.4px; }
.v-flagged { color: var(--red); }
.v-safe { color: var(--green); }
.risk { font-size: 10.5px; padding: 1px 7px; border-radius: 999px; border: 1px solid var(--line); color: var(--text-dim); }
.risk-HIGH { color: var(--red); border-color: rgba(255, 93, 108, 0.5); }
.risk-MEDIUM { color: var(--orange); border-color: rgba(255, 171, 74, 0.5); }
.path-label { font-size: 13px; font-weight: 600; }
.outcome { margin: 5px 0 0; font-size: 12px; color: var(--text-dim); line-height: 1.55; }
.evidence { margin: 4px 0 0; color: var(--text-faint); }
.note { margin: 4px 0 0; font-size: 11px; color: var(--red); opacity: 0.85; }
.guardrail { display: flex; align-items: center; gap: 10px; margin-top: 12px; font-size: 12.5px; color: var(--text-dim); }
.flagged-num { color: var(--red); }
</style>
