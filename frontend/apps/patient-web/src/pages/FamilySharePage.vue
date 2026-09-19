<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { request } from "@smart-cloud-brain/shared-api";

/**
 * 家属守护圈 · 只读视图（第九轮创新）
 *
 * 通过 HMAC 签名的分享令牌访问，无需登录。最小披露口径：
 * 只见守护态势（天气等级/headline/家属须知/72h趋势形状/消解率），
 * 不含患者姓名、诊断原文、决策记录——知情权与隐私权的边界在此显式划清。
 */

interface FamilyView {
  weather: string;
  weatherLabel: string;
  index: number;
  headline: string;
  familyTip: string;
  dueTodayCount: number;
  trend: number[];
  resolutionRate: number;
  model: string;
}

const route = useRoute();
const token = computed(() => String(route.params.token ?? ""));
const view = ref<FamilyView | null>(null);
const error = ref("");
const loading = ref(true);

const WEATHER_COLOR: Record<string, string> = {
  SUNNY: "#41795f",
  CLOUDY: "#a8871a",
  RAIN: "#c07a1d",
  STORM: "#bd4033",
};

function forecastColor(v: number): string {
  if (v >= 45) return "#bd4033";
  if (v >= 25) return "#c07a1d";
  if (v > 0) return "#a8871a";
  return "#41795f";
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    view.value = await request<FamilyView>(`/api/health-event/share/public/${token.value}`, {}, null);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "链接无效或已过期";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <main class="patient-page theme-patient">
    <header class="share-head">
      <span class="share-seal">守</span>
      <div>
        <h1>家属守护圈<span class="share-en">FAMILY GUARDIAN CIRCLE</span></h1>
        <p>这是一份只读的守护态势视图——由您的家人主动分享，帮助您一起守护TA</p>
      </div>
    </header>

    <p v-if="loading" class="share-status">正在加载守护态势…</p>
    <section v-else-if="error" class="share-error" role="alert">
      <b>链接无效或已过期</b>
      <span>{{ error }}。请让家人重新生成守护圈链接（链接7天有效，可随时刷新）。</span>
    </section>

    <template v-else-if="view">
      <!-- 今日健康气象 -->
      <section class="share-panel">
        <div class="sp-weather" :style="{ '--tone': WEATHER_COLOR[view.weather] ?? '#8f8a75' }">
          <div class="sp-weather-main">
            <span class="sp-icon">{{ view.weather === "SUNNY" ? "☀️" : view.weather === "CLOUDY" ? "⛅" : view.weather === "RAIN" ? "🌧️" : "⛈️" }}</span>
            <div>
              <h2 class="sp-label">{{ view.weatherLabel }}</h2>
              <p class="sp-headline">{{ view.headline }}</p>
            </div>
            <div class="sp-index">
              <b>{{ view.index }}</b>
              <span>今日守护指数</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 家属须知（这个页面最重要的信息） -->
      <section v-if="view.familyTip" class="share-panel">
        <h3 class="sp-title">家属须知 · 您现在可以帮上什么</h3>
        <p class="sp-tip">{{ view.familyTip }}</p>
      </section>

      <!-- 72小时趋势 -->
      <section class="share-panel">
        <h3 class="sp-title">未来三天守护趋势</h3>
        <p class="sp-hint">每一格是3小时——颜色越暖表示那个时段越需要家人多留意。</p>
        <div class="sp-trend" role="img" aria-label="未来72小时守护强度趋势">
          <div v-for="(v, i) in view.trend" :key="i" class="sp-col" :title="`${i}小时后 · 强度${v}`">
            <div class="sp-bar" :style="{ height: Math.max(4, Math.min(72, v)) + 'px', background: forecastColor(v) }" />
          </div>
        </div>
      </section>

      <!-- 消解率 -->
      <section class="share-panel sp-row">
        <div>
          <h3 class="sp-title">守护回执进展</h3>
          <p class="sp-hint">守护事项的消解率——每缓解一项，趋势就会降下去。</p>
        </div>
        <div class="sp-rate">
          <svg viewBox="0 0 90 90" class="sp-ring">
            <circle cx="45" cy="45" r="38" fill="none" stroke="#e3dcbc" stroke-width="9" />
            <circle cx="45" cy="45" r="38" fill="none" stroke="#41795f" stroke-width="9" stroke-linecap="round"
                stroke-dasharray="238.6" :stroke-dashoffset="238.6 * (1 - Math.min(100, view.resolutionRate) / 100)"
                transform="rotate(-90 45 45)" />
            <text x="45" y="50" text-anchor="middle" class="sp-ring-num">{{ view.resolutionRate }}%</text>
          </svg>
        </div>
      </section>

      <footer class="share-foot">
        <p>{{ view.model }}</p>
        <p>健康事件涟漪守护智能体 · 家属守护圈（只读，不含诊疗记录）</p>
      </footer>
    </template>
  </main>
</template>

<style scoped>
.share-head {
  max-width: 860px;
  margin: 26px auto 18px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 14px;
  border-bottom: 2px solid var(--ink);
  padding-bottom: 14px;
}
.share-seal {
  width: 52px; height: 52px;
  display: grid; place-items: center;
  font-family: var(--font-serif);
  font-size: 26px; font-weight: 800;
  color: #f6f3e8;
  background: var(--primary);
  border-radius: 10px;
  transform: rotate(-3deg);
  box-shadow: 0 2px 10px rgba(189, 64, 51, 0.28);
  flex: none;
}
.share-head h1 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 26px;
  letter-spacing: 1px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.share-en { font-size: 9px; color: var(--muted); letter-spacing: 2px; font-family: var(--font-mono); }
.share-head p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.share-status, .share-error { max-width: 860px; margin: 16px auto; padding: 0 24px; color: var(--muted); }
.share-error { display: flex; flex-direction: column; gap: 6px; color: var(--danger); }

.share-panel {
  max-width: 860px;
  margin: 14px auto;
  padding: 18px 22px;
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow);
}
.sp-title { margin: 0 0 8px; font-family: var(--font-serif); font-size: 16px; letter-spacing: 0.5px; }
.sp-hint { margin: 0 0 10px; color: var(--muted); font-size: 12.5px; line-height: 1.6; }

