<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { DIMENSION_META, type RippleDimensions, type RippleIntensity, type RippleNode } from "../types";

/**
 * 器官涟漪图 ORGAN MAP · 3D 全息投影版（第十一轮）：
 * - 人体 = 三维点云/线框（躯干截面放样 + 头部球 + 四肢胶囊），绕纵轴缓慢自转
 * - 器官 = 3D 发光点云球（医学语义映射：低血糖→心、视网膜→眼、药物肝损→肝…）
 * - 高强度器官 = 红色脉冲热点 + 引线标注浮窗；血管分支沿心/肺分布
 * - 底部投影光锥 + 旋转基座 + 全身扫描光带（全息投影感）
 * - 全程序化 canvas（无图片、离线可用）；挂载即同步首帧（防后台 rAF 节流）
 * - 点击器官 → 弹出该器官最高风险节点的评分依据抽屉
 */
const props = defineProps<{
  dimensions: RippleDimensions;
  healthEvent: { diagnosis: string; drugs: string[]; pastHistory: string };
  intensity: RippleIntensity | null | undefined;
  selected: RippleNode | null;
}>();
const emit = defineEmits<{ select: [node: RippleNode] }>();

const STATIC_MODE = new URLSearchParams(window.location.search).has("static");

/* ============ 器官定义与映射 ============ */

interface OrganDef {
  key: string;
  x: number; y: number; z: number;  // 3D 解剖位（y 向上）
  r: number;
  label: string;
  keywords: string[];
}

const ORGANS: OrganDef[] = [
  { key: "eye",      x: 12,  y: 578, z: 26,  r: 9,  label: "眼 · 视网膜", keywords: ["视网膜", "视力", "飞蚊", "眼科", "白内障"] },
  { key: "brain",    x: 0,   y: 584, z: 0,   r: 20, label: "脑 · 脑血管", keywords: ["卒中", "脑", "晕厥", "神经", "意识", "头晕", "头痛", "晨峰", "认知"] },
  { key: "heart",    x: 16,  y: 428, z: 10,  r: 24, label: "心脏",       keywords: ["心梗", "心肌", "心律", "心衰", "冠心", "胸痛", "胸闷", "血压", "心悸"] },
  { key: "lungL",    x: -34, y: 438, z: 0,   r: 26, label: "肺 · 呼吸",   keywords: ["哮", "喘", "呼吸", "肺", "咳嗽", "低氧"] },
  { key: "lungR",    x: 34,  y: 438, z: 0,   r: 26, label: "肺 · 呼吸",   keywords: ["哮", "喘", "呼吸", "肺", "咳嗽", "低氧"] },
  { key: "liver",    x: -28, y: 348, z: 8,   r: 22, label: "肝 · 代谢",   keywords: ["肝", "饮酒", "酒", "酒精", "他汀", "转氨酶"] },
  { key: "pancreas", x: 22,  y: 336, z: 0,   r: 16, label: "胰腺 · 血糖", keywords: ["血糖", "胰岛", "糖化", "酮症", "二甲双胍", "降糖"] },
  { key: "kidney",   x: 0,   y: 292, z: -14, r: 18, label: "肾 · 泌尿",   keywords: ["肾", "尿", "肌酐", "白蛋白", "透析", "造影剂"] },
  { key: "stomach",  x: 4,   y: 372, z: 14,  r: 15, label: "胃 · 消化",   keywords: ["胃", "消化", "恶心", "呕吐", "腹泻", "腹痛", "食欲"] },
  { key: "footL",    x: -40, y: 24,  z: 0,   r: 13, label: "足 · 下肢",   keywords: ["足", "脚", "下肢", "伤口不愈", "截肢"] },
  { key: "footR",    x: 40,  y: 24,  z: 0,   r: 13, label: "足 · 下肢",   keywords: ["足", "脚", "下肢", "伤口不愈", "截肢"] },
];

