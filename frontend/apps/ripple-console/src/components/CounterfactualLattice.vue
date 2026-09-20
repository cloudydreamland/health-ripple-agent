<script setup lang="ts">
import { computed, ref } from "vue";
import type { CounterfactualPath, CounterfactualTree } from "../types";

/**
 * 反事实决策晶格：16 条替代路径排成伪 3D 晶格，
 * 红色节点 = 护栏 FLAGGED（连向右侧 GUARDRAIL GATE 闸门，禁止下发）；
 * 墨色节点 = SAFE。下方为已选路径锚点。
 */
const props = defineProps<{ tree: CounterfactualTree | null }>();

const expanded = ref<number | null>(null);

const N = 4;
const CX = 178;
const TOP = 46;
const DX = 66;
const DY = 44;
const GATE = { x: 430, y: 176 };
const CHOSEN = { x: 178, y: 392 };

const lattice = computed(() => {
  const alts = props.tree?.alternativePaths ?? [];
  return alts.map((p, idx) => {
    const i = idx % N;
    const j = Math.floor(idx / N);
    return {
      path: p,
      idx,
      x: CX + (i - j) * DX,
      y: TOP + (i + j) * DY,
      flagged: p.guardrailVerdict === "FLAGGED",
    };
  });
});

const links = computed(() => {
  const out: Array<{ x1: number; y1: number; x2: number; y2: number }> = [];
  for (let i = 0; i < N; i++) {
    for (let j = 0; j < N; j++) {
      const idx = j * N + i;
      const a = lattice.value[idx];
      if (!a) continue;
      if (i + 1 < N) {
        const b = lattice.value[j * N + (i + 1)];
        if (b) out.push({ x1: a.x, y1: a.y, x2: b.x, y2: b.y });
      }
      if (j + 1 < N) {
        const b = lattice.value[(j + 1) * N + i];
        if (b) out.push({ x1: a.x, y1: a.y, x2: b.x, y2: b.y });
      }
    }
  }
  return out;
});

const gateEdges = computed(() =>
  lattice.value.map((n) => ({ x1: n.x, y1: n.y, x2: GATE.x, y2: GATE.y + 28, flagged: n.flagged })),
);

const summary = computed(() => props.tree?.guardrailSummary ?? null);
const flaggedCount = computed(
  () =>
    summary.value?.flaggedPaths ??
    (props.tree?.alternativePaths ?? []).filter((p: CounterfactualPath) => p.guardrailVerdict === "FLAGGED").length,
);

function toggle(idx: number) {
  expanded.value = expanded.value === idx ? null : idx;
}

function shortPath(s: string): string {
  return s.length > 22 ? s.slice(0, 21) + "…" : s;
}
</script>

<template>
  <div v-if="tree" class="cft-lattice">
    <div class="lattice-wrap">
      <svg viewBox="0 0 480 430" class="lattice-svg">
        <!-- 晶格连线 -->
        <line v-for="(l, i) in links" :key="'l' + i" :x1="l.x1" :y1="l.y1" :x2="l.x2" :y2="l.y2" class="lat-link" />
        <!-- 闸门连线 -->
        <line
          v-for="(e, i) in gateEdges" :key="'g' + i"
          :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2"
          :class="e.flagged ? 'gate-edge-flagged' : 'gate-edge-safe'"
        />
        <!-- 已选路径连线 -->
        <line :x1="CHOSEN.x" :y1="CHOSEN.y" :x2="lattice[0]?.x ?? CX" :y2="lattice[0]?.y ?? TOP" class="chosen-link" />

        <!-- 节点 -->
        <g
          v-for="n in lattice" :key="'n' + n.idx"
          class="lat-node" @click="toggle(n.idx)"
        >
          <circle :cx="n.x" :cy="n.y" r="9" :class="n.flagged ? 'node-flagged' : 'node-safe'" />
          <circle :cx="n.x" :cy="n.y" :r="n.flagged ? 3 : 2.2" :fill="n.flagged ? '#f4695c' : '#7e93ab'" />
          <g v-if="n.flagged" :transform="`translate(${n.x + 12.5}, ${n.y - 4.6})`" class="lock-mark">
            <rect x="0" y="2.4" width="6.6" height="5.2" rx="1.1" fill="none" stroke="#f4695c" stroke-width="1.1" />
            <path d="M 1.3 2.4 v -1.1 a 2 2 0 0 1 4 0 v 1.1" fill="none" stroke="#f4695c" stroke-width="1.1" />
          </g>
          <title>{{ n.path.path }} · {{ n.path.riskIfChosen }} · {{ n.path.guardrailVerdict }}</title>
        </g>

        <!-- 护栏闸门 -->
        <g>
          <rect :x="GATE.x - 42" :y="GATE.y" width="88" height="56" rx="8" class="gate-box" />
          <text :x="GATE.x + 2" :y="GATE.y + 22" text-anchor="middle" class="gate-title">GUARDRAIL</text>
          <text :x="GATE.x + 2" :y="GATE.y + 38" text-anchor="middle" class="gate-title gate-sub">GATE · CLOSED</text>
        </g>

        <!-- 已选路径锚点 -->
        <g>
          <circle :cx="CHOSEN.x" :cy="CHOSEN.y" r="12" class="node-chosen" />
          <circle :cx="CHOSEN.x" :cy="CHOSEN.y" r="3.4" fill="#4fd1a5" />
          <text :x="CHOSEN.x + 18" :y="CHOSEN.y + 4" class="chosen-label">CHOSEN · 已选路径</text>
        </g>

        <text x="10" y="20" class="lat-annot">16 ALT PATHS / 4×4 LATTICE · 红点=FLAGGED 锁定</text>
      </svg>
      <div class="lat-stats mono">
        <span>PATHS <b>{{ tree.counterfactualCount }}</b></span>
        <span class="red">FLAGGED <b>{{ flaggedCount }}</b></span>
        <span>SAFE <b>{{ (tree.alternativePaths?.length ?? 0) - flaggedCount }}</b></span>
        <span class="red">GATE <b>CLOSED</b></span>
      </div>
    </div>

    <div class="alt-list">
      <p class="list-title mono">// 替代路径明细（点击晶格节点或行展开）</p>
      <div
        v-for="(path, i) in tree.alternativePaths"
        :key="i"
        class="alt-item"
        :class="{ flagged: path.guardrailVerdict === 'FLAGGED', open: expanded === i }"
        @click="toggle(i)"
      >
        <div class="alt-top">
          <span class="verdict" :class="path.guardrailVerdict === 'FLAGGED' ? 'v-flagged' : 'v-safe'">
            {{ path.guardrailVerdict === "FLAGGED" ? "🔒 FLAG" : "✓ SAFE" }}
          </span>
          <span class="path-label">{{ shortPath(path.path) }}</span>
          <span class="risk mono" :class="'risk-' + path.riskIfChosen">{{ path.riskIfChosen }}</span>
        </div>
        <template v-if="expanded === i">
          <p class="outcome">若选择 → {{ path.counterfactualOutcome }}</p>
          <p class="evidence mono">{{ path.evidence }}</p>
          <p v-if="path.guardrailNote" class="note">{{ path.guardrailNote }}</p>
        </template>
      </div>
    </div>
  </div>
  <p v-else class="hint">尚无反事实决策晶格。</p>
