<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { DIMENSION_META, type RippleDimensions, type RippleIntensity, type RippleNode } from "../types";

/**
 * 器官涟漪图 ORGAN MAP · 3D 全息投影版（第十三轮 · 辉光渲染）：
 * - 人体 = 真实骨骼比例点云（7.5 头身 / 600 单位，y 向上，0 = 脚底）：
 *   颅骨（脑颅+面颅+眼眶+下颌）、24 椎 S 形脊柱（颈前凸/胸后凸/腰前凸）、
 *   12 对肋 + 胸骨、锁骨肩胛、骨盆（髂嵴/骶骨/耻骨弓）、四肢长骨 + 手足
 * - 渲染 = 预渲染辉光精灵 + additive 加色混合：体表"玻璃壳"体积、
 *   四级递归血管树（宽晕+亮线双描）、热点外晕/中晕/白炽核/双脉冲环、
 *   地面透视放射网格 + 旋转虚线盘 + 辉点、环境上浮微尘、全身扫描光带
 * - 器官 = 3D 发光点云球（医学语义映射：低血糖→心、视网膜→眼、药物肝损→肝…）
 * - 高强度器官 = 红色脉冲热点 + 引线标注浮窗；血管分支沿心/肺分布
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
  { key: "eye",      x: 9,   y: 560, z: 18,  r: 7.5, label: "眼 · 视网膜", keywords: ["视网膜", "视力", "飞蚊", "眼科", "白内障"] },
  { key: "brain",    x: 0,   y: 578, z: -2,  r: 17, label: "脑 · 脑血管", keywords: ["卒中", "脑", "晕厥", "神经", "意识", "头晕", "头痛", "晨峰", "认知"] },
  { key: "heart",    x: 15,  y: 428, z: 12,  r: 20, label: "心脏",       keywords: ["心梗", "心肌", "心律", "心衰", "冠心", "胸痛", "胸闷", "血压", "心悸"] },
  { key: "lungL",    x: -30, y: 440, z: -2,  r: 22, label: "肺 · 呼吸",   keywords: ["哮", "喘", "呼吸", "肺", "咳嗽", "低氧"] },
  { key: "lungR",    x: 30,  y: 440, z: -2,  r: 22, label: "肺 · 呼吸",   keywords: ["哮", "喘", "呼吸", "肺", "咳嗽", "低氧"] },
  { key: "liver",    x: -24, y: 350, z: 10,  r: 19, label: "肝 · 代谢",   keywords: ["肝", "饮酒", "酒", "酒精", "他汀", "转氨酶"] },
  { key: "pancreas", x: 20,  y: 338, z: 2,   r: 14, label: "胰腺 · 血糖", keywords: ["血糖", "胰岛", "糖化", "酮症", "二甲双胍", "降糖"] },
  { key: "kidney",   x: 0,   y: 296, z: -12, r: 15, label: "肾 · 泌尿",   keywords: ["肾", "尿", "肌酐", "白蛋白", "透析", "造影剂"] },
  { key: "stomach",  x: 6,   y: 372, z: 16,  r: 13, label: "胃 · 消化",   keywords: ["胃", "消化", "恶心", "呕吐", "腹泻", "腹痛", "食欲"] },
  { key: "footL",    x: -33, y: 14,  z: 8,   r: 12, label: "足 · 下肢",   keywords: ["足", "脚", "下肢", "伤口不愈", "截肢"] },
  { key: "footR",    x: 33,  y: 14,  z: 8,   r: 12, label: "足 · 下肢",   keywords: ["足", "脚", "下肢", "伤口不愈", "截肢"] },
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

/* ============ 发光精灵（预渲染径向渐变 + 加色叠加 = 全息辉光） ============ */
const spriteCache = new Map<string, HTMLCanvasElement>();
function glowSprite(rgb: string): HTMLCanvasElement {
  let sp = spriteCache.get(rgb);
  if (sp) return sp;
  sp = document.createElement("canvas");
  sp.width = 64; sp.height = 64;
  const g = sp.getContext("2d")!;
  const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, `rgba(${rgb},1)`);
  grad.addColorStop(0.22, `rgba(${rgb},0.5)`);
  grad.addColorStop(0.55, `rgba(${rgb},0.14)`);
  grad.addColorStop(1, `rgba(${rgb},0)`);
  g.fillStyle = grad;
  g.fillRect(0, 0, 64, 64);
  spriteCache.set(rgb, sp);
  return sp;
}
function drawGlow(c: CanvasRenderingContext2D, x: number, y: number, radius: number, rgb: string, alpha: number) {
  if (alpha <= 0.005 || radius <= 0.2) return;
  c.globalAlpha = Math.min(1, alpha);
  c.drawImage(glowSprite(rgb), x - radius, y - radius, radius * 2, radius * 2);
}