function organOfNode(node: RippleNode): OrganDef | null {
  const text = [node.conflict, node.complication, node.item, node.event, node.attention, node.advice, node.risk, node.signal]
    .map((v) => String(v ?? ""))
    .join(" ");
  for (const o of ORGANS) {
    if (o.keywords.some((k) => text.includes(k))) return o;
  }
  const fallback: Record<string, string[]> = {
    drugLifestyleConflicts: ["liver"],
    complicationSignals: ["heart"],
    recheckWindows: ["kidney"],
    chronoTriggers: ["brain"],
  };
  const keys = fallback[String(node.dimension ?? "")] ?? [];
  return ORGANS.find((o) => keys.includes(o.key)) ?? null;
}

interface OrganHot {
  key: string;
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
let backoff = 0;
let W = 0;
let H = 0;
let dpr = 1;

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
        map.set(organ.key, { key: organ.key, organ, sum: intensity, max: intensity, count: 1, topNode: node, topLabel: label });
      }
    }
  }
  return map;
});

const familyNodes = computed<RippleNode[]>(() => props.dimensions.familyAttentions ?? []);

const callouts = computed<OrganHot[]>(() => {
  return [...organHots.value.values()].sort((a, b) => b.sum - a.sum).slice(0, 2);
});

function heatRgb(sum: number, max: number): [number, number, number] {
  if (max >= 70 || sum >= 150) return [244, 105, 92];
  if (max >= 40 || sum >= 80) return [240, 164, 92];
  return [56, 207, 232];
}

