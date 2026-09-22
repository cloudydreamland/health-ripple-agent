import { reactive } from "vue";

/**
 * 主题：夜航（默认，墨玉鎏金暗色） / 日间（米纸亮色，与患者端·医生端·管理端同色系）。
 * 切换方式：顶栏按钮；localStorage 记忆；URL 参数 ?theme=day 强制指定。
 *
 * 画布与 SVG 不写死颜色：令牌全部来自 CSS 变量（style.css 中 :root 与
 * :root[data-theme="day"] 两套定义），画布在读色时用 triple()/varColor() 解析，
 * 主题切换后清缓存并重绘。
 */
export type ThemeMode = "night" | "day";

const STORAGE_KEY = "rc-theme";

function initialMode(): ThemeMode {
  const q = new URLSearchParams(window.location.search).get("theme");
  if (q === "day" || q === "night") return q;
  return localStorage.getItem(STORAGE_KEY) === "day" ? "day" : "night";
}

export const themeStore = reactive({ mode: initialMode() as ThemeMode });

export const THEME_EVENT = "rc-theme-change";

export function applyTheme(mode: ThemeMode, persist = true) {
  themeStore.mode = mode;
  if (persist) localStorage.setItem(STORAGE_KEY, mode);
  document.documentElement.dataset.theme = mode;
  clearColorCache();
  window.dispatchEvent(new CustomEvent(THEME_EVENT, { detail: mode }));
}

export function initTheme() {
  applyTheme(themeStore.mode, false);
}

export function toggleTheme() {
  applyTheme(themeStore.mode === "day" ? "night" : "day");
}

export function isDay(): boolean {
  return themeStore.mode === "day";
}

/* ---------------- 画布取色 ---------------- */

const colorCache = new Map<string, string>();

export function clearColorCache() {
  colorCache.clear();
}

function cssValue(name: string, host?: HTMLElement | null): string {
  const el = host ?? document.documentElement;
  return getComputedStyle(el).getPropertyValue(name).trim();
}

/** 颜色字面量 → "r, g, b"（支持 #rgb / #rrggbb / rgb() / rgba()） */
function toTriple(value: string): string | null {
  const v = value.trim();
  if (!v) return null;
  if (v.startsWith("#")) {
    const hex = v.slice(1);
    const full = hex.length === 3 ? hex.split("").map((c) => c + c).join("") : hex;
    if (full.length < 6) return null;
    const n = parseInt(full.slice(0, 6), 16);
    return `${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}`;
  }
  const m = v.match(/rgba?\(([^)]+)\)/);
  if (m) {
    const parts = m[1].split(",").map((s) => s.trim());
    if (parts.length >= 3) return `${parts[0]}, ${parts[1]}, ${parts[2]}`;
  }
  return null;
}

/** 读取 CSS 变量的 "r, g, b" 三元组（画布拼 rgba 用），主题切换时缓存失效 */
export function triple(name: string, fallbackTriple: string, host?: HTMLElement | null): string {
  const hit = colorCache.get(name);
  if (hit) return hit;
  const parsed = toTriple(cssValue(name, host));
  const out = parsed ?? fallbackTriple;
  colorCache.set(name, out);
  return out;
}

/** 读取 CSS 变量原值（SVG/DOM 内联用），带兜底 */
export function varColor(name: string, fallback: string, host?: HTMLElement | null): string {
  return cssValue(name, host) || fallback;
}

/** 解析 var(--x) / #hex / rgb() 为 "r, g, b"（canvas 用） */
export function resolveTriple(value: string, host?: HTMLElement | null, fallback = "120, 120, 120"): string {
  const v = value.trim();
  if (v.startsWith("var(")) {
    const name = v.slice(4, v.indexOf(")")).split(",")[0].trim();
    return triple(name, fallback, host);
  }
  return toTriple(v) ?? fallback;
}

/** canvas 着色：任意颜色字面量 + 透明度 */
export function tint(value: string, alpha: number, host?: HTMLElement | null): string {
  return `rgba(${resolveTriple(value, host)}, ${alpha})`;
}
