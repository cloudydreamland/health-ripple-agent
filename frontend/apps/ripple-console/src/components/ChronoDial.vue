<script setup lang="ts">
import { computed } from "vue";
import type { ChronoTriggerView } from "../types";

/**
 * 时辰守护盘 —— 把触达计划画在一张 24 小时日晷上。
 * 每项触达按其发生时辰占一段弧（窗口期/节律/周期/季节各有墨色），
 * 墨针指向此刻，朱砂点标出下一个即将触达的时辰。
 */
const props = defineProps<{ triggers: ChronoTriggerView[] }>();

const TYPE_COLOR: Record<string, string> = {
  WINDOW: "#f4695c",
  RHYTHM: "#9d8cff",
  PERIODIC: "#38cfe8",
  SEASONAL: "#4fd1a5",
};

const CX = 150;
const CY = 150;
const R = 108;

function parseWindow(s: string): { a: number; b: number } | null {
  const range = s.match(/(\d{1,2})\s*[-~至]\s*(\d{1,2})\s*点?/);
  if (range) {
    const a = Math.min(23, Math.max(0, +range[1]));
    let b = Math.min(24, Math.max(1, +range[2]));
    if (b <= a) b = Math.min(24, a + 1);
    return { a, b };
  }
  const single = s.match(/(\d{1,2})\s*点/);
  if (single) {
    const a = Math.min(23, Math.max(0, +single[1]));
    return { a, b: a + 1 };
  }
  return null;
}

interface Arc {
  id: number;
  type: string;
  color: string;
  d: string;
  event: string;
}

function polar(r: number, hour: number): { x: number; y: number } {
  const ang = ((hour / 24) * 360 - 90) * (Math.PI / 180);
  return { x: CX + r * Math.cos(ang), y: CY + r * Math.sin(ang) };
}

function arcPath(a: number, b: number): string {
  const p1 = polar(R, a);
  const p2 = polar(R, b);
  const large = b - a > 12 ? 1 : 0;
  return `M ${p1.x.toFixed(1)} ${p1.y.toFixed(1)} A ${R} ${R} 0 ${large} 1 ${p2.x.toFixed(1)} ${p2.y.toFixed(1)}`;
}

const arcs = computed<Arc[]>(() =>
  props.triggers.map((t) => {
    let win = t.triggerTime ? parseWindow(t.triggerTime) : null;
    if (!win && t.nextTriggerAt) {
      const h = new Date(t.nextTriggerAt).getHours();
      if (Number.isFinite(h)) win = { a: h, b: h + 2 };
    }
    if (!win) win = { a: 0, b: 2 };
    return {
      id: t.triggerId,
      type: t.chronoType,
      color: TYPE_COLOR[t.chronoType] ?? "#7e93ab",
      d: arcPath(win.a, win.b),
      event: t.event,
    };
  }),
);

/** 下一个触达：nextTriggerAt 最近且在未来的；演示数据取第一个 ACTIVE。 */
const nextTrigger = computed(() => {
  const now = Date.now();
  const future = props.triggers
    .filter((t) => t.nextTriggerAt && new Date(t.nextTriggerAt).getTime() > now)
    .sort((a, b) => new Date(a.nextTriggerAt).getTime() - new Date(b.nextTriggerAt).getTime());
  return future[0] ?? props.triggers[0] ?? null;
});

const nextPos = computed(() => {
  if (!nextTrigger.value) return null;
  const arc = arcs.value.find((a) => a.id === nextTrigger.value!.triggerId);
  if (!arc) return null;
  // 用弧中点近似标记位置：直接解析 nextTriggerAt 的小时
  const h = new Date(nextTrigger.value.nextTriggerAt).getHours();
  return polar(R + 14, Number.isFinite(h) ? h + 0.5 : 12);
});

const nowAngle = computed(() => {
  const d = new Date();
  return (d.getHours() + d.getMinutes() / 60) / 24;
});

const nowPos = computed(() => polar(R - 16, nowAngle.value * 24));

