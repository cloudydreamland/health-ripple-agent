<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { DIMENSION_META, type RippleDimensions, type RippleIntensity, type RippleNode } from "../types";

/**
 * 活水涟漪池 —— 作品的核心视觉母题。
 * 健康事件是投入水面的石头：推演发生时石落水开，涟漪一圈圈荡开，
 * 节点随涟漪抵达各自环层时如墨滴般浮现。悬停/点击节点查看标注。
 */
const props = defineProps<{
  dimensions: RippleDimensions;
  healthEvent: { diagnosis: string; drugs: string[]; pastHistory: string };
  intensity: RippleIntensity | null | undefined;
  selected: RippleNode | null;
}>();

const emit = defineEmits<{ select: [node: RippleNode] }>();

const RING_OF_DIM: Record<string, number> = {
  drugLifestyleConflicts: 1,
  complicationSignals: 2,
  recheckWindows: 3,
  chronoTriggers: 4,
  familyAttentions: 5,
};
const RING_NAMES: Record<number, string> = {
  1: "用药安全圈",
  2: "疾病进展圈",
  3: "复查窗口圈",
  4: "触达时机圈",
  5: "家庭影响圈",
};

interface PondNode {
  node: RippleNode;
  dimension: string;
  color: string;
  ring: number;
  angle: number;
  r: number;
  label: string;
}

const canvasRef = ref<HTMLCanvasElement | null>(null);
const wrapRef = ref<HTMLDivElement | null>(null);
const hoverLabel = ref("");
const cursorPointer = ref(false);

let ctx: CanvasRenderingContext2D | null = null;
let raf = 0;
let ro: ResizeObserver | null = null;
let W = 0;
let H = 0;
let dpr = 1;
let burstAt = performance.now();
const splashList: Array<{ x: number; y: number; at: number }> = [];

/** ?static=1：渲染单帧完整画面（截图/低性能环境），正常演示保持水波动画。 */
const STATIC_MODE = new URLSearchParams(window.location.search).has("static");

const pondNodes = computed<PondNode[]>(() => {
  const out: PondNode[] = [];
  for (const [dimension, nodes] of Object.entries(props.dimensions)) {
    const meta = DIMENSION_META[dimension];
    if (!meta || nodes.length === 0) continue;
    const ring = Number(nodes[0].ring) || RING_OF_DIM[dimension] || 1;
    const count = nodes.length;
    nodes.forEach((node, i) => {
      const startAngle = ring * 1.35 + (dimension.charCodeAt(0) % 7) * 0.31;
      const angle = startAngle + (i * 2 * Math.PI) / count;
      out.push({
        node,
        dimension,
        color: meta.color,
        ring,
        angle,
        r: 5.5 + (Number(node.intensity) / 100) * 11,
        label: String(node.conflict ?? node.complication ?? node.item ?? node.event ?? node.attention ?? ""),
      });
    });
  }
  return out;
});

const topRiskLabels = computed(() => new Set((props.intensity?.topRisks ?? []).map((t) => t.label)));

/** Top 风险标签是"药+冲突"组合词，节点是短名（如"饮酒"）——双向包含即命中。 */
function isTopRisk(n: PondNode): boolean {
  if (!n.label) return false;
  if (topRiskLabels.value.has(n.label)) return true;
  for (const t of topRiskLabels.value) {
    if (t.includes(n.label) || n.label.includes(t)) return true;
  }
  return false;
}

function ringRadius(ring: number): number {
  const R = Math.min(W, H) / 2 - 34;
  return R * (0.24 + 0.185 * (ring - 1));
}

function nodePos(n: PondNode, cx: number, cy: number): { x: number; y: number } {
  const rad = ringRadius(n.ring);
  return { x: cx + rad * Math.cos(n.angle), y: cy + rad * Math.sin(n.angle) };
}

function easeOutCubic(p: number): number {
  return 1 - Math.pow(1 - p, 3);
}

function draw() {
  try {
    drawFrame();
  } catch (e) {
    (window as any).__pondErr = String((e as Error)?.stack ?? e);
  }
  if (!STATIC_MODE) raf = requestAnimationFrame(draw);
}