/* ============ 3D 几何构建（一次性）· 真实人体骨骼比例 ============
 * 标准人体比例（7.5 头身，总高 600 单位，y 向上，0 = 脚底）：
 *   头顶 600 / 眼线 ≈562 / 下巴 520 / 颈 495-522 / 肩线 490 / 乳头线 440
 *   肚脐 360 / 髋(会阴) 280 / 膝 ≈128 / 踝 ≈28；指端 ≈255（中段大腿）
 *   肩半宽 ≈66（含三角肌）/ 肋廓最宽半宽 52 / 髋半宽 48
 * 脊柱 S 形生理弯曲：腰椎前凸(≈335) → 胸椎后凸(≈430) → 颈椎前凸(≈505)
 */
type P3 = [number, number, number];
let rnd: () => number = Math.random;  // buildGeometry 内替换为确定性随机
const bodyPoints: P3[] = [];       // 骨骼亮层
const shellPoints: P3[] = [];      // 体表暗壳（极淡，体积语境）
const dustPoints: P3[] = [];       // 环境微尘（缓慢上浮）
const floorDots: P3[] = [];        // 基座地面辉点
const vesselLines: P3[][] = [];    // 血管线（折线段）
const vesselRed: boolean[] = [];   // 血管是否红色（心肺）

/* 脊柱 S 形：锚点 [y, z] 平滑插值（smoothstep） */
const SPINE_ANCHORS: [number, number][] = [
  [262, -13], [280, -15], [335, -6], [430, -24], [475, -16], [505, -9], [522, -12],
];
function spineZ(y: number): number {
  for (let i = 0; i < SPINE_ANCHORS.length - 1; i++) {
    const [y0, z0] = SPINE_ANCHORS[i];
    const [y1, z1] = SPINE_ANCHORS[i + 1];
    if (y <= y1) {
      const f = Math.max(0, Math.min(1, (y - y0) / (y1 - y0)));
      return z0 + (z1 - z0) * (f * f * (3 - 2 * f));
    }
  }
  return SPINE_ANCHORS[SPINE_ANCHORS.length - 1][1];
}

/* 肋廓外形：[y, 半宽, 前后径]，肩下最宽、肋缘收窄 */
const RIB_PROFILE: [number, number, number][] = [
  [472, 34, 27], [444, 52, 39], [408, 49, 35], [372, 44, 31], [350, 40, 29],
];
function ribProfile(y: number): [number, number] {
  if (y >= RIB_PROFILE[0][0]) return [RIB_PROFILE[0][1], RIB_PROFILE[0][2]];
  for (let i = 0; i < RIB_PROFILE.length - 1; i++) {
    const a = RIB_PROFILE[i], b = RIB_PROFILE[i + 1];
    if (y <= a[0] && y >= b[0]) {
      const f = (a[0] - y) / (a[0] - b[0]);
      return [a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f];
    }
  }
  const last = RIB_PROFILE[RIB_PROFILE.length - 1];
  return [last[1], last[2]];
}

/* 长骨：沿线段胶囊采样（半径 r0→r1 渐变，圆盘截面均匀分布） */
function bone(a: P3, b: P3, r0: number, r1: number, n: number, shell = false) {
  const ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
  const len = Math.hypot(ux, uy, uz) || 1;
  const dx = ux / len, dy = uy / len, dz = uz / len;
  let vx = 0, vy = 0, vz = 1;
  if (Math.abs(dz) > 0.9) { vx = 1; vy = 0; vz = 0; }
  let cx = dy * vz - dz * vy, cy = dz * vx - dx * vz, cz = dx * vy - dy * vx;
  const cl = Math.hypot(cx, cy, cz) || 1;
  cx /= cl; cy /= cl; cz /= cl;
  const wx = dy * cz - dz * cy, wy = dz * cx - dx * cz, wz = dx * cy - dy * cx;
  const out = shell ? shellPoints : bodyPoints;
  for (let i = 0; i < n; i++) {
    const f = rnd();
    const r = (r0 + (r1 - r0) * f) * Math.sqrt(rnd());
    const ang = rnd() * Math.PI * 2;
    const c = Math.cos(ang) * r, s = Math.sin(ang) * r;
    out.push([
      a[0] + dx * len * f + cx * c + wx * s,
      a[1] + dy * len * f + cy * c + wy * s,
      a[2] + dz * len * f + cz * c + wz * s,
    ]);
  }
}

