<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { DIMENSION_META, type RippleDimensions, type RippleIntensity, type RippleNode } from "../types";

/**
 * 器官涟漪图 ORGAN MAP（第十一轮创新）：
 * 把五维涟漪节点按医学语义映射到人体器官上——
 *   低血糖→心脏 · 视网膜病变→眼 · 药物性肝损/饮酒→肝 · 酮症→胰腺 ·
 *   糖尿病足→足 · 卒中/晨峰→脑 · 肾功能→肾 · 哮喘→肺。
 * 器官辉光 ∝ 该器官关联风险强度；红色脉冲=高强度热点；标注浮窗可点击。
 * 全程序化 canvas 绘制（无图片依赖，离线可用），挂载即同步首帧（防 rAF 节流）。
 */
const props = defineProps<{
  dimensions: RippleDimensions;
  healthEvent: { diagnosis: string; drugs: string[]; pastHistory: string };
  intensity: RippleIntensity | null | undefined;
  selected: RippleNode | null;
}>();
const emit = defineEmits<{ select: [node: RippleNode] }>();

/** ?static=1 渲染单帧（截图/低性能）。 */
const STATIC_MODE = new URLSearchParams(window.location.search).has("static");

interface OrganDef {
  key: string;
  x: number; y: number;   // 画布逻辑坐标（460×660 空间）
  r: number;              // 基础辉光半径
  label: string;
  keywords: string[];
}

/** 器官解剖位（逻辑坐标）+ 病症关键词（匹配顺序=特异性从高到低）。 */
const ORGANS: OrganDef[] = [
  { key: "eye",      x: 200, y: 56,  r: 10, label: "眼 · 视网膜",       keywords: ["视网膜", "视力", "飞蚊", "眼科", "白内障"] },
  { key: "brain",    x: 200, y: 46,  r: 20, label: "脑 · 脑血管",       keywords: ["卒中", "脑", "晕厥", "神经", "意识", "头晕", "头痛", "晨峰", "认知"] },
  { key: "heart",    x: 216, y: 172, r: 22, label: "心脏",             keywords: ["心梗", "心肌", "心律", "心衰", "冠心", "胸痛", "胸闷", "血压", "强心"] },
  { key: "lung",     x: 182, y: 158, r: 20, label: "肺 · 呼吸",         keywords: ["哮", "喘", "呼吸", "肺", "咳嗽", "低氧"] },
  { key: "liver",    x: 176, y: 214, r: 20, label: "肝 · 代谢",         keywords: ["肝", "饮酒", "酒", "酒精", "他汀", "转氨酶"] },
  { key: "pancreas", x: 224, y: 216, r: 15, label: "胰腺 · 血糖",       keywords: ["血糖", "胰岛", "糖化", "酮症", "二甲双胍", "降糖"] },
  { key: "kidney",   x: 200, y: 252, r: 18, label: "肾 · 泌尿",         keywords: ["肾", "尿", "肌酐", "白蛋白", "透析"] },
  { key: "stomach",  x: 200, y: 186, r: 14, label: "胃 · 消化",         keywords: ["胃", "消化", "恶心", "呕吐", "腹泻", "腹痛", "食欲"] },
  { key: "foot",     x: 178, y: 552, r: 14, label: "足 · 下肢",         keywords: ["足", "脚", "下肢", "伤口不愈", "截肢"] },
];

/** 节点文本 → 器官（特异性优先；未命中按维度兜底）。 */
function organOfNode(node: RippleNode): OrganDef | null {
  const text = [node.conflict, node.complication, node.item, node.event, node.attention, node.advice, node.risk, node.signal]
    .map((v) => String(v ?? ""))
    .join(" ");
  for (const o of ORGANS) {
    if (o.keywords.some((k) => text.includes(k))) return o;
  }
  const dim = String(node.dimension ?? "");
  const fallback: Record<string, string> = {
    drugLifestyleConflicts: "liver",
    complicationSignals: "heart",
    recheckWindows: "kidney",
    chronoTriggers: "brain",
  };
  return ORGANS.find((o) => o.key === fallback[dim]) ?? null;
}