const ticks = computed(() =>
  Array.from({ length: 24 }, (_, h) => {
    const p1 = polar(R + 4, h);
    const p2 = polar(R + (h % 6 === 0 ? 10 : 7), h);
    return { h, p1, p2, major: h % 6 === 0 };
  }),
);

function hourLabel(h: number): { x: number; y: number; text: string } {
  const p = polar(R + 22, h);
  return { x: p.x, y: p.y + 3, text: `${h}时` };
}
</script>

<template>
  <div class="dial-wrap">
    <svg viewBox="0 0 300 300" class="dial">
      <!-- 表盘 -->
      <circle :cx="CX" :cy="CY" :r="R" fill="none" stroke="#dbe9f9" stroke-opacity="0.28" stroke-width="1" />
      <circle :cx="CX" :cy="CY" :r="R - 30" fill="none" stroke="#dbe9f9" stroke-opacity="0.09" stroke-width="1" stroke-dasharray="2 6" />
      <line
        v-for="t in ticks" :key="t.h"
        :x1="t.p1.x" :y1="t.p1.y" :x2="t.p2.x" :y2="t.p2.y"
        :stroke="t.major ? '#dbe9f9' : '#7e93ab'"
        :stroke-opacity="t.major ? 0.65 : 0.4"
        :stroke-width="t.major ? 1.6 : 1"
      />
      <text
        v-for="t in ticks.filter(t => t.major)" :key="'l' + t.h"
        :x="hourLabel(t.h).x" :y="hourLabel(t.h).y"
        text-anchor="middle" class="dial-label"
      >{{ hourLabel(t.h).text }}</text>

      <!-- 触达弧 -->
      <path
        v-for="a in arcs" :key="a.id"
        :d="a.d" fill="none"
        :stroke="a.color" stroke-width="8" stroke-linecap="round" stroke-opacity="0.9"
      >
        <title>{{ a.event }}</title>
      </path>

      <!-- 下一触达（朱砂点） -->
      <g v-if="nextPos">
        <circle :cx="nextPos.x" :cy="nextPos.y" r="6" fill="#0a1420" stroke="#f4695c" stroke-width="2" />
        <circle :cx="nextPos.x" :cy="nextPos.y" r="2" fill="#f4695c" />
        <text :x="nextPos.x" :y="nextPos.y - 10" text-anchor="middle" class="dial-next">NEXT</text>
      </g>

      <!-- 此刻墨针 -->
      <line :x1="CX" :y1="CY" :x2="nowPos.x" :y2="nowPos.y" stroke="#dbe9f9" stroke-width="1.6" />
      <circle :cx="nowPos.x" :cy="nowPos.y" r="3" fill="#dbe9f9" />

      <!-- 盘心 -->
      <text :x="CX" :y="CY - 2" text-anchor="middle" class="dial-num">{{ triggers.length }}</text>
      <text :x="CX" :y="CY + 15" text-anchor="middle" class="dial-cap">触达时辰</text>
    </svg>
    <div class="dial-legend mono">
      <span><i style="background:#f4695c" />窗口期</span>
      <span><i style="background:#9d8cff" />节律</span>
      <span><i style="background:#38cfe8" />周期</span>
      <span><i style="background:#4fd1a5" />季节</span>
    </div>
  </div>
</template>

<style scoped>
.dial-wrap { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.dial { width: 100%; max-width: 280px; display: block; }
.dial-label { font-size: 10px; fill: #7e93ab; font-family: var(--font-mono); }
.dial-next { font-size: 8.5px; fill: #f4695c; font-family: var(--font-mono); letter-spacing: 1px; font-weight: 700; }
.dial-num { font-size: 26px; font-weight: 700; fill: #dbe9f9; font-family: var(--font-mono); }
.dial-cap { font-size: 9.5px; fill: #7e93ab; letter-spacing: 2px; }
.dial-legend { display: flex; gap: 12px; font-size: 10px; color: #a9c0dc; }
.dial-legend span { display: inline-flex; align-items: center; gap: 4px; }
.dial-legend i { width: 8px; height: 8px; border-radius: 50%; }
</style>