/* 椭球点云（实心偏壳） */
function blob(c: P3, rx: number, ry: number, rz: number, n: number, shell = false) {
  const out = shell ? shellPoints : bodyPoints;
  for (let i = 0; i < n; i++) {
    const u = rnd() * Math.PI * 2, v = Math.acos(2 * rnd() - 1);
    const k = 0.82 + rnd() * 0.18;
    out.push([c[0] + Math.sin(v) * Math.cos(u) * rx * k, c[1] + Math.cos(v) * ry * k, c[2] + Math.sin(v) * Math.sin(u) * rz * k]);
  }
}

function buildGeometry() {
  rnd = mulberry32(20260921);
  bodyPoints.length = 0;
  shellPoints.length = 0;
  dustPoints.length = 0;
  floorDots.length = 0;
  vesselLines.length = 0;
  vesselRed.length = 0;

  /* —— 颅骨：脑颅 + 面颅 + 眼眶 + 下颌 —— */
  blob([0, 574, -2], 25, 25, 24, 240);
  blob([0, 540, 7], 18, 20, 19, 90);
  for (const side of [-1, 1]) blob([side * 8.5, 558, 19], 5, 4, 3.5, 14);
  for (const side of [-1, 1]) {
    for (let i = 0; i <= 8; i++) {
      const f = i / 8;
      bodyPoints.push([side * (16 * (1 - f)), 534 - 13 * f + (rnd() - 0.5) * 2, 4 + 11 * Math.sin(f * Math.PI / 2) + (rnd() - 0.5) * 2]);
    }
  }

  /* —— 脊柱：24 椎（棘突/横突）+ 骶骨三角 —— */
  for (let i = 0; i < 25; i++) {
    const y = 518 - i * 9.2;
    const z = spineZ(y);
    blob([0, y, z], 5.5, 4.5, 4.5, 9);
    bodyPoints.push([0, y - 2, z - 7]);
    bodyPoints.push([4.5, y, z - 2]);
    bodyPoints.push([-4.5, y, z - 2]);
  }
  for (let i = 0; i < 12; i++) {
    const f = i / 11;
    bodyPoints.push([(rnd() - 0.5) * (9 - 6 * f), 292 - f * 30, -19 + f * 4 + (rnd() - 0.5) * 2]);
  }

  /* —— 胸廓：12 对肋（浮肋短）+ 胸骨 —— */
  for (let i = 0; i < 12; i++) {
    const y0 = 468 - i * 11;
    const short = i >= 10;
    for (const side of [-1, 1]) {
      const n = short ? 10 : 16;
      for (let k = 0; k < n; k++) {
        const f = k / (n - 1);
        const phi = f * (short ? 2.4 : 3.5);
        const y = y0 - f * (short ? 10 : 16) - Math.sin(f * Math.PI) * 3;
        const [rx, rz] = ribProfile(y0 - f * 10);
        const w = short ? 0.75 : 1;
        bodyPoints.push([
          side * Math.sin(phi) * rx * w + (rnd() - 0.5) * 2.4,
          y + (rnd() - 0.5) * 2.4,
          -Math.cos(phi) * rz * w + (rnd() - 0.5) * 2.4,
        ]);
      }
    }
  }
  for (let i = 0; i < 14; i++) {
    const y = 462 - (i / 13) * 64;
    bodyPoints.push([(rnd() - 0.5) * 2.6, y, ribProfile(y)[1] - 1 + (rnd() - 0.5) * 2]);
  }

  /* —— 肩带：锁骨 S 弯 + 肩胛骨片 + 肱骨头 —— */
  for (const side of [-1, 1]) {
    for (let i = 0; i < 16; i++) {
      const f = i / 15;
      bodyPoints.push([side * (6 + f * 58), 478 + f * 10 + Math.sin(f * Math.PI) * 2, 30 - f * 28 + Math.sin(f * Math.PI) * 5]);
    }
    for (let i = 0; i < 14; i++) {
      const fx = rnd(), fy = rnd();
      bodyPoints.push([side * (14 + fx * 38), 470 - fy * 34 - fx * 6, -26 - rnd() * 4]);
    }
    blob([side * 60, 482, 0], 8, 8, 8, 16);
  }

  /* —— 骨盆：髂嵴环 + 耻骨弓 + 髋臼 —— */
  for (const side of [-1, 1]) {
    for (let i = 0; i < 16; i++) {
      const f = i / 15, phi = 0.18 + f * (Math.PI / 2 - 0.1);
      bodyPoints.push([
        side * Math.sin(phi) * 48,
        291 + Math.sin(phi) * 9 + (rnd() - 0.5) * 3,
        -Math.cos(phi) * 27,
      ]);
    }
    for (let i = 0; i < 8; i++) {
      const f = i / 7;
      bodyPoints.push([side * 30 * (1 - f), 272 - 10 * f * f, 24 + 4 * f]);
    }
    blob([side * 32, 280, 2], 6.5, 6.5, 6.5, 12);
  }

  /* —— 上肢：肱骨 / 桡尺骨 / 手（掌 + 4 指 + 拇）—— */
  for (const side of [-1, 1]) {
    bone([side * 62, 480, 0], [side * 67, 362, -6], 7.5, 6, 120);
    bone([side * 67, 358, -6], [side * 62, 288, 15], 5.5, 4, 92);
    bone([side * 64, 356, -9], [side * 59, 292, 11], 2.2, 1.8, 36);
    blob([side * 66, 362, -6], 5.5, 5.5, 5.5, 10);
    blob([side * 60, 276, 20], 6.5, 5, 8.5, 22);
    for (let fg = 0; fg < 4; fg++) {
      const ox = (fg - 1.5) * 3.4;
      for (let i = 0; i < 5; i++) {
        const f = i / 4;
        bodyPoints.push([side * (60 + ox * (1 + f * 0.4)), 270 - 15 * f, 25 + 5 * f]);
      }
    }
    for (let i = 0; i < 4; i++) bodyPoints.push([side * (55 - i * 1.2), 272 - i * 4, 19 - i * 1.5]);
  }

  /* —— 下肢：股骨 / 髌骨 / 胫腓骨 / 足 —— */
  for (const side of [-1, 1]) {
    bone([side * 32, 280, 2], [side * 30, 128, -4], 9, 7, 130);
    blob([side * 30, 133, 9], 4.5, 5, 3.5, 10);
    bone([side * 30, 124, -4], [side * 32, 30, -4], 6.5, 4, 100);
    bone([side * 34, 120, -4], [side * 35, 32, -3], 2.2, 1.8, 36);
    blob([side * 32, 14, -10], 5.5, 6, 6, 12);
    for (let i = 0; i < 22; i++) {
      const f = rnd();
      const z = -8 + f * 38;
      const y = 6 + 9 * Math.pow(1 - f, 1.6) + (rnd() - 0.5) * 2;
      bodyPoints.push([side * (32 + (rnd() - 0.5) * (9 - 3 * f)), y, z]);
    }
    for (let t2 = 0; t2 < 3; t2++) bodyPoints.push([side * (29 + t2 * 3), 5, 31 + t2 * 1.2]);
  }

  /* —— 体表暗壳：躯干环 + 头壳 + 四肢鞘（极淡） —— */
  const shellRings: [number, number, number][] = [
    [500, 20, 16], [470, 60, 34], [440, 60, 38], [410, 52, 34], [380, 46, 30],
    [350, 45, 30], [320, 52, 31], [295, 53, 30],
  ];
  for (const [y, rx, rz] of shellRings) {
    for (let i = 0; i < 20; i++) {
      const a = (i / 20) * Math.PI * 2;
      shellPoints.push([Math.cos(a) * rx + (rnd() - 0.5) * 4, y + (rnd() - 0.5) * 7, Math.sin(a) * rz + (rnd() - 0.5) * 4]);
    }
  }
  blob([0, 560, 0], 27, 40, 27, 120, true);
  for (const side of [-1, 1]) {
    bone([side * 66, 474, 0], [side * 62, 300, 10], 9, 7, 60, true);
    bone([side * 57, 262, 16], [side * 56, 250, 20], 6, 5, 10, true);
    bone([side * 40, 270, 2], [side * 33, 120, -2], 11, 9, 70, true);
    bone([side * 34, 110, -2], [side * 34, 26, -2], 8, 5, 60, true);
    bone([side * 33, 20, -4], [side * 32, 8, 16], 7, 4, 14, true);
  }

  /* —— 器官点云球 —— */
  for (const organ of ORGANS) {
    const n = Math.round(organ.r * 3.4);
    blob([organ.x, organ.y, organ.z], organ.r, organ.r, organ.r, n);
  }

  /* —— 环境微尘（人体四周缓慢上浮的辉尘）—— */
  for (let i = 0; i < 34; i++) {
    dustPoints.push([(rnd() - 0.5) * 380, rnd() * 640, (rnd() - 0.5) * 220]);
  }
  /* —— 基座地面辉点 —— */
  for (let i = 0; i < 46; i++) {
    const a = rnd() * Math.PI * 2, rr = Math.sqrt(rnd());
    floorDots.push([Math.cos(a) * rr * 130, 1, Math.sin(a) * rr * 46]);
  }

  // 血管树：主动脉（心→下）+ 冠状分支 + 肺内分支（红），递归加密到四级
  const branch = (origin: P3, dir: P3, depth: number, len: number, red: boolean) => {
    if (depth <= 0) return;
    const pts: P3[] = [];
    let [x, y, z] = origin;
    let [dx, dy, dz] = dir;
    for (let i = 0; i < len; i++) {
      pts.push([x, y, z]);
      x += dx * 5.2 + (rnd() - 0.5) * 4;
      y += dy * 5.2 + (rnd() - 0.5) * 4;
      z += dz * 5.2 + (rnd() - 0.5) * 4;
      dx += (rnd() - 0.5) * 0.22; dy += (rnd() - 0.5) * 0.22; dz += (rnd() - 0.5) * 0.22;
    }
    vesselLines.push(pts); vesselRed.push(red);
    if (depth >= 2) {
      branch(pts[len - 1], [dx + (rnd() - 0.5), dy + (rnd() - 0.5), dz + (rnd() - 0.5)], depth - 1, Math.max(3, len - 2), red);
      if (depth >= 3) branch(pts[Math.floor(len / 2)], [dx * -0.7 + (rnd() - 0.5), dy + (rnd() - 0.5), dz + (rnd() - 0.5)], depth - 2, Math.max(3, len - 3), red);
    }
  };
  // 主动脉下行（脊柱前方，止于骨盆水平）
  branch([14, 424, 16], [0.03, -1, 0.04], 3, 9, false);
  // 冠状动脉（心脏周围红，限制在肋廓内）
  branch([15, 430, 16], [0.9, -0.15, 0.35], 3, 5, true);
  branch([15, 426, 12], [-0.5, -0.3, 0.45], 3, 5, true);
  branch([15, 432, 10], [0.2, -0.9, -0.4], 2, 5, true);
  // 肺内分支（红，两侧各三根，参考"支气管树"意象）
  for (const side of [-1, 1]) {
    branch([side * 24, 460, 2], [side * 0.5, -0.65, 0.25], 3, 6, true);
    branch([side * 26, 440, 0], [side * 0.6, 0.35, 0.3], 3, 6, true);
    branch([side * 22, 452, -4], [side * 0.35, -0.4, -0.55], 2, 5, true);
  }
  // 肝/肾区滋养血管（青色，少量）
  branch([-22, 356, 12], [-0.3, -0.6, 0.2], 2, 5, false);
  branch([2, 300, -8], [0.1, -0.8, -0.2], 2, 5, false);
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
  [0, 0, 0], [0, 600, 0], [66, 482, 0], [-66, 482, 0],
  [64, 255, 20], [-64, 255, 20], [48, 300, 0], [-48, 300, 0],
  [34, 4, 32], [-34, 4, 32], [0, 574, 24],
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
  fitK = Math.min((H * 0.96) / range, (W * 0.92) / (2 * halfW + 1));
  fitY = H * 0.02 - minY * fitK;
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

  // 1. 投影基座：光锥（画面下沿的投影仪射向脚底）+ 脚底旋转椭圆盘 + 地面辉光网格
  const feet = project([0, 0, 0]);
  const bodyTop = project([0, 600, 0]);
  ctx.save();
  const cone = ctx.createLinearGradient(cx, H * 1.12, cx, feet.y);
  cone.addColorStop(0, "rgba(56, 191, 248, 0.14)");
  cone.addColorStop(0.55, "rgba(56, 191, 248, 0.05)");
  cone.addColorStop(1, "rgba(56, 191, 248, 0.015)");
  ctx.fillStyle = cone;
  ctx.beginPath();
  ctx.moveTo(cx - 40, H * 1.1);
  ctx.lineTo(cx - 115, feet.y);
  ctx.lineTo(cx + 115, feet.y);
  ctx.lineTo(cx + 40, H * 1.1);
  ctx.closePath();
  ctx.fill();
  ctx.restore();

  // 1b. 地面：透视放射网格 + 旋转虚线盘 + 辉点（加色，参考"全息舞台"）
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  const gridRx = 128 * fitK, gridRz = gridRx * 0.34;
  ctx.strokeStyle = "rgba(56, 191, 248, 0.10)";
  ctx.lineWidth = 1;
  for (let i = 0; i <= 12; i++) {
    const a = (i / 12) * Math.PI * 2;
    ctx.beginPath();
    ctx.moveTo(cx, feet.y);
    ctx.lineTo(cx + Math.cos(a) * gridRx, feet.y + Math.sin(a) * gridRz);
    ctx.stroke();
  }
  for (let i = 1; i <= 3; i++) {
    const rr = gridRx * (0.3 + i * 0.24);
    ctx.strokeStyle = `rgba(56, 191, 248, ${(0.16 - i * 0.035).toFixed(3)})`;
    ctx.beginPath();
    ctx.ellipse(cx, feet.y, rr, rr * 0.34, 0, 0, Math.PI * 2);
    ctx.stroke();
  }
  for (let i = 1; i <= 3; i++) {
    ctx.beginPath();
    ctx.ellipse(cx, feet.y, 38 + i * 34, (38 + i * 34) * 0.24, 0, 0, Math.PI * 2);
    ctx.strokeStyle = `rgba(125, 211, 252, ${0.24 - i * 0.06})`;
    ctx.lineWidth = 1;
    ctx.setLineDash([5, 9]);
    ctx.lineDashOffset = -t * 14 * i;
    ctx.stroke();
    ctx.setLineDash([]);
  }
  for (let i = 0; i < floorDots.length; i++) {
    const pr = project(floorDots[i]);
    const tw = 0.5 + 0.5 * Math.sin(t * 1.6 + i * 1.9);
    drawGlow(ctx, pr.x, pr.y, 1.4 + pr.s * 2.4, "125,211,252", 0.08 + tw * 0.14);
  }
  ctx.restore();

  // 0. 环境微尘：人体四周缓慢上浮的辉尘（加色）
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  for (let i = 0; i < dustPoints.length; i++) {
    const d = dustPoints[i];
    const y = (d[1] + t * 9) % 660;
    const pr = project([d[0], y, d[2]]);
    const tw = 0.5 + 0.5 * Math.sin(t * 1.3 + i * 2.4);
    drawGlow(ctx, pr.x, pr.y, 1.5 + pr.s * 2.6, "125,211,252", 0.07 + tw * 0.10);
  }
  ctx.restore();

  // 2. 体表暗壳：半透明"玻璃体"（加色叠出体积）
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  ctx.fillStyle = "rgba(88, 168, 224, 0.085)";
  for (const p of shellPoints) {
    const pr = project(p);
    ctx.fillRect(pr.x - 1.2, pr.y - 1.2, 2.4, 2.4);
  }
  ctx.restore();

  // 3. 骨骼亮层：锐利亮核 + 轻晕双层（近亮远暗）
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  for (const p of bodyPoints) {
    const pr = project(p);
    const a = Math.max(0.2, Math.min(0.9, (pr.s - 0.45) * 2.4));
    drawGlow(ctx, pr.x, pr.y, 1.7 + pr.s * 1.7, "150, 220, 255", a * 0.34);
    ctx.fillStyle = `rgba(198, 235, 255, ${a.toFixed(3)})`;
    ctx.fillRect(pr.x - 1.1, pr.y - 1.1, 2.2, 2.2);
  }
  ctx.restore();

  // 4. 器官点云（热度着色辉光，亮度呼吸）
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  for (const hot of hots.values()) {
    const [r, g, b] = heatRgb(hot.sum, hot.max);
    const rgb = `${r}, ${g}, ${b}`;
    const breathe = 0.75 + 0.25 * Math.sin(t * 1.7 + hot.organ.x * 0.05);
    const n = Math.round(hot.organ.r * 3.4);
    const rnd = mulberry32(hot.organ.y * 977 + hot.organ.x);
    for (let i = 0; i < n; i++) {
      const u = rnd() * Math.PI * 2, v = Math.acos(2 * rnd() - 1);
      const rr = hot.organ.r * (0.35 + 0.65 * Math.cbrt(rnd()));
      const p: P3 = [hot.organ.x + Math.sin(v) * Math.cos(u) * rr, hot.organ.y + Math.cos(v) * rr, hot.organ.z + Math.sin(v) * Math.sin(u) * rr];
      const pr = project(p);
      const a = Math.min(0.9, (0.4 + hot.max / 150) * breathe);
      drawGlow(ctx, pr.x, pr.y, 1.8 + pr.s * 1.9, rgb, a * 0.42);
      ctx.fillStyle = `rgba(${r}, ${g}, ${b}, ${(a * 0.92).toFixed(3)})`;
      ctx.fillRect(pr.x - 1.15, pr.y - 1.15, 2.3, 2.3);
    }
  }
  ctx.restore();

  // 5. 血管树（宽淡晕 + 细亮线双描，加色）
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  ctx.lineCap = "round";
  vesselLines.forEach((pts, idx) => {
    const red = vesselRed[idx];
    const rgb = red ? "244, 105, 92" : "125, 211, 252";
    ctx.beginPath();
    pts.forEach((p, i) => {
      const pr = project(p);
      i === 0 ? ctx.moveTo(pr.x, pr.y) : ctx.lineTo(pr.x, pr.y);
    });
    ctx.strokeStyle = `rgba(${rgb}, 0.10)`;
    ctx.lineWidth = 3.2;
    ctx.stroke();
    ctx.strokeStyle = `rgba(${rgb}, ${red ? 0.55 : 0.4})`;
    ctx.lineWidth = 1;
    ctx.stroke();
  });
  ctx.restore();

  // 6. 器官热点：外晕 + 中晕 + 白炽亮核 + 双脉冲扩散环（参考医学全息图）
  projOrgans.length = 0;
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  for (const hot of hots.values()) {
    const center = project([hot.organ.x, hot.organ.y, hot.organ.z]);
    projOrgans.push({ key: hot.key, sx: center.x, sy: center.y, label: hot.topLabel });
    const [r, g, b] = heatRgb(hot.sum, hot.max);
    const rgb = `${r}, ${g}, ${b}`;
    const breathe = 0.72 + 0.28 * Math.sin(t * 1.7 + hot.organ.x * 0.05);
    drawGlow(ctx, center.x, center.y, 30 * center.s + 6, rgb, (0.16 + hot.max / 420) * breathe);
    drawGlow(ctx, center.x, center.y, 13 * center.s + 3, rgb, 0.42 * breathe);
    if (hot.max >= 70 || hot.sum >= 150) {
      for (const off of [0, 0.5]) {
        const pulse = (t * 0.9 + hot.organ.x * 0.02 + off) % 1;
        ctx.strokeStyle = `rgba(244, 105, 92, ${((1 - pulse) * 0.55).toFixed(3)})`;
        ctx.lineWidth = 1.6;
        ctx.beginPath();
        ctx.arc(center.x, center.y, (6 + pulse * 30) * center.s, 0, Math.PI * 2);
        ctx.stroke();
      }
    }
    drawGlow(ctx, center.x, center.y, 4.2 * center.s + 1.4, "255, 255, 255", 0.75);
  }
  ctx.restore();

  // 7. TOP2 标注浮窗：恢复正常混合保证可读
  ctx.globalCompositeOperation = "source-over";
  top2Callouts(ctx, t);

  // 8. 家属注意事项：环绕外圈辉光点
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  for (const node of familyNodes.value) {
    const ang = (Number(node.intensity) / 100) * Math.PI * 2 + t * 0.06;
    const p: P3 = [Math.cos(ang) * 150, 330 + Math.sin(t * 0.4 + ang) * 60, Math.sin(ang) * 90];
    const pr = project(p);
    if (pr.s <= 0.05) continue;  // 转到相机身后时跳过（负半径会让 arc 抛异常中断整帧）
    drawGlow(ctx, pr.x, pr.y, 6 * pr.s + 2, "79, 209, 165", 0.5);
    ctx.strokeStyle = "rgba(79, 209, 165, 0.3)";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(pr.x, pr.y, Math.max(0.5, (6 + Math.sin(t * 2 + ang) * 2) * pr.s), 0, Math.PI * 2);
    ctx.stroke();
  }

  // 9. 全身扫描光带（上下往复，加色，柔和）
  const bodyTopY = Math.min(...projOrgans.map(p => p.sy), bodyTop.y);
  const bodyBotY = Math.max(...projOrgans.map(p => p.sy), feet.y);
  const scanY = bodyTopY + ((t * 0.14) % 1) * (bodyBotY - bodyTopY);
  const scanGrad = ctx.createLinearGradient(0, scanY - 22, 0, scanY + 22);
  scanGrad.addColorStop(0, "rgba(56, 191, 248, 0)");
  scanGrad.addColorStop(0.5, "rgba(56, 191, 248, 0.055)");
  scanGrad.addColorStop(1, "rgba(56, 191, 248, 0)");
  ctx.fillStyle = scanGrad;
  const scanHalf = Math.max(70, 80 * fitK);
  ctx.fillRect(cx - scanHalf, scanY - 22, scanHalf * 2, 44);
  drawGlow(ctx, cx, scanY, scanHalf * 0.9, "125, 211, 252", 0.04);
  ctx.fillStyle = "rgba(170, 225, 255, 0.28)";
  ctx.fillRect(cx - scanHalf, scanY - 0.7, scanHalf * 2, 1.2);
  ctx.restore();
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

/** 面板固定占高：栏头 + 内边距 + 底部图例 + flex 间隙（画布之外的部分） */
function chromeHeight(): number {
  const panel = wrapRef.value?.closest(".pond-hero") as HTMLElement | null;
  if (!panel) return 100;
  const head = panel.querySelector(".sec-head") as HTMLElement | null;
  const body = panel.querySelector(".panel-body") as HTMLElement | null;
  const legend = wrapRef.value?.querySelector(".omap-legend") as HTMLElement | null;
  const cs = body ? getComputedStyle(body) : null;
  const padY = cs ? parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom) : 0;
  return Math.round((head?.getBoundingClientRect().height ?? 44) + padY + (legend?.getBoundingClientRect().height ?? 0) + 8);
}