function drawFrame() {
  const c = canvasRef.value;
  if (!c || !ctx) return;
  const cx = W / 2;
  const cy = H / 2;
  const now = performance.now();
  const t = now / 1000;
  const maxR = ringRadius(5) + 26;
  const ink = "200,224,250"; // 夜航墨：纸白墨线在深底上发光

  ctx.clearRect(0, 0, W, H);

  // 池心水光：极淡的青瓷辉光随呼吸起伏（夜水微光）
  const glow = 0.035 + 0.02 * Math.sin(t * 0.7);
  const water = ctx.createRadialGradient(cx, cy, 0, cx, cy, maxR * 0.9);
  water.addColorStop(0, `rgba(56,207,232,${glow.toFixed(3)})`);
  water.addColorStop(0.55, `rgba(56,207,232,${(glow * 0.4).toFixed(3)})`);
  water.addColorStop(1, "rgba(56,207,232,0)");
  ctx.fillStyle = water;
  ctx.fillRect(0, 0, W, H);

  // 环层导轨
  for (let ring = 1; ring <= 5; ring++) {
    const rad = ringRadius(ring);
    const active = ring <= (props.intensity?.radius ?? 0);
    ctx.beginPath();
    ctx.arc(cx, cy, rad, 0, Math.PI * 2);
    ctx.strokeStyle = active ? `rgba(${ink},0.3)` : `rgba(${ink},0.1)`;
    ctx.lineWidth = 1;
    ctx.setLineDash(active ? [] : [2, 7]);
    ctx.stroke();
    ctx.setLineDash([]);
    // 环名标签（右上 45°）
    ctx.font = "10px 'Cascadia Code', Consolas, monospace";
    const lx = cx + rad * 0.7071 + 4;
    const ly = cy - rad * 0.7071 - 4;
    ctx.lineWidth = 3.5;
    ctx.strokeStyle = "rgba(20,17,11,0.92)";
    const label = `R${ring} ${RING_NAMES[ring]}`;
    ctx.strokeText(label, lx, ly);
    ctx.fillStyle = active ? "rgba(213,206,184,0.98)" : "rgba(124,118,98,0.95)";
    ctx.fillText(label, lx, ly);
  }

  // 环境涟漪：三道循环扩散的水波
  for (let k = 0; k < 3; k++) {
    const period = 6.5;
    const p = ((t * 1 + k / 3) % 1);
    const rad = p * maxR;
    const alpha = 0.20 * (1 - p);
    ctx.beginPath();
    ctx.arc(cx, cy, rad, 0, Math.PI * 2);
    ctx.strokeStyle = `rgba(${ink},${alpha.toFixed(3)})`;
    ctx.lineWidth = 1.1;
    ctx.stroke();
  }

  // 石落水开：推演爆发涟漪
  const burstAge = (now - burstAt) / 1500;
  if (burstAge >= 0 && burstAge <= 1.6) {
    const p = Math.min(1, burstAge);
    const rad = easeOutCubic(p) * maxR;
    ctx.beginPath();
    ctx.arc(cx, cy, rad, 0, Math.PI * 2);
    ctx.strokeStyle = `rgba(${ink},${(0.5 * (1 - p)).toFixed(3)})`;
    ctx.lineWidth = 2.4;
    ctx.stroke();
    if (p < 0.5) {
      ctx.beginPath();
      ctx.arc(cx, cy, easeOutCubic(p * 2) * maxR * 0.7, 0, Math.PI * 2);
      ctx.strokeStyle = `rgba(${ink},${(0.25 * (1 - p * 2)).toFixed(3)})`;
      ctx.lineWidth = 1.4;
      ctx.stroke();
    }
  }

  // 节点墨滴：随涟漪抵达逐环浮现
  for (const n of pondNodes.value) {
    const { x, y } = nodePos(n, cx, cy);
    const delay = 320 + (n.ring - 1) * 240;
    const reveal = Math.max(0, Math.min(1, ((now - burstAt) - delay) / 480));
    if (reveal <= 0) continue;
    const pop = easeOutCubic(reveal);
    const rr = n.r * (0.6 + 0.4 * pop);
    const high = Number(n.node.intensity) >= 70;
    const pulse = high ? 0.5 + 0.5 * Math.sin(t * 2.2 + n.angle * 3) : 0;

    if (high) {
      ctx.beginPath();
      ctx.arc(x, y, rr + 5 + pulse * 3, 0, Math.PI * 2);
      ctx.strokeStyle = hexA(n.color, 0.28 + pulse * 0.22);
      ctx.lineWidth = 1;
      ctx.setLineDash([7, 9]);
      ctx.stroke();
      ctx.setLineDash([]);
    }
    // 墨滴：外圈色环 + 夜色留底 + 发光色芯
    ctx.beginPath();
    ctx.arc(x, y, rr, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(10,22,40,0.94)";
    ctx.fill();
    ctx.strokeStyle = hexA(n.color, 0.95);
    ctx.lineWidth = 1.8;
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(x, y, Math.max(2, rr * 0.34), 0, Math.PI * 2);
    ctx.fillStyle = hexA(n.color, 0.95);
    ctx.fill();

    if (isTopRisk(n)) {
      ctx.font = "600 10px 'Cascadia Code', Consolas, monospace";
      const tw = ctx.measureText(n.label).width;
      const chipW = tw + 14;
      // 避让中央墨石：标签矩形与石盘相交时沿左右方向推开
      const stoneEdge = Math.min(W, H) * 0.082 + 6;
      let chipCx = x;
      const dx = chipCx - cx;
      if (Math.abs(dx) < stoneEdge + chipW / 2) {
        chipCx = cx + (dx >= 0 ? 1 : -1) * (stoneEdge + chipW / 2 + 4);
      }
      const chipY = y + rr + 8;
      ctx.fillStyle = "rgba(10,22,40,0.95)";
      ctx.strokeStyle = n.color;
      ctx.lineWidth = 1;
      roundRect(chipCx - chipW / 2, chipY, chipW, 16, 4);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = n.color;
      ctx.fillText(n.label, chipCx - chipW / 2 + 7, chipY + 11.5);
    }
  }

  // 健康事件墨石（中心，有机圆缘：比夜更深的墨，纸白描边）
  const stoneR = Math.min(W, H) * 0.082;
  ctx.fillStyle = "rgba(4,10,20,0.97)";
  ctx.beginPath();
  ctx.arc(cx, cy, stoneR, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(cx - stoneR * 0.4, cy + stoneR * 0.25, stoneR * 0.55, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(cx + stoneR * 0.42, cy - stoneR * 0.2, stoneR * 0.5, 0, Math.PI * 2);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(cx, cy, stoneR + 1.6, 0, Math.PI * 2);
  ctx.strokeStyle = `rgba(${ink},0.34)`;
  ctx.lineWidth = 1;
  ctx.stroke();
  ctx.fillStyle = "rgba(246,243,232,0.6)";
  ctx.font = "10px sans-serif";
  ctx.textAlign = "center";
  ctx.fillText("健康事件", cx, cy - 9);
  ctx.fillStyle = "#dbe9f9";
  ctx.font = `700 ${Math.max(12, stoneR * 0.34)}px 'Noto Serif SC', SimSun, serif`;
  const diag = props.healthEvent.diagnosis || "—";
  ctx.fillText(diag.length > 7 ? diag.slice(0, 6) + "…" : diag, cx, cy + 12);
  ctx.textAlign = "start";

  // 节点溅开的小涟漪
  for (let i = splashList.length - 1; i >= 0; i--) {
    const s = splashList[i];
    const p = (now - s.at) / 700;
    if (p >= 1) {
      splashList.splice(i, 1);
      continue;
    }
    ctx.beginPath();
    ctx.arc(s.x, s.y, easeOutCubic(p) * 34, 0, Math.PI * 2);
    ctx.strokeStyle = `rgba(${ink},${(0.4 * (1 - p)).toFixed(3)})`;
    ctx.lineWidth = 1.2;
    ctx.stroke();
  }

  // 选中节点：双环强调
  if (props.selected) {
    const sel = pondNodes.value.find((n) => n.node === props.selected);
    if (sel) {
      const { x, y } = nodePos(sel, cx, cy);
      ctx.beginPath();
      ctx.arc(x, y, sel.r + 4, 0, Math.PI * 2);
      ctx.strokeStyle = sel.color;
      ctx.lineWidth = 2.2;
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(x, y, sel.r + 9, 0, Math.PI * 2);
      ctx.strokeStyle = hexA(sel.color, 0.4);
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }

}

function roundRect(x: number, y: number, w: number, h: number, r: number) {
  if (!ctx) return;
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function hexA(hex: string, alpha: number): string {
  const v = hex.replace("#", "");
  const r = parseInt(v.slice(0, 2), 16);
  const g = parseInt(v.slice(2, 4), 16);
  const b = parseInt(v.slice(4, 6), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

function resize() {
  const c = canvasRef.value;
  const wrap = wrapRef.value;
  if (!c || !wrap) return;
  dpr = window.devicePixelRatio || 1;
  W = wrap.clientWidth;
  H = Math.max(500, Math.min(660, W * 0.62));
  c.width = Math.round(W * dpr);
  c.height = Math.round(H * dpr);
  c.style.height = H + "px";
  ctx = c.getContext("2d");
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0);
  if (STATIC_MODE && ctx) draw();
}

function hitTest(ev: MouseEvent): { node: PondNode; x: number; y: number } | null {
  const c = canvasRef.value;
  if (!c) return null;
  const rect = c.getBoundingClientRect();
  const mx = ev.clientX - rect.left;
  const my = ev.clientY - rect.top;
  const cx = W / 2;
  const cy = H / 2;
  let best: { node: PondNode; x: number; y: number; d: number } | null = null;
  for (const n of pondNodes.value) {
    const { x, y } = nodePos(n, cx, cy);
    const d = Math.hypot(mx - x, my - y);
    if (d <= Math.max(14, n.r + 7) && (!best || d < best.d)) best = { node: n, x, y, d };
  }
  return best;
}

function onClick(ev: MouseEvent) {
  const hit = hitTest(ev);
  if (hit) {
    splashList.push({ x: hit.x, y: hit.y, at: performance.now() });
    emit("select", hit.node.node);
  }
}

function onMove(ev: MouseEvent) {
  const hit = hitTest(ev);
  hoverLabel.value = hit ? `${hit.node.label} · 强度 ${hit.node.node.intensity}` : "";
  cursorPointer.value = !!hit;
}

// 新推演到达 → 石落水开
watch(
  () => pondNodes.value.map((n) => n.node.intensity).join(",") + "|" + (props.healthEvent.diagnosis ?? ""),
  () => {
    if (STATIC_MODE) {
      draw();
      return;
    }
    burstAt = performance.now();
  },
);

onMounted(() => {
  if (STATIC_MODE) burstAt = performance.now() - 10_000;
  resize();
  // 防节流加固：挂载后先同步画一帧（后台标签 rAF 冻结时也有内容），回到可见时重绘
  if (ctx) drawFrame();
  document.addEventListener("visibilitychange", onVisibility);
  ro = new ResizeObserver(() => resize());
  if (wrapRef.value) ro.observe(wrapRef.value);
  raf = requestAnimationFrame(draw);
});

function onVisibility() {
  if (!document.hidden) {
    resize();
    if (ctx) drawFrame();
  }
}

onBeforeUnmount(() => {
  cancelAnimationFrame(raf);
  ro?.disconnect();
  document.removeEventListener("visibilitychange", onVisibility);
});
</script>

<template>
  <div ref="wrapRef" class="pond-wrap" :style="{ cursor: cursorPointer ? 'pointer' : 'default' }">
    <canvas ref="canvasRef" class="pond" @click="onClick" @mousemove="onMove" @mouseleave="hoverLabel = ''" />
    <div v-if="hoverLabel" class="pond-tip mono">{{ hoverLabel }}</div>
    <div class="pond-legend">
      <span v-for="(meta, dim) in DIMENSION_META" :key="dim" class="legend-item">
        <i :style="{ background: meta.color }" />{{ meta.label }}
      </span>
      <span class="legend-size">墨滴大小 ∝ 涟漪强度 · 朱砂环 = 高风险 · 点击查看评分依据</span>
    </div>
  </div>
</template>

<style scoped>
.pond-wrap { position: relative; display: flex; flex-direction: column; align-items: center; gap: 8px; min-width: 0; }
.pond { width: 100%; display: block; }
.pond-tip {
  position: absolute;
  top: 10px; left: 12px;
  font-size: 11px;
  color: var(--ink-soft);
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  padding: 3px 9px;
  pointer-events: none;
  box-shadow: 0 2px 8px rgba(48, 42, 24, 0.1);
}
.pond-legend { display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; align-items: center; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; color: var(--ink-soft); }
.legend-item i { width: 9px; height: 9px; border-radius: 50%; display: inline-block; }
.legend-size { font-size: 10.5px; color: var(--faint); font-family: var(--font-mono); }
</style>
