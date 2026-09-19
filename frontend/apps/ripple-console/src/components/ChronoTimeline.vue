<script setup lang="ts">
import { computed, ref } from "vue";
import { reviewTrigger } from "../api";
import type { ChronoTriggerView } from "../types";

const props = defineProps<{ triggers: ChronoTriggerView[]; live?: boolean }>();
const emit = defineEmits<{ reviewed: [info: { triggerId: number; decision: string; reviewStatus: string; event: string; note: string }] }>();

const TYPE_META: Record<string, { label: string; color: string; en: string }> = {
  WINDOW: { label: "窗口期", color: "#e05a47", en: "WINDOW" },
  RHYTHM: { label: "节律", color: "#a48cd4", en: "RHYTHM" },
  PERIODIC: { label: "周期", color: "#55aeb9", en: "PERIODIC" },
  SEASONAL: { label: "季节", color: "#5cad85", en: "SEASONAL" },
};

const hovered = ref<number | null>(null);
const busyId = ref<number | null>(null);
const reviewError = ref("");

const grouped = computed(() => {
  const order = ["WINDOW", "RHYTHM", "PERIODIC", "SEASONAL"];
  return order
    .filter((t) => props.triggers.some((x) => x.chronoType === t))
    .map((type) => ({ type, items: props.triggers.filter((x) => x.chronoType === type) }));
});

function metaOf(type: string) {
  return TYPE_META[type] ?? { label: type, color: "#958d74", en: type };
}

/** 医生审定（人机共驾终审）：审定入印鉴链；否决需填理由。 */
async function doReview(item: ChronoTriggerView, decision: "APPROVE" | "ADJUST" | "VETO") {
  if (busyId.value !== null) {
    return;
  }
  let note = "";
  if (decision === "VETO") {
    const reason = window.prompt(`否决「${item.event}」的理由（将随印鉴链存证）：`, "患者情况已由人工管理，暂停自动触达");
    if (reason === null) {
      return;
    }
    note = reason;
  } else if (decision === "ADJUST") {
    note = "医生改期：推迟24小时";
  }
  busyId.value = item.triggerId;
  reviewError.value = "";
  try {
    const nextAt = decision === "ADJUST"
      ? new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 19)
      : undefined;
    const updated = await reviewTrigger(item.triggerId, decision, note, nextAt);
    const reviewStatus = String((updated as Record<string, unknown>)?.reviewStatus ?? decision);
    emit("reviewed", { triggerId: item.triggerId, decision, reviewStatus, event: item.event, note });
  } catch (e) {
    reviewError.value = e instanceof Error ? e.message : String(e);
  } finally {
    busyId.value = null;
  }
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
          :class="{ hovered: hovered === item.triggerId, vetoed: item.reviewStatus === 'VETOED' }"
          @mouseenter="hovered = item.triggerId"
          @mouseleave="hovered = null"
        >
          <div class="trigger-main">
            <span class="event">{{ item.event }}</span>
            <span class="review-pill mono" v-if="item.reviewStatus" :data-review="item.reviewStatus">
              {{ item.reviewStatus === "APPROVED" ? "✓ 医生已通过" : item.reviewStatus === "ADJUSTED" ? "⟲ 医生已改期" : "✕ 医生已否决" }}
            </span>
            <span class="time mono">{{ item.triggerTime }}</span>
          </div>
          <div class="trigger-sub">
            <span class="action">{{ item.action }}</span>
            <span class="spacer" />
            <span class="next mono">NEXT {{ item.nextTriggerAt?.replace("T", " ").slice(5, 16) || "—" }}</span>
          </div>

          <!-- 医生审定：AI建议 ≠ 执行指令，终审权在医生；审定动作随印鉴链存证 -->
          <div v-if="!item.reviewStatus" class="review-row">
            <template v-if="live">
              <button class="rv approve" :disabled="busyId === item.triggerId" @click="doReview(item, 'APPROVE')">✓ 通过</button>
              <button class="rv adjust" :disabled="busyId === item.triggerId" @click="doReview(item, 'ADJUST')">⟲ 推迟24h</button>
              <button class="rv veto" :disabled="busyId === item.triggerId" @click="doReview(item, 'VETO')">✕ 否决</button>
            </template>
            <span v-else class="rv-hint mono">待医生审定（LIVE 模式可批，审定入印鉴链）</span>
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
    <p v-if="reviewError" class="rv-error" role="alert">审定失败：{{ reviewError }}</p>
    <p class="hint foot mono">悬停触达项展开 Timing Card；通过/改期/否决由医生终审，审定随印鉴链存证</p>
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
.trigger.hovered { border-color: var(--line-strong); background: var(--raised); }
.trigger.vetoed { opacity: 0.55; }
.trigger-main { display: flex; justify-content: space-between; gap: 10px; align-items: baseline; }
.event { font-size: 13px; font-weight: 600; }
.time { color: var(--ink-soft); font-size: 11px; }
.trigger-sub { display: flex; gap: 8px; align-items: baseline; margin-top: 3px; }
.action { font-size: 11.5px; color: var(--muted); }
.next { font-size: 9.5px; color: var(--faint); letter-spacing: 0.6px; }

/* 医生审定 */
.review-pill {
  flex: none;
  font-size: 9.5px;
  font-weight: 700;
  letter-spacing: 0.6px;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid var(--line-strong);
  color: var(--ink-soft);
}
.review-pill[data-review="APPROVED"] { color: var(--green); border-color: rgba(92, 173, 133, 0.5); background: var(--green-bg); }
.review-pill[data-review="ADJUSTED"] { color: var(--yellow); border-color: rgba(217, 184, 76, 0.5); background: var(--yellow-bg); }
.review-pill[data-review="VETOED"] { color: var(--red); border-color: rgba(224, 90, 71, 0.5); background: var(--red-bg); }
.review-row { display: flex; gap: 6px; margin-top: 7px; align-items: center; }
.rv {
  font-size: 10.5px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}
.rv.approve { background: transparent; border: 1px solid rgba(92, 173, 133, 0.55); color: var(--green); }
.rv.approve:hover { background: var(--green-bg); }
.rv.adjust { background: transparent; border: 1px solid rgba(217, 184, 76, 0.55); color: var(--yellow); }
.rv.adjust:hover { background: var(--yellow-bg); }
.rv.veto { background: transparent; border: 1px solid rgba(224, 90, 71, 0.55); color: var(--red); }
.rv.veto:hover { background: var(--red-bg); }
.rv-hint { font-size: 9.5px; color: var(--faint); letter-spacing: 0.5px; }
.rv-error { margin: 4px 0 0; font-size: 11px; color: var(--red); }

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