/** 左栏内容自然高度（逐子面板求和）——中栏与之对齐，三栏底边齐平 */
function sideContentHeight(): number {
  const row = wrapRef.value?.closest(".hero-row") as HTMLElement | null;
  const side = row?.querySelector(".hero-left") as HTMLElement | null;
  if (!side) return 0;
  const kids = [...side.children] as HTMLElement[];
  if (!kids.length) return 0;
  const gap = 14;
  return Math.round(kids.reduce((a, k) => a + k.getBoundingClientRect().height, 0) + gap * (kids.length - 1));
}

function resize() {
  const c = canvasRef.value;
  const wrap = wrapRef.value;
  if (!c || !wrap) return;
  const w = wrap.clientWidth;
  if (w < 40) return;
  dpr = window.devicePixelRatio || 1;
  W = w;
  // 竖幅画布：人体是窄高比例；再按"左栏内容高度 − 面板占高"收敛，
  // 使中栏底边与左右两栏对齐（不再垂出屏幕）
  const byWidth = Math.min(940, W * 1.45);
  const bySides = sideContentHeight() - chromeHeight();
  H = Math.round(Math.max(460, Math.min(byWidth, bySides > 320 ? bySides : byWidth)));
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

/** 视口尺寸变化（缩窗/切分辨率）→ 重算画布高度，保持与侧栏底边对齐 */
function onWindowResize() {
  resize();
}

watch(organHots, () => {
  // 数据变化会改变左栏内容高度 → 重算画布高度，保持三栏底边对齐
  resize();
}, { deep: false });

onMounted(() => {
  document.addEventListener("visibilitychange", onVisibility);
  window.addEventListener("resize", onWindowResize);
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
  window.removeEventListener("resize", onWindowResize);
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
.omap-wrap { position: relative; display: flex; flex-direction: column; align-items: center; gap: 8px; min-width: 0; width: 100%; }
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
