<script setup lang="ts">
import { computed, ref } from "vue";
import type { ChronoTriggerView } from "../types";

const props = defineProps<{ triggers: ChronoTriggerView[] }>();

const TYPE_META: Record<string, { label: string; color: string; en: string }> = {
  WINDOW: { label: "窗口期", color: "#bd4033", en: "WINDOW" },
  RHYTHM: { label: "节律", color: "#7a63a8", en: "RHYTHM" },
  PERIODIC: { label: "周期", color: "#37808a", en: "PERIODIC" },
  SEASONAL: { label: "季节", color: "#41795f", en: "SEASONAL" },
};

const hovered = ref<number | null>(null);

const grouped = computed(() => {
  const order = ["WINDOW", "RHYTHM", "PERIODIC", "SEASONAL"];
  return order
    .filter((t) => props.triggers.some((x) => x.chronoType === t))
    .map((type) => ({ type, items: props.triggers.filter((x) => x.chronoType === type) }));
});

function metaOf(type: string) {
  return TYPE_META[type] ?? { label: type, color: "#8f8a75", en: type };
}
</script>

<template>
  <div v-if="triggers.length" class="chrono">
    <div v-for="group in grouped" :key="group.type" class="lane">
      <div class="lane-head">
        <span class="lane-code mono" :style="{ color: metaOf(group.type).color }">{{ metaOf(group.type).en }}</span>
        <span class="lane-label">{{ metaOf(group.type).label }}</span>
        <span class="spacer" />
        <span class="lane-count mono">{{ group.items.length }} 项</span>
      </div>
      <div class="lane-items">
        <div
          v-for="item in group.items"
          :key="item.triggerId"
          class="trigger"
          :class="{ hovered: hovered === item.triggerId }"
          @mouseenter="hovered = item.triggerId"
          @mouseleave="hovered = null"
        >
          <div class="trigger-main">
            <span class="event">{{ item.event }}</span>
            <span class="time mono">{{ item.triggerTime }}</span>
          </div>
          <div class="trigger-sub">
            <span class="action">{{ item.action }}</span>
            <span class="spacer" />
            <span class="next mono">NEXT {{ item.nextTriggerAt?.replace("T", " ").slice(5, 16) ?? "—" }}</span>
          </div>

          <!-- Timing Card 循证卡片（索引卡风格） -->
          <transition name="card">
            <div v-if="hovered === item.triggerId && item.timingCard?.evidenceBasis" class="timing-card">
              <div class="tc-head">
                <b class="mono">TIMING CARD · 循证卡</b>
                <span class="tag" :class="item.timingCard.evidenceLevel">{{ item.timingCard.evidenceLevel }}</span>
              </div>
              <p><b>依据</b>：{{ item.timingCard.evidenceBasis }}</p>
              <p><b>错过代价</b>：{{ item.timingCard.missCost }}</p>
            </div>
          </transition>
        </div>
      </div>
    </div>
    <p class="hint foot mono">悬停触达项展开 Timing Card（指南依据 / 错过代价 / 证据等级）</p>
  </div>
  <p v-else class="hint">本次事件未注册时间学触达计划。</p>
</template>

<style scoped>
.chrono { display: flex; flex-direction: column; gap: 13px; }
.lane-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 5px; border-bottom: 1px solid var(--line); padding-bottom: 4px; }
.lane-code { font-size: 10px; font-weight: 700; letter-spacing: 1.4px; }
.lane-label { font-size: 12px; font-weight: 700; color: var(--ink-soft); }
.lane-count { font-size: 10px; color: var(--faint); }
.spacer { flex: 1; }
.lane-items { display: flex; flex-direction: column; gap: 5px; }
.trigger {
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 8px 12px;
  background: var(--card-inset);
  cursor: default;
}
.trigger.hovered { border-color: var(--ink); background: var(--card); }
.trigger-main { display: flex; justify-content: space-between; gap: 10px; align-items: baseline; }
.event { font-size: 13px; font-weight: 600; }
.time { color: var(--ink-soft); font-size: 11px; }
.trigger-sub { display: flex; gap: 8px; align-items: baseline; margin-top: 3px; }
.action { font-size: 11.5px; color: var(--muted); }
.next { font-size: 9.5px; color: var(--faint); letter-spacing: 0.6px; }
.timing-card {
  margin-top: 8px;
  border: 1px dashed var(--green);
  border-left: 4px solid var(--green);
  background: var(--green-bg);
  border-radius: 6px;
  padding: 8px 11px;
  z-index: 5;
}
.tc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; font-size: 10px; color: var(--green); letter-spacing: 1px; }
.timing-card p { margin: 3px 0; font-size: 11.5px; color: var(--ink-soft); line-height: 1.55; }
.timing-card b { color: var(--ink); }
.card-enter-active { transition: all 0.15s ease; }
.card-enter-from { opacity: 0; transform: translateY(-4px); }
.foot { margin: 2px 0 0; font-size: 10px; letter-spacing: 0.5px; }
</style>