interface OrganHot {
  organ: OrganDef;
  sum: number;
  max: number;
  count: number;
  topNode: RippleNode;
  topLabel: string;
}

const canvasRef = ref<HTMLCanvasElement | null>(null);
const wrapRef = ref<HTMLDivElement | null>(null);
const hoverInfo = ref("");
const cursorPointer = ref(false);

let ctx: CanvasRenderingContext2D | null = null;
let raf = 0;
let ro: ResizeObserver | null = null;
let W = 0;
let H = 0;
let dpr = 1;

/** 器官聚合：遍历五维节点 → 器官 {强度和/最大强度/计数/最高节点}。 */
const organHots = computed<Map<string, OrganHot>>(() => {
  const map = new Map<string, OrganHot>();
  for (const [dimension, nodes] of Object.entries(props.dimensions)) {
    for (const node of nodes) {
      const organ = organOfNode({ ...node, dimension });
      if (!organ) continue;
      const intensity = Number(node.intensity) || 0;
      const label = String(node.conflict ?? node.complication ?? node.item ?? node.event ?? node.attention ?? node.signal ?? "风险");
      const hot = map.get(organ.key);
      if (hot) {
        hot.sum += intensity;
        hot.count += 1;
        if (intensity > hot.max) { hot.max = intensity; hot.topNode = node; hot.topLabel = label; }
      } else {
        map.set(organ.key, { organ, sum: intensity, max: intensity, count: 1, topNode: node, topLabel: label });
      }
    }
  }
  return map;
});

/** 家属注意事项（不落器官，画外圈光环节点）。 */
const familyNodes = computed<RippleNode[]>(() => props.dimensions.familyAttentions ?? []);

/** 标注浮窗取强度最高的 2 个器官热点。 */
const callouts = computed<OrganHot[]>(() => {
  return [...organHots.value.values()].sort((a, b) => b.sum - a.sum).slice(0, 2);
});

function heatColor(sum: number, max: number): string {
  if (max >= 70 || sum >= 150) return "#f4695c";
  if (max >= 40 || sum >= 80) return "#f0a45c";
  return "#38cfe8";
}

/* ================= 绘制 ================= */

let scale = 1;
let ox = 0;
let oy = 0;

function toCanvas(x: number, y: number): { x: number; y: number } {
  return { x: ox + x * scale, y: oy + y * scale };
}
function P(x: number, y: number): [number, number] {
  const p = toCanvas(x, y);
  return [p.x, p.y];
}

function draw() {
  try {
    drawFrame();
  } catch (e) {
    (window as any).__organErr = String((e as Error)?.stack ?? e);
  }
  if (!STATIC_MODE) raf = requestAnimationFrame(draw);
}

