<script setup lang="ts">
import { computed } from "vue";
import { DIMENSION_META, type RippleNode, type RippleDimensions } from "../types";

const props = defineProps<{
  dimensions: RippleDimensions;
  healthEvent: { diagnosis: string; drugs: string[]; pastHistory: string };
  intensity: { radius: number } | null | undefined;
  selected: RippleNode | null;
}>();

const emit = defineEmits<{ select: [node: RippleNode] }>();

interface PlacedNode extends RippleNode {
  dimension: string;
  x: number;
  y: number;
  r: number;
  angle: number;
}

const RING_RADII = [64, 116, 168, 220, 272];
const CENTER = 300;
const RING_OF_DIM: Record<string, number> = {
  drugLifestyleConflicts: 1,
  complicationSignals: 2,
  recheckWindows: 3,
  chronoTriggers: 4,
  familyAttentions: 5,
};

const placedNodes = computed<PlacedNode[]>(() => {
  const result: PlacedNode[] = [];
  for (const [dimension, nodes] of Object.entries(props.dimensions)) {
    const meta = DIMENSION_META[dimension];
    if (!meta) continue;
    const count = nodes.length;
    if (count === 0) continue;
    // 环号以后端 RII 标注为准（节点级 ring 字段）
    const ring = Number(nodes[0].ring) || RING_OF_DIM[dimension] || 1;
    nodes.forEach((node, i) => {
      // 同环节点均匀分布；不同维度错开起始角，避免连线重叠
      const startAngle = ring * 1.35 + (dimension.charCodeAt(0) % 7) * 0.31;
      const angle = startAngle + (i * 2 * Math.PI) / count;
      const radius = RING_RADII[ring - 1];
      const size = 8 + (node.intensity / 100) * 16;
      result.push({
        ...node,
        dimension,
        angle,
        x: CENTER + radius * Math.cos(angle),
        y: CENTER + radius * Math.sin(angle),
        r: size,
      });
    });
  }
  return result;
});

const radiusPx = computed(() => RING_RADII.map((r) => ({ r, ring: RING_RADII.indexOf(r) + 1 })));

const labelByRing: Record<number, string> = {
  1: "R1 用药安全圈",
  2: "R2 疾病进展圈",
  3: "R3 复查窗口圈",
  4: "R4 触达时机圈",
  5: "R5 家庭影响圈",
};

function nodeTitle(node: PlacedNode): string {
  const meta = DIMENSION_META[node.dimension];
  const label = String(node.conflict ?? node.complication ?? node.item ?? node.event ?? node.attention ?? "");
  return `${meta?.label ?? node.dimension}｜${label}｜强度 ${node.intensity}`;
}

function colorOf(node: PlacedNode): string {
  return DIMENSION_META[node.dimension]?.color ?? "#3fd8f2";
}

function isSelected(node: PlacedNode): boolean {
  return props.selected != null && node === props.selected;
}
</script>

<template>
  <div class="ripple-graph">
    <svg viewBox="0 0 600 600" class="graph-svg">
      <defs>
        <radialGradient id="event-core" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="#3fd8f2" stop-opacity="0.9" />
          <stop offset="70%" stop-color="#4f8fe6" stop-opacity="0.35" />
          <stop offset="100%" stop-color="#4f8fe6" stop-opacity="0" />
        </radialGradient>
      </defs>

      <!-- 五环背景（有效扩散半径内的环加亮） -->
      <g v-for="{ r, ring } in radiusPx" :key="ring">
        <circle
          :cx="CENTER" :cy="CENTER" :r="r"
          fill="none"
          :stroke="ring <= (intensity?.radius ?? 0) ? 'rgba(63,216,242,0.28)' : 'rgba(94,140,200,0.12)'"
          :stroke-width="ring <= (intensity?.radius ?? 0) ? 1.4 : 1"
          :stroke-dasharray="ring <= (intensity?.radius ?? 0) ? 'none' : '3 6'"
        />
        <text
          :x="CENTER + r * 0.7071 + 4" :y="CENTER - r * 0.7071 - 4"
          class="ring-label"
          :class="{ active: ring <= (intensity?.radius ?? 0) }"
        >{{ labelByRing[ring] }}</text>
      </g>

      <!-- 健康事件核心 -->
      <circle :cx="CENTER" :cy="CENTER" r="56" fill="url(#event-core)" class="core-pulse" />
      <circle :cx="CENTER" :cy="CENTER" r="34" fill="rgba(13,22,40,0.9)" stroke="rgba(63,216,242,0.5)" />
      <text :x="CENTER" :y="CENTER - 6" text-anchor="middle" class="core-label">健康事件</text>
      <text :x="CENTER" :y="CENTER + 12" text-anchor="middle" class="core-value">{{ healthEvent.diagnosis || "—" }}</text>

      <!-- 涟漪节点 -->
      <g
        v-for="(node, i) in placedNodes"
        :key="node.dimension + '-' + i"
        class="node"
        :class="{ selected: isSelected(node), high: node.intensity >= 70 }"
        @click="emit('select', node)"
      >
        <circle
          :cx="node.x" :cy="node.y" :r="node.r"
          :fill="colorOf(node)" fill-opacity="0.16"
          :stroke="colorOf(node)" stroke-width="1.6"
        />
        <circle :cx="node.x" :cy="node.y" :r="4" :fill="colorOf(node)" />
        <title>{{ nodeTitle(node) }}</title>
      </g>
    </svg>

    <div class="legend">
      <span v-for="(meta, dim) in DIMENSION_META" :key="dim" class="legend-item">
        <i :style="{ background: meta.color }" />{{ meta.label }}
      </span>
      <span class="legend-size">节点大小 ∝ 涟漪强度（RII）</span>
    </div>
  </div>
</template>

<style scoped>
.ripple-graph { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.graph-svg { width: 100%; max-width: 620px; }
.ring-label { font-size: 11px; fill: var(--text-faint); }
.ring-label.active { fill: var(--cyan); }
.core-label { font-size: 11px; fill: var(--text-dim); }
.core-value { font-size: 13px; font-weight: 700; fill: var(--text); }
.node { cursor: pointer; }
.node circle { transition: r 0.2s ease, filter 0.2s ease; }
.node:hover circle:first-child { filter: drop-shadow(0 0 8px currentColor); fill-opacity: 0.32; }
.node.selected circle:first-child { filter: drop-shadow(0 0 10px currentColor); stroke-width: 2.4; }
.node.high circle:first-child { animation: pulse 2.2s ease-in-out infinite; }
@keyframes pulse {
  0%, 100% { fill-opacity: 0.14; }
  50% { fill-opacity: 0.34; }
}
.legend { display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; align-items: center; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; color: var(--text-dim); }
.legend-item i { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
.legend-size { font-size: 11px; color: var(--text-faint); }
</style>