/* ============ 确定性随机（点云布局稳定） ============ */
function mulberry32(seed: number) {
  return () => {
    seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* ============ 3D 几何构建（一次性） ============ */
type P3 = [number, number, number];
const bodyPoints: P3[] = [];       // 人体点云（暗青）
const vesselLines: P3[][] = [];    // 血管线（折线段）
const vesselRed: boolean[] = [];   // 血管是否红色（心肺）

function buildGeometry() {
  const rnd = mulberry32(20260920);
  bodyPoints.length = 0;
  vesselLines.length = 0;
  vesselRed.length = 0;

  // 躯干：截面放样（椭圆环 → 点环）
  const sections: [number, number, number][] = [
    [486, 124, 60], [452, 119, 58], [418, 111, 53], [384, 100, 49],
    [350, 92, 45], [316, 95, 47], [282, 105, 52],
  ];
  for (const [y, rx, rz] of sections) {
    for (let i = 0; i < 26; i++) {
      const a = (i / 26) * Math.PI * 2;
      const jx = (rnd() - 0.5) * 6, jy = (rnd() - 0.5) * 8, jz = (rnd() - 0.5) * 5;
      bodyPoints.push([Math.cos(a) * rx + jx, y + jy, Math.sin(a) * rz + jz]);
    }
  }
  // 头：球面点云
  for (let i = 0; i < 150; i++) {
    const u = rnd() * Math.PI * 2, v = Math.acos(2 * rnd() - 1);
    const r = 34 * (0.92 + rnd() * 0.1);
    bodyPoints.push([Math.sin(v) * Math.cos(u) * r, 574 + Math.cos(v) * r * 1.12, Math.sin(v) * Math.sin(u) * r]);
  }
  // 颈
  for (let i = 0; i < 26; i++) {
    const a = (i / 26) * Math.PI * 2, y = 494 + rnd() * 30;
    bodyPoints.push([Math.cos(a) * 16, y, Math.sin(a) * 14]);
  }
  // 手臂（两侧胶囊点列）
  for (const side of [-1, 1]) {
    for (let i = 0; i < 46; i++) {
      const f = i / 45;
      const x = side * (108 + f * 48 + (rnd() - 0.5) * 8);
      const y = 462 - f * 300 + (rnd() - 0.5) * 8;
      bodyPoints.push([x, y, (rnd() - 0.5) * 16]);
    }
  }
  // 腿（两侧）
  for (const side of [-1, 1]) {
    for (let i = 0; i < 60; i++) {
      const f = i / 59;
      const x = side * (38 + f * 6 + (rnd() - 0.5) * 8);
      const y = 268 - f * 250 + (rnd() - 0.5) * 8;
      bodyPoints.push([x, y, (rnd() - 0.5) * 20]);
    }
  }

  // 器官点云球
  for (const organ of ORGANS) {
    const n = Math.round(organ.r * 2.6);
    for (let i = 0; i < n; i++) {
      const u = rnd() * Math.PI * 2, v = Math.acos(2 * rnd() - 1);
      const r = organ.r * (0.35 + 0.65 * Math.cbrt(rnd()));
      bodyPoints.push([organ.x + Math.sin(v) * Math.cos(u) * r, organ.y + Math.cos(v) * r, organ.z + Math.sin(v) * Math.sin(u) * r]);
    }
  }

  // 血管：主动脉（心→下）+ 冠状分支（心周围）+ 肺内分支（红）
  const branch = (origin: P3, dir: P3, depth: number, len: number, red: boolean) => {
    if (depth <= 0) return;
    const pts: P3[] = [];
    let [x, y, z] = origin;
    let [dx, dy, dz] = dir;
    for (let i = 0; i < len; i++) {
      pts.push([x, y, z]);
      x += dx * 7 + (rnd() - 0.5) * 6;
      y += dy * 7 + (rnd() - 0.5) * 6;
      z += dz * 7 + (rnd() - 0.5) * 6;
      dx += (rnd() - 0.5) * 0.24; dy += (rnd() - 0.5) * 0.24; dz += (rnd() - 0.5) * 0.24;
    }
    vesselLines.push(pts); vesselRed.push(red);
    if (depth >= 2) branch(pts[len - 1], [dx + (rnd() - 0.5), dy + (rnd() - 0.5), dz + (rnd() - 0.5)], depth - 1, Math.max(3, len - 2), red);
  };
  // 主动脉下行
  branch([16, 418, 6], [0.05, -1, 0.05], 3, 9, false);
  // 冠状动脉（心脏周围红）
  branch([16, 424, 12], [1, -0.2, 0.4], 2, 6, true);
  branch([16, 420, 8], [-0.6, -0.3, 0.5], 2, 6, true);
  // 肺内分支（红，两侧）
  for (const side of [-1, 1]) {
    branch([side * 30, 462, 4], [side * 0.5, -0.7, 0.2], 2, 6, true);
    branch([side * 30, 430, 4], [side * 0.6, 0.4, 0.3], 2, 6, true);
  }
}
let geometryBuilt = false;

/* ============ 3D 投影 ============ */
let yaw = 0;
let fitK = 1;      // 自动取景缩放（每帧按锚点重算，人体始终充满画布）
let fitY = 0;      // 取景纵向偏移
const projOrgans: { key: string; sx: number; sy: number; label: string }[] = [];

function rawProject(p: P3): { x1: number; y1s: number; s: number } {
  const cy = Math.cos(yaw), sy = Math.sin(yaw);
  const x1 = p[0] * cy - p[2] * sy;
  const z1 = p[0] * sy + p[2] * cy;
  const tilt = -0.1;
  const y2 = p[1] * Math.cos(tilt) - z1 * Math.sin(tilt);
  const z2 = p[1] * Math.sin(tilt) + z1 * Math.cos(tilt);
  const s = 900 / (900 + z2 + 320);
  return { x1: x1 * s, y1s: y2 * s, s };
}

const FIT_ANCHORS: P3[] = [
  [0, 0, 0], [0, 600, 0], [90, 486, 0], [-90, 486, 0],
  [120, 150, 0], [-120, 150, 0], [44, 10, 0], [-44, 10, 0], [0, 584, 20],
];

function fitView() {
  // 屏幕 Y 向下、人体 Y 向上：用 -y1s 作为屏幕相对值
  let minY = Infinity, maxY = -Infinity, halfW = 0;
  for (const a of FIT_ANCHORS) {
    const r = rawProject(a);
    const sy = -r.y1s;
    minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
    halfW = Math.max(halfW, Math.abs(r.x1));
  }
  const range = Math.max(1, maxY - minY);
  fitK = Math.min((H * 0.84) / range, (W * 0.8) / (2 * halfW + 1));
  fitY = H * 0.06 - minY * fitK;
}

function project(p: P3): { x: number; y: number; s: number; z: number } {
  const r = rawProject(p);
  return { x: W / 2 + r.x1 * fitK, y: fitY - r.y1s * fitK, s: r.s * fitK, z: 0 };
}
function minRawYFit() { return 0; }
function minRawY() { return 0; }

/* ============ 绘制 ============ */

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
  yaw = t * 0.22;
  if (!geometryBuilt) { buildGeometry(); geometryBuilt = true; }
  fitView();
  ctx.clearRect(0, 0, W, H);

  const hots = organHots.value;
  const cx = W / 2;
  const baseY = H * 0.9;

  // 1. 投影基座：光锥（画面下沿的投影仪射向脚底）+ 脚底旋转椭圆盘
  const feet = project([0, 0, 0]);
  const bodyTop = project([0, 600, 0]);
  ctx.save();
  const cone = ctx.createLinearGradient(cx, H * 1.12, cx, feet.y);
  cone.addColorStop(0, "rgba(56, 191, 248, 0.16)");
  cone.addColorStop(0.55, "rgba(56, 191, 248, 0.06)");
  cone.addColorStop(1, "rgba(56, 191, 248, 0.02)");
  ctx.fillStyle = cone;
  ctx.beginPath();
  ctx.moveTo(cx - 40, H * 1.1);
  ctx.lineTo(cx - 165, feet.y);
  ctx.lineTo(cx + 165, feet.y);
  ctx.lineTo(cx + 40, H * 1.1);
  ctx.closePath();
  ctx.fill();
  for (let i = 1; i <= 3; i++) {
    ctx.beginPath();
    ctx.ellipse(cx, feet.y, 60 + i * 52, (60 + i * 52) * 0.22, 0, 0, Math.PI * 2);
    ctx.strokeStyle = `rgba(56, 191, 248, ${0.22 - i * 0.055})`;
    ctx.lineWidth = 1;
    ctx.setLineDash([5, 9]);
    ctx.lineDashOffset = -t * 14 * i;
    ctx.stroke();
    ctx.setLineDash([]);
  }
  ctx.restore();

  // 2. 人体点云（暗青，深度渐隐）
  ctx.save();
  for (const p of bodyPoints) {
    const pr = project(p);
    const a = Math.max(0.08, Math.min(0.55, (pr.s - 0.62) * 1.8));
    ctx.fillStyle = `rgba(125, 211, 252, ${a.toFixed(3)})`;
    ctx.fillRect(pr.x - 0.8, pr.y - 0.8, 1.7, 1.7);
  }
  ctx.restore();

  // 3. 器官点云（热度着色，亮度呼吸）
  for (const hot of hots.values()) {
    const [r, g, b] = heatRgb(hot.sum, hot.max);
    const breathe = 0.75 + 0.25 * Math.sin(t * 1.7 + hot.organ.x * 0.05);
    const n = Math.round(hot.organ.r * 2.6);
    const rnd = mulberry32(hot.organ.y * 977 + hot.organ.x);
    for (let i = 0; i < n; i++) {
      const u = rnd() * Math.PI * 2, v = Math.acos(2 * rnd() - 1);
      const rr = hot.organ.r * (0.35 + 0.65 * Math.cbrt(rnd()));
      const p: P3 = [hot.organ.x + Math.sin(v) * Math.cos(u) * rr, hot.organ.y + Math.cos(v) * rr, hot.organ.z + Math.sin(v) * Math.sin(u) * rr];
      const pr = project(p);
      const a = Math.min(0.85, (0.35 + hot.max / 160) * breathe);
      ctx.fillStyle = `rgba(${r},${g},${b},${a.toFixed(3)})`;
      ctx.fillRect(pr.x - 1, pr.y - 1, 2.1, 2.1);
    }
  }

  // 4. 血管（折线；心肺红）
  vesselLines.forEach((pts, idx) => {
    const red = vesselRed[idx];
    ctx.strokeStyle = red ? "rgba(244, 105, 92, 0.5)" : "rgba(125, 211, 252, 0.34)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    pts.forEach((p, i) => {
      const pr = project(p);
      i === 0 ? ctx.moveTo(pr.x, pr.y) : ctx.lineTo(pr.x, pr.y);
    });
    ctx.stroke();
  });

  // 5. 器官热点：脉冲扩散环 / 亮核
  projOrgans.length = 0;
  for (const hot of hots.values()) {
    const center = project([hot.organ.x, hot.organ.y, hot.organ.z]);
    projOrgans.push({ key: hot.key, sx: center.x, sy: center.y, label: hot.topLabel });
    const [r, g, b] = heatRgb(hot.sum, hot.max);
    if (hot.max >= 70 || hot.sum >= 150) {
      const pulse = (t * 0.9 + hot.organ.x * 0.02) % 1;
      ctx.strokeStyle = `rgba(244, 105, 92, ${((1 - pulse) * 0.75).toFixed(3)})`;
      ctx.lineWidth = 1.8;
      ctx.beginPath();
      ctx.arc(center.x, center.y, (6 + pulse * 26) * center.s, 0, Math.PI * 2);
      ctx.stroke();
      ctx.fillStyle = "rgba(244, 105, 92, 0.95)";
      ctx.beginPath();
      ctx.arc(center.x, center.y, 3.4 * center.s + 1, 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.fillStyle = `rgba(${r},${g},${b},0.9)`;
      ctx.beginPath();
      ctx.arc(center.x, center.y, 2.6 * center.s + 1, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  // 6. TOP2 标注浮窗：锚点=投影中心，卡片贴左右
  top2Callouts(ctx, t);

  // 7. 家属注意事项：环绕外圈光点
  for (const node of familyNodes.value) {
    const ang = (Number(node.intensity) / 100) * Math.PI * 2 + t * 0.06;
    const p: P3 = [Math.cos(ang) * 150, 330 + Math.sin(t * 0.4 + ang) * 60, Math.sin(ang) * 90];
    const pr = project(p);
    ctx.fillStyle = "rgba(79, 209, 165, 0.85)";
    ctx.beginPath();
    ctx.arc(pr.x, pr.y, 2.6 * pr.s + 0.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "rgba(79, 209, 165, 0.3)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(pr.x, pr.y, (6 + Math.sin(t * 2 + ang) * 2) * pr.s, 0, Math.PI * 2);
    ctx.stroke();
  }

  // 8. 全身扫描光带（上下往复）
  const bodyTopY = Math.min(...projOrgans.map(p => p.sy), bodyTop.y);
  const bodyBotY = Math.max(...projOrgans.map(p => p.sy), feet.y);
  const scanY = bodyTopY + ((t * 0.14) % 1) * (bodyBotY - bodyTopY);
  const scanGrad = ctx.createLinearGradient(0, scanY - 14, 0, scanY + 14);
  scanGrad.addColorStop(0, "rgba(56, 191, 248, 0)");
  scanGrad.addColorStop(0.5, "rgba(56, 191, 248, 0.10)");
  scanGrad.addColorStop(1, "rgba(56, 191, 248, 0)");
  ctx.fillStyle = scanGrad;
  ctx.fillRect(cx - 195, scanY - 14, 390, 28);
  ctx.fillStyle = "rgba(125, 211, 252, 0.5)";
  ctx.fillRect(cx - 195, scanY - 1, 390, 1.4);
}

/** TOP2 标注浮窗（引线 + 卡片） */
function top2Callouts(ctx: CanvasRenderingContext2D, t: number) {
  const top2 = callouts.value;
  top2.forEach((hot, i) => {
    const anchor = projOrgans.find((p) => p.key === hot.key);
    if (!anchor) return;
    const [r, g, b] = heatRgb(hot.sum, hot.max);
    const side = i === 0 ? -1 : 1;
    const cardW = 158, cardH = 54;
    const cardX = side < 0 ? Math.max(10, anchor.sx - 240) : Math.min(W - cardW - 10, anchor.sx + 90);
    const cardY = Math.max(12, Math.min(H - cardH - 12, anchor.sy - 60 + i * 30));
    ctx.globalAlpha = 0.85;
    ctx.strokeStyle = `rgba(${r},${g},${b},0.9)`;
    ctx.lineWidth = 1.1;
    ctx.beginPath();
    ctx.moveTo(anchor.sx, anchor.sy);
    ctx.lineTo(side < 0 ? cardX + cardW : cardX, cardY + cardH / 2);
    ctx.stroke();
    ctx.globalAlpha = 1;
    ctx.fillStyle = "rgba(8, 17, 32, 0.92)";
    ctx.strokeStyle = `rgba(${r},${g},${b},0.85)`;
    roundRect(cardX, cardY, cardW, cardH, 9);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#e8f1fb";
    ctx.font = "600 12.5px 'Microsoft YaHei', sans-serif";
    ctx.textAlign = "left";
    const label = hot.topLabel.length > 12 ? hot.topLabel.slice(0, 11) + "…" : hot.topLabel;
    ctx.fillText(label, cardX + 12, cardY + 20);
    ctx.font = "11px 'JetBrains Mono', monospace";
    ctx.fillStyle = `rgba(${r},${g},${b},1)`;
    ctx.fillText(`${hot.organ.label} · ${hot.topNode.intensity}`, cardX + 12, cardY + 40);
  });
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

/* ============ 交互 ============ */

function hitOrgan(mx: number, my: number): OrganHot | null {
  for (const p of projOrgans) {
    if (Math.hypot(mx - p.sx, my - p.sy) <= 30) {
      const hot = organHots.value.get(p.key);
      if (hot) return hot;
    }
  }
  return null;
}

function onClick(ev: MouseEvent) {
  const c = canvasRef.value;
  if (!c) return;
  const rect = c.getBoundingClientRect();
  const scaleX = c.width / rect.width, scaleY = c.height / rect.height;
  const hot = hitOrgan((ev.clientX - rect.left) * scaleX, (ev.clientY - rect.top) * scaleY);
  if (hot) emit("select", hot.topNode);
}

function onMove(ev: MouseEvent) {
  const c = canvasRef.value;
  if (!c) return;
  const rect = c.getBoundingClientRect();
  const scaleX = c.width / rect.width, scaleY = c.height / rect.height;
  const hot = hitOrgan((ev.clientX - rect.left) * scaleX, (ev.clientY - rect.top) * scaleY);
  cursorPointer.value = !!hot;
  hoverInfo.value = hot
    ? `${hot.organ.label} · ${hot.count} 项关联 · 强度和 ${hot.sum.toFixed(0)} · 点击查看评分依据`
    : "";
}

/* ============ 生命周期（防节流） ============ */

function resize() {
  const c = canvasRef.value;
  const wrap = wrapRef.value;
  if (!c || !wrap) return;
  const w = wrap.clientWidth;
  if (w < 40) return;
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
  document.addEventListener("visibilitychange", onVisibility);
  ro = new ResizeObserver(() => resize());
  if (wrapRef.value) ro.observe(wrapRef.value);
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
      <span>人体缓慢自转 · 器官辉光 ∝ 风险强度</span>
      <span class="sep">·</span>
      <span style="color:#f4695c">红色脉冲 = 高强度热点</span>
      <span class="sep">·</span>
      <span>点击器官查看评分依据</span>
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
</style>