function drawFrame() {
  const c = canvasRef.value;
  if (!c || !ctx || !W || !H) return;
  const now = performance.now();
  const t = now / 1000;
  ctx.clearRect(0, 0, W, H);

  scale = Math.min(W / 500, H / 700);
  ox = W / 2 - 230 * scale;
  oy = H / 2 - 330 * scale;

  const hots = organHots.value;

  // 1. 底座网格圆盘（脚下，缓慢旋转的虚线）
  const base = toCanvas(230, 600);
  for (let i = 1; i <= 3; i++) {
    ctx.beginPath();
    ctx.ellipse(base.x, base.y, 150 * scale * (i / 3), 34 * scale * (i / 3), 0, 0, Math.PI * 2);
    ctx.strokeStyle = `rgba(56, 191, 248, ${0.14 - i * 0.035})`;
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 8]);
    ctx.lineDashOffset = -t * 12 * i;
    ctx.stroke();
    ctx.setLineDash([]);
  }

  // 2. 人体轮廓（全息描边）
  ctx.save();
  ctx.strokeStyle = "rgba(56, 191, 248, 0.4)";
  ctx.fillStyle = "rgba(56, 191, 248, 0.045)";
  ctx.lineWidth = 1.6;
  ctx.shadowColor = "rgba(56, 191, 248, 0.5)";
  ctx.shadowBlur = 14;
  ctx.beginPath();
  // 头
  const head = P(200, 48);
  ctx.arc(head[0], head[1], 30 * scale, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();
  ctx.beginPath();
  // 颈+躯干+腿（简化剪影）
  const torso: [number, number][] = [
    P(200, 78), P(200, 96), P(148, 118), P(132, 200), P(140, 268),
    P(158, 322), P(170, 470), P(166, 596), P(198, 596), P(200, 470),
    P(202, 470), P(204, 596), P(236, 596), P(232, 470), P(244, 322),
    P(262, 268), P(270, 200), P(254, 118), P(200, 96),
  ];
  torso.forEach(([x, y], i) => (i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)));
  ctx.closePath();
  ctx.fill();
  ctx.stroke();
  // 手臂
  ctx.beginPath();
  const armL: [number, number][] = [P(148, 122), P(120, 190), P(108, 268), P(112, 330)];
  const armR: [number, number][] = [P(254, 122), P(282, 190), P(294, 268), P(290, 330)];
  armL.concat(armR).forEach(([x, y], i) => (i === 0 ? ctx.moveTo(x, y) : i === 4 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)));
  ctx.stroke();
  ctx.restore();

  // 3. 脊柱中线 + 血管流动虚线（数据流动感）
  ctx.save();
  ctx.strokeStyle = "rgba(56, 191, 248, 0.35)";
  ctx.lineWidth = 1.2;
  ctx.setLineDash([2, 10]);
  ctx.lineDashOffset = -t * 30;
  ctx.beginPath();
  const sTop = P(200, 96);
  const sBot = P(200, 320);
  ctx.moveTo(sTop[0], sTop[1]);
  ctx.lineTo(sBot[0], sBot[1]);
  ctx.stroke();
  ctx.setLineDash([]);
  ctx.restore();

  // 4. 器官辉光（呼吸 + 强度着色）
  for (const hot of hots.values()) {
    const { organ } = hot;
    const breathe = 0.82 + 0.18 * Math.sin(t * 1.6 + organ.x);
    const color = heatColor(hot.sum, hot.max);
    const pos = toCanvas(organ.x, organ.y);
    const radius = organ.r * scale * (1 + hot.count * 0.08);
    const rgb = hexRgb(color);
    const g = ctx.createRadialGradient(pos.x, pos.y, 0, pos.x, pos.y, radius * 2.4 * breathe);
    g.addColorStop(0, `rgba(${rgb}, ${0.5 * breathe})`);
    g.addColorStop(0.5, `rgba(${rgb}, ${0.2 * breathe})`);
    g.addColorStop(1, `rgba(${rgb}, 0)`);
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, radius * 2.4 * breathe, 0, Math.PI * 2);
    ctx.fill();
    // 核
    ctx.fillStyle = `rgba(${rgb}, 0.9)`;
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, Math.max(3, radius * 0.32), 0, Math.PI * 2);
    ctx.fill();
    // 高强度脉冲环
    if (hot.max >= 70 || hot.sum >= 150) {
      const pulse = (t * 0.9 + organ.x * 0.01) % 1;
      ctx.strokeStyle = `rgba(244, 105, 92, ${(1 - pulse) * 0.7})`;
      ctx.lineWidth = 1.6;
      ctx.beginPath();
      ctx.arc(pos.x, pos.y, radius * (0.6 + pulse * 1.6), 0, Math.PI * 2);
      ctx.stroke();
    }
  }

  // 5. 标注浮窗（TOP2 器官：引线 + 概率卡，参照 Pulmonary Analysis）
  for (const hot of callouts.value) {
    const color = heatColor(hot.sum, hot.max);
    const pos = toCanvas(hot.organ.x, hot.organ.y);
    const side = hot.organ.x <= 200 ? -1 : 1;
    const cardW = 148 * scale;
    const cardH = 52 * scale;
    const cardX = pos.x + side * (60 * scale) - (side < 0 ? cardW : 0);
    const cardY = pos.y - 30 * scale;
    // 引线
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.2;
    ctx.globalAlpha = 0.75;
    ctx.beginPath();
    ctx.moveTo(pos.x, pos.y);
    ctx.lineTo(cardX + (side < 0 ? cardW : 0), cardY + cardH / 2);
    ctx.stroke();
    // 热点圆
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, 4 * scale, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1;
    // 卡片
    ctx.fillStyle = "rgba(8, 17, 32, 0.92)";
    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    roundRect(cardX, cardY, cardW, cardH, 8 * scale);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#e8f1fb";
    ctx.font = `600 ${Math.max(11, 12 * scale)}px ${"\"Microsoft YaHei\", sans-serif"}`;
    ctx.textAlign = "left";
    const label = hot.topLabel.length > 12 ? hot.topLabel.slice(0, 11) + "…" : hot.topLabel;
    ctx.fillText(label, cardX + 10 * scale, cardY + 19 * scale);
    ctx.font = `${Math.max(10, 11 * scale)}px "Cascadia Code", monospace`;
    ctx.fillStyle = color;
    ctx.fillText(`${hot.organ.label} · ${hot.topNode.intensity}`, cardX + 10 * scale, cardY + cardH - 10 * scale);
  }

  // 6. 家属注意事项：体外光环节点
  for (const node of familyNodes.value) {
    const ang = (Number(node.intensity) / 100) * Math.PI + t * 0.05;
    const rx = 260 * scale;
    const pos = toCanvas(200 + Math.cos(ang) * 260 * 1.15, 300 + Math.sin(ang) * 120);
    ctx.fillStyle = "rgba(79, 209, 165, 0.85)";
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, 3.2 * scale, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "rgba(79, 209, 165, 0.3)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, (6 + Math.sin(t * 2 + ang) * 2) * scale, 0, Math.PI * 2);
    ctx.stroke();
  }

  // 7. 上升粒子（沿躯体，数据流动感）
  const particles = 14;
  for (let i = 0; i < particles; i++) {
    const seed = i * 137.5;
    const px = 150 + ((seed * 7.3) % 110);
    const speed = 22 + (i % 5) * 7;
    const py = 580 - ((t * speed + seed * 11) % 480);
    const a = 0.5 * Math.sin((Math.PI * py) / 580) ** 2;
    const pos = toCanvas(px, py);
    ctx.fillStyle = `rgba(125, 211, 252, ${a.toFixed(3)})`;
    ctx.beginPath();
    ctx.arc(pos.x, pos.y, 1.6 * scale, 0, Math.PI * 2);
    ctx.fill();
  }

  // 8. 选中器官：双环强调
  if (props.selected) {
    const organ = organOfNode({ ...props.selected, dimension: String(props.selected.dimension ?? "") });
    if (organ) {
      const pos = toCanvas(organ.x, organ.y);
      ctx.strokeStyle = "#7dd3fc";
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(pos.x, pos.y, organ.r * scale + 6, 0, Math.PI * 2);
      ctx.stroke();
      ctx.strokeStyle = "rgba(125, 211, 252, 0.4)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(pos.x, pos.y, organ.r * scale + 12, 0, Math.PI * 2);
      ctx.stroke();
    }
  }
}