</template>

<style scoped>
.cft-lattice { display: grid; grid-template-columns: 1.15fr 1fr; gap: 14px; }
@media (max-width: 1400px) { .cft-lattice { grid-template-columns: 1fr; } }
.lattice-wrap { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.lattice-svg { width: 100%; display: block; }
.lat-link { stroke: rgba(200, 224, 250, 0.1); stroke-width: 1; }
.gate-edge-flagged { stroke: rgba(244, 105, 92, 0.55); stroke-width: 1.2; }
.gate-edge-safe { stroke: rgba(200, 224, 250, 0.08); stroke-width: 0.8; stroke-dasharray: 2 5; }
.chosen-link { stroke: rgba(79, 209, 165, 0.5); stroke-width: 1.3; stroke-dasharray: 4 4; }
.lat-node { cursor: pointer; }
.node-flagged { fill: rgba(244, 105, 92, 0.16); stroke: #f4695c; stroke-width: 1.8; }
.node-safe { fill: #101f33; stroke: #7e93ab; stroke-width: 1.4; }
.node-chosen { fill: rgba(79, 209, 165, 0.14); stroke: #4fd1a5; stroke-width: 1.8; }
.lock-mark { opacity: 0.9; }
.gate-box { fill: rgba(244, 105, 92, 0.1); stroke: #f4695c; stroke-width: 1.5; }
.gate-title { font-size: 10px; font-weight: 700; fill: #f4695c; font-family: var(--font-mono); letter-spacing: 1px; }
.gate-sub { font-size: 8.5px; fill: #7e93ab; }
.chosen-label { font-size: 10.5px; fill: #4fd1a5; font-family: var(--font-mono); font-weight: 700; letter-spacing: 0.5px; }
.lat-annot { font-size: 9px; fill: #5a708c; font-family: var(--font-mono); letter-spacing: 0.8px; }
.lat-stats { display: flex; gap: 16px; font-size: 10.5px; color: #7e93ab; letter-spacing: 1px; padding: 0 4px; }
.lat-stats b { color: #dbe9f9; font-size: 13px; }
.lat-stats .red b { color: #f4695c; }

.alt-list { min-width: 0; display: flex; flex-direction: column; gap: 5px; max-height: 430px; overflow-y: auto; padding-right: 4px; }
.list-title { margin: 0 0 2px; font-size: 10px; color: var(--faint); letter-spacing: 1px; }
.alt-item { border: 1px solid var(--line); border-radius: 9px; padding: 7px 11px; background: var(--card-inset); cursor: pointer; }
.alt-item.flagged { border-color: rgba(189, 64, 51, 0.55); background: var(--red-bg); }
.alt-item.open { border-color: var(--ink); }
.alt-top { display: flex; align-items: center; gap: 8px; }
.verdict { font-family: var(--font-mono); font-size: 10px; font-weight: 700; letter-spacing: 0.5px; flex: none; }
.v-flagged { color: var(--red); }
.v-safe { color: var(--green); }
.path-label { flex: 1; font-size: 12.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.risk { font-size: 10px; color: var(--muted); flex: none; }
.risk-HIGH { color: var(--red); font-weight: 700; }
.risk-MEDIUM { color: var(--orange); font-weight: 700; }
.outcome { margin: 6px 0 0; font-size: 12px; color: var(--ink-soft); line-height: 1.55; }
.evidence { margin: 4px 0 0; color: var(--faint); font-size: 10.5px; }
.note { margin: 4px 0 0; font-size: 11px; color: var(--red); }
</style>