.sp-weather {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px 20px;
  background:
    radial-gradient(460px 150px at 14% 0%, rgba(51, 98, 143, 0.07), transparent 72%),
    radial-gradient(340px 130px at 88% 4%, rgba(189, 64, 51, 0.05), transparent 70%),
    var(--surface-alt);
}
.sp-weather-main { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.sp-icon { font-size: 40px; }
.sp-label { margin: 0; font-family: var(--font-serif); font-size: 24px; color: var(--tone, var(--ink)); letter-spacing: 1px; }
.sp-headline { margin: 5px 0 0; font-size: 14px; color: var(--ink-soft); }
.sp-index { margin-left: auto; text-align: center; }
.sp-index b { display: block; font-size: 36px; line-height: 1; font-variant-numeric: tabular-nums; }
.sp-index span { font-size: 11px; color: var(--muted); }

.sp-tip {
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--ink);
  background: var(--surface-alt);
  border: 1px dashed var(--line-strong);
  border-radius: 10px;
  padding: 12px 14px;
}

.sp-trend { display: flex; align-items: flex-end; gap: 3px; height: 92px; padding-top: 6px; border-bottom: 2px solid var(--ink); }
.sp-col { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; height: 100%; min-width: 0; }
.sp-bar { width: 100%; border-radius: 3px 3px 0 0; }

.sp-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.sp-rate { margin-left: auto; }
.sp-ring { width: 84px; }
.sp-ring-num { font-size: 15px; font-weight: 700; fill: var(--ink); }

.share-foot { max-width: 860px; margin: 20px auto 30px; padding: 0 24px; color: var(--subtle); font-size: 11px; line-height: 1.8; }
.share-foot p { margin: 2px 0; }

@media (max-width: 720px) {
  .share-head h1 { font-size: 21px; }
  .sp-index b { font-size: 28px; }
}
</style>