function hexRgb(hex: string): string {
  const v = hex.replace("#", "");
  const r = parseInt(v.slice(0, 2), 16);
  const g = parseInt(v.slice(2, 4), 16);
  const b = parseInt(v.slice(4, 6), 16);
  return `${r},${g},${b}`;
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

/* ================= 交互 ================= */

function hitOrgan(mx: number, my: number): OrganHot | null {
  for (const hot of organHots.value.values()) {
    const pos = toCanvas(hot.organ.x, hot.organ.y);
    if (Math.hypot(mx - pos.x, my - pos.y) <= Math.max(16, hot.organ.r * scale)) return hot;
  }
  return null;
}

function onClick(ev: MouseEvent) {
  const c = canvasRef.value;
  if (!c) return;
  const rect = c.getBoundingClientRect();
  const hot = hitOrgan(ev.clientX - rect.left, ev.clientY - rect.top);
  if (hot) emit("select", hot.topNode);
}

function onMove(ev: MouseEvent) {
  const c = canvasRef.value;
  if (!c) return;
  const rect = c.getBoundingClientRect();
  const hot = hitOrgan(ev.clientX - rect.left, ev.clientY - rect.top);
  cursorPointer.value = !!hot;
  hoverInfo.value = hot
    ? `${hot.organ.label} · ${hot.count} 项关联 · 强度和 ${hot.sum.toFixed(0)} · 点击查看依据`
    : "";
}

/* ================= 生命周期（防节流：同步首帧+可见性重绘） ================= */

function resize() {
  const c = canvasRef.value;
  const wrap = wrapRef.value;
  if (!c || !wrap) return;
  const w = wrap.clientWidth;
  (window as any).__organResizeN = ((window as any).__organResizeN || 0) + 1;
  (window as any).__organLastW = w;
  if (w < 40) return; // 布局未就绪：保持旧画布，等 RO 下一次触发
  dpr = window.devicePixelRatio || 1;
  W = w;
  H = Math.max(520, Math.min(680, W * 0.9));
  c.width = Math.round(W * dpr);
  c.height = Math.round(H * dpr);
  c.style.height = H + "px";
  ctx = c.getContext("2d");
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0);
  try {
    drawFrame();
  } catch (e) {
    (window as any).__organErr = String((e as Error)?.stack ?? e);
  }
}

