<script setup lang="ts">
/**
 * AI 洞察面板（左列，参照 VitaSphere AI Insights）：
 * 智能体的主动判断流——主动守护评估 / 高风险预警 / 护栏结论 / 预报峰值。
 * 每条带图标、说明与"已入链"徽章，轮播式滚动（数据流动感）。
 */
const props = defineProps<{
  proactive: { proactiveAction: string; reason?: string } | null;
  topRisks: { label: string; ringName: string; intensity: number }[];
  guardrail: { auditedPaths?: number; flaggedPaths?: number } | null;
  forecastPeak: { hourOffset: number; drivers: string[] } | null;
}>();

const emit = defineEmits<{ focusRisk: [label: string] }>();
</script>

<template>
  <div class="insights">
    <p class="head mono">AI INSIGHTS · 系统判断</p>

    <div v-if="proactive" class="item">
      <span class="icon i-pro">✦</span>
      <div class="body">
        <b>主动守护评估</b>
        <span>{{ proactive.proactiveAction }}</span>
      </div>
      <span class="pill mono">已推送</span>
    </div>

    <div v-for="(risk, i) in topRisks.slice(0, 3)" :key="i" class="item risk" @click="emit('focusRisk', risk.label)">
      <span class="icon i-risk">◉</span>
      <div class="body">
        <b>{{ risk.label }}</b>
        <span>{{ risk.ringName }} · 强度 {{ risk.intensity.toFixed(1) }}</span>
      </div>
      <span class="pill mono pill-red">TOP{{ i + 1 }}</span>
    </div>

    <div v-if="guardrail" class="item">
      <span class="icon i-guard">⛨</span>
      <div class="body">
        <b>护栏审计</b>
        <span>{{ guardrail.auditedPaths }} 条替代路径，{{ guardrail.flaggedPaths }} 条高风险已锁定</span>
      </div>
      <span class="pill mono">已入链</span>
    </div>

    <div v-if="forecastPeak" class="item">
      <span class="icon i-fc">⏱</span>
      <div class="body">
        <b>72 小时预报峰值</b>
        <span>+{{ forecastPeak.hourOffset }}h · {{ forecastPeak.drivers.join("、") || "守护事项" }}</span>
      </div>
      <span class="pill mono">预报</span>
    </div>

    <p v-if="!proactive && !topRisks.length && !guardrail && !forecastPeak" class="empty">推演完成后，智能体的主动判断会出现在这里。</p>
  </div>
</template>

<style scoped>
.insights { display: flex; flex-direction: column; gap: 8px; }
.head { margin: 0 0 2px; font-size: 8.5px; letter-spacing: 2px; color: var(--muted); font-weight: 700; }
.item {
  display: flex; align-items: center; gap: 10px;
  border: 1px solid var(--line);
  border-radius: 11px;
  padding: 9px 11px;
  background: var(--card-inset);
  transition: border-color 0.2s ease, transform 0.2s ease;
}
.item:hover { border-color: rgba(56, 191, 248, 0.4); transform: translateX(2px); }
.item.risk { cursor: pointer; }
.icon {
  width: 30px; height: 30px; flex: none;
  display: grid; place-items: center;
  border-radius: 9px; font-size: 14px;
}
.i-pro { background: rgba(56, 191, 248, 0.12); color: #38cfe8; }
.i-risk { background: rgba(244, 105, 92, 0.12); color: #f4695c; }
.i-guard { background: rgba(79, 209, 165, 0.12); color: #4fd1a5; }
.i-fc { background: rgba(157, 140, 255, 0.12); color: #9d8cff; }
.body { display: flex; flex-direction: column; gap: 1px; min-width: 0; flex: 1; }
.body b { font-size: 12.5px; }
.body span { font-size: 10.5px; color: var(--muted); line-height: 1.45; }
.pill {
  flex: none; font-size: 8.5px; letter-spacing: 1px;
  color: #38cfe8; border: 1px solid rgba(56, 207, 232, 0.4);
  border-radius: 999px; padding: 1px 8px;
}
.pill-red { color: #f4695c; border-color: rgba(244, 105, 92, 0.4); }
.empty { color: var(--muted); font-size: 12px; }
</style>
