<script setup lang="ts">
import { computed, ref } from "vue";
import type { ChronoTriggerView } from "../types";

const props = defineProps<{ triggers: ChronoTriggerView[] }>();

const TYPE_META: Record<string, { label: string; color: string; icon: string }> = {
  WINDOW: { label: "窗口期", color: "#ff5d6c", icon: "⚡" },
  RHYTHM: { label: "节律", color: "#9d7bff", icon: "🌙" },
  PERIODIC: { label: "周期", color: "#3fd8f2", icon: "🔁" },
  SEASONAL: { label: "季节", color: "#3ee6a4", icon: "🍂" },
};

const hovered = ref<number | null>(null);

const grouped = computed(() => {
  const order = ["WINDOW", "RHYTHM", "PERIODIC", "SEASONAL"];
  return order
    .filter((t) => props.triggers.some((x) => x.chronoType === t))
    .map((type) => ({ type, items: props.triggers.filter((x) => x.chronoType === type) }));
});

function metaOf(type: string) {
  return TYPE_META[type] ?? { label: type, color: "#8aa3c4", icon: "•" };
}
</script>

<template>
  <div v-if="triggers.length" class="chrono">
    <div v-for="group in grouped" :key="group.type" class="lane">
      <div class="lane-head">
        <span class="lane-tag" :style="{ color: metaOf(group.type).color, borderColor: metaOf(group.type).color }">
          {{ metaOf(group.type).icon }} {{ metaOf(group.type).label }}
        </span>
        <span class="lane-count">{{ group.items.length }} 项</span>
      </div>
      <div class="lane-items">
        <div
          v-for="item in group.items"
          :key="item.triggerId"
          class="trigger"
          :style="{ borderColor: metaOf(group.type).color + '55' }"
          @mouseenter="hovered = item.triggerId"
          @mouseleave="hovered = null"
        >
          <div class="trigger-main">
            <span class="event">{{ item.event }}</span>
            <span class="time mono">{{ item.triggerTime }}</span>
          </div>
          <div class="trigger-sub">
            <span class="status tag GREEN">{{ item.status }}</span>
            <span class="hint">{{ item.action }}</span>
          </div>

          <!-- Timing Card 循证卡片 -->
          <transition name="card">
            <div v-if="hovered === item.triggerId && item.timingCard?.evidenceBasis" class="timing-card">
              <div class="tc-head">
                <b>Timing Card 循证卡片</b>
                <span class="tag" :class="item.timingCard.evidenceLevel">{{ item.timingCard.evidenceLevel }}</span>
              </div>
              <p><b>依据</b>：{{ item.timingCard.evidenceBasis }}</p>
              <p><b>错过代价</b>：{{ item.timingCard.missCost }}</p>
            </div>
          </transition>
        </div>
      </div>
    </div>
    <p class="hint foot">悬停触达项查看 Timing Card（指南依据 / 错过代价 / 证据等级）</p>
  </div>
  <p v-else class="hint">本次事件未注册时间学触达计划。</p>
</template>

<style scoped>
.chrono { display: flex; flex-direction: column; gap: 12px; }
.lane-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.lane-tag { font-size: 12px; font-weight: 700; border: 1px solid; border-radius: 6px; padding: 2px 8px; background: rgba(0,0,0,0.2); }
.lane-count { font-size: 11px; color: var(--text-faint); }
.lane-items { display: flex; flex-direction: column; gap: 6px; }
.trigger {
  position: relative;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 12px;
  background: var(--bg-inset);
  cursor: default;
}
.trigger:hover { border-color: var(--line-strong); }
.trigger-main { display: flex; justify-content: space-between; gap: 10px; align-items: baseline; }
.event { font-size: 13px; font-weight: 600; }
.time { color: var(--cyan); }
.trigger-sub { display: flex; gap: 8px; align-items: center; margin-top: 4px; }
.timing-card {
  margin-top: 8px;
  border: 1px solid rgba(62, 230, 164, 0.4);
  background: rgba(62, 230, 164, 0.06);
  border-radius: 8px;
  padding: 8px 10px;
  z-index: 5;
}
.tc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; font-size: 12px; color: var(--green); }
.timing-card p { margin: 3px 0; font-size: 11.5px; color: var(--text-dim); line-height: 1.5; }
.timing-card b { color: var(--text); }
.card-enter-active { transition: all 0.15s ease; }
.card-enter-from { opacity: 0; transform: translateY(-4px); }
.foot { margin: 2px 0 0; }
</style>