function onVisibility() {
  if (!document.hidden && ctx) drawFrame();
}

watch(organHots, () => {
  if (ctx) drawFrame();
}, { deep: false });

onMounted(() => {
  (window as any).__organMounted = (window as any).__organMounted || 0;
  (window as any).__organMounted += 1;
  document.addEventListener("visibilitychange", onVisibility);
  ro = new ResizeObserver(() => resize());
  if (wrapRef.value) ro.observe(wrapRef.value);
  // 布局兜底：后台标签的 rAF/RO 通知会被冻结，setInterval 不受影响——
  // 每 300ms 补量一次直到拿到真实宽度（拿到后自动停）。
  const backoff = window.setInterval(() => {
    resize();
    if (W > 0) window.clearInterval(backoff);
  }, 300);
  onBeforeUnmount(() => window.clearInterval(backoff));
  raf = requestAnimationFrame(draw);
});

onBeforeUnmount(() => {
  cancelAnimationFrame(raf);
  ro?.disconnect();
  document.removeEventListener("visibilitychange", onVisibility);
});
</script>

<template>
  <div ref="wrapRef" class="omap-wrap" :style="{ cursor: cursorPointer ? 'pointer' : 'default' }">
    <canvas ref="canvasRef" class="omap" @click="onClick" @mousemove="onMove" @mouseleave="hoverInfo = ''" />
    <div v-if="hoverInfo" class="omap-tip mono">{{ hoverInfo }}</div>
    <div class="omap-legend">
      <span>器官辉光 ∝ 该器官关联风险强度</span>
      <span class="sep">·</span>
      <span style="color:#f4695c">红色脉冲 = 高强度热点</span>
      <span class="sep">·</span>
      <span>点击器官查看节点评分依据</span>
      <span class="sep">·</span>
      <span class="legend-note">病症按医学语义映射器官，未命中项按维度归类</span>
    </div>
  </div>
</template>

<style scoped>
.omap-wrap { position: relative; display: flex; flex-direction: column; align-items: center; gap: 8px; min-width: 0; }
.omap { width: 100%; display: block; }
.omap-tip {
  position: absolute;
  top: 10px; left: 12px;
  font-size: 11px;
  color: var(--ink-soft);
  background: var(--card);
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  padding: 3px 9px;
  pointer-events: none;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
}
.omap-legend { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; align-items: center; font-size: 11px; color: var(--ink-soft); }
.omap-legend .sep { color: var(--faint); }
.legend-note { color: var(--faint); font-size: 10px; }
</style>
