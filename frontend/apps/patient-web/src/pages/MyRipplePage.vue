<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { request, useAuthStore } from "@smart-cloud-brain/shared-api";

/**
 * 我的安全涟漪 —— 患者端守护页
 *
 * 三块内容：
 * 1. 今日健康气象：用天气隐喻转译当日守护态势（晴/多云/大雨/暴雨）
 * 2. 涟漪曲线：历次健康事件推演的RII强度轨迹（患者健康数字孪生的雏形）
 * 3. 守护回执：时间学触达的干预回执（已缓解/未缓解/已就医），驱动涟漪消解闭环
 *
 * 附带：适老化模式（大字号高对比）+ 一键分享给家属（文本摘要复制）
 */

interface WeatherItem {
  chronoType: string;
  event: string;
  triggerTime: string;
  nextTriggerAt: string;
  action: string;
  intensity: number;
  timingCard: { evidenceBasis: string; missCost: string; evidenceLevel: string };
}

interface Weather {
  date: string;
  index: number;
  weather: string;
  weatherLabel: string;
  color: string;
  headline: string;
  dueTodayCount: number;
  escalatedCount: number;
  items: WeatherItem[];
  familyTip: string;
  model: string;
}

interface LedgerItem {
  triggerId: number;
  chronoType: string;
  event: string;
  triggerTime: string;
  action: string;
  status: string;
  intensity: number;
  timingCard: { evidenceBasis: string; missCost: string; evidenceLevel: string };
  feedbackStatus: string;
  feedbackNote: string;
  feedbackAt: string;
}

interface Resolution {
  totalTriggers: number;
  pendingFeedback: number;
  resolvedCount: number;
  escalatedCount: number;
  totalIntensity: number;
  resolvedIntensity: number;
  resolutionRate: number;
  closureStatus: string;
}

interface RippleHistoryItem {
  rippleEventId: number;
  diagnosis: string;
  createdAt: string;
  rippleGraph: { summary?: { rippleIntensity?: { index: number; level: string } } };
}

const auth = useAuthStore();
const elderMode = ref(localStorage.getItem("scb-elder-mode") === "1");
const loading = ref(true);
const errorMsg = ref("");
const shareText = ref("");
const shareFallback = ref(false);

const weather = ref<Weather | null>(null);
const ledger = ref<LedgerItem[]>([]);
const resolution = ref<Resolution | null>(null);
const history = ref<RippleHistoryItem[]>([]);
const feedbackBusy = ref<number | null>(null);
const shared = ref(false);

const patientId = computed(() => auth.session?.userId ?? 0);

const riiPoints = computed(() =>
  history.value
    .map((h) => ({
      t: h.createdAt,
      index: h.rippleGraph?.summary?.rippleIntensity?.index ?? 0,
      diagnosis: h.diagnosis,
    }))
    .slice(0, 12)
    .reverse(),
);

const WEATHER_ICON: Record<string, string> = {
  SUNNY: "☀️",
  CLOUDY: "⛅",
  RAIN: "🌧️",
  STORM: "⛈️",
};

const TYPE_LABEL: Record<string, string> = {
  WINDOW: "窗口期",
  RHYTHM: "节律",
  PERIODIC: "周期",
  SEASONAL: "季节",
};

async function loadAll() {
  if (!patientId.value) return;
  loading.value = true;
  errorMsg.value = "";
  const token = auth.token();
  try {
    const [w, l, r, h] = await Promise.all([
      request<Weather>(`/api/health-weather/daily?patientId=${patientId.value}`, {}, token).catch(() => null),
      request<LedgerItem[]>(`/api/health-event/ripple/feedback-ledger?patientId=${patientId.value}`, {}, token).catch(() => []),
      request<Resolution>(`/api/health-event/ripple/resolution?patientId=${patientId.value}`, {}, token).catch(() => null),
      request<RippleHistoryItem[]>(`/api/health-event/ripple/patient/${patientId.value}`, {}, token).catch(() => []),
    ]);
    weather.value = w;
    ledger.value = Array.isArray(l) ? l : [];
    resolution.value = r;
    history.value = Array.isArray(h) ? h : [];
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function sendFeedback(item: LedgerItem, outcome: string) {
  // 已就医不可撤销（会计入气象警报与家属提示），必须二次确认防误触
  if (outcome === "ESCALATED"
    && !window.confirm("确认已完成就医、需要医生跟进吗？\n确认后该事项将终结并通知家属重点关注，不可撤销。")) {
    return;
  }
  feedbackBusy.value = item.triggerId;
  try {
    await request(
      `/api/chrono/trigger/${item.triggerId}/feedback?outcome=${outcome}&note=${encodeURIComponent(outcome === "RESOLVED" ? "已缓解" : outcome === "UNRESOLVED" ? "未缓解，需要加强关注" : "症状加重，已升级就医")}`,
      { method: "POST" },
      auth.token(),
    );
    await loadAll();
  } finally {
    feedbackBusy.value = null;
  }
}

function toggleElder() {
  elderMode.value = !elderMode.value;
  localStorage.setItem("scb-elder-mode", elderMode.value ? "1" : "0");
}

/** 剪贴板写入：优先 Clipboard API（需 HTTPS/localhost），失败回退 execCommand，
 * 再失败把文本展示出来供手动复制——演示环境常为 http 局域网地址，必须有兜底。 */
async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    /* 回退 execCommand */
  }
  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

async function shareToFamily() {
  const lines: string[] = [];
  if (weather.value) {
    lines.push(`【今日健康气象】${weather.value.weatherLabel}（指数${weather.value.index}）`);
    lines.push(weather.value.headline);
    if (weather.value.familyTip) lines.push(`家属须知：${weather.value.familyTip}`);
  }
  if (resolution.value) {
    lines.push(`守护回执：已缓解${resolution.value.resolvedCount}项，消解率${resolution.value.resolutionRate}%（${resolution.value.closureStatus}）`);
  }
  shareText.value = lines.join("\n");
  if (await copyText(shareText.value)) {
    shared.value = true;
    setTimeout(() => (shared.value = false), 2500);
  } else {
    shareFallback.value = true; // 无法自动复制：展示文本让患者/家属手动复制
  }
}

onMounted(loadAll);
</script>

<template>
  <div class="ripple-page" :class="{ 'elder-mode': elderMode }">
    <header class="rp-head">
      <div>
        <h2>我的健康涟漪</h2>
        <p>每一次诊断都会在生活中激起涟漪——这里能看到它，也能让它平息</p>
      </div>
      <div class="rp-actions">
        <button class="ghost" type="button" @click="shareToFamily">
          {{ shared ? "✓ 已复制给家属" : "分享给家属" }}
        </button>
        <button class="ghost" type="button" @click="toggleElder">{{ elderMode ? "标准字号" : "适老化大字" }}</button>
      </div>
    </header>

    <p v-if="errorMsg" class="rp-error" role="status" aria-live="polite">守护数据暂时加载不出来，请稍后下拉重试；如持续失败请联系您的医生或社区工作人员。</p>
    <p v-if="loading" class="rp-loading" role="status">正在加载守护数据…</p>

    <!-- 剪贴板不可用时的手动复制兜底（http 局域网环境常见） -->
    <section v-if="shareFallback" class="rp-card" role="region" aria-label="复制分享内容">
      <h3>请手动复制给家属</h3>
      <textarea class="rp-share-text" readonly rows="5" @focus="($event.target as HTMLTextAreaElement).select()">{{ shareText }}</textarea>
      <p class="rp-hint">点击文本框全选后，用"复制"或长按复制发给家属。</p>
      <button class="ghost" type="button" @click="shareFallback = false">关闭</button>
    </section>

    <!-- 今日健康气象 -->
    <section v-if="weather" class="rp-weather" :style="{ borderColor: weather.color + '66' }">
      <div class="rp-weather-main">
        <span class="rp-weather-icon">{{ WEATHER_ICON[weather.weather] ?? "☁️" }}</span>
        <div>
          <h3 :style="{ color: weather.color }">{{ weather.weatherLabel }}</h3>
          <p class="rp-headline">{{ weather.headline }}</p>
        </div>
        <div class="rp-index">
          <b>{{ weather.index }}</b>
          <span>今日守护指数</span>
        </div>
      </div>
      <ul v-if="weather.items.length" class="rp-items">
        <li v-for="item in weather.items" :key="item.event + item.triggerTime">
          <span class="rp-type" :data-type="item.chronoType">{{ TYPE_LABEL[item.chronoType] ?? item.chronoType }}</span>
          <span class="rp-event">{{ item.event }}</span>
          <span class="rp-time">{{ item.triggerTime }}</span>
        </li>
      </ul>
      <p v-if="weather.familyTip" class="rp-family">👨‍👩‍👧 家属须知：{{ weather.familyTip }}</p>
    </section>

    <!-- 涟漪曲线 + 消解率 -->
    <section class="rp-grid">
      <div class="rp-card">
        <h3>涟漪曲线（RII 轨迹）</h3>
        <svg v-if="riiPoints.length >= 2" viewBox="0 0 320 120" class="rp-chart">
          <polyline
            :points="riiPoints.map((p, i) => `${20 + (i * 280) / (riiPoints.length - 1)},${105 - Math.min(100, p.index)}`).join(' ')"
            fill="none" stroke="#2a8787" stroke-width="2.5" stroke-linejoin="round"
          />
          <circle
            v-for="(p, i) in riiPoints" :key="i"
            :cx="20 + (i * 280) / (riiPoints.length - 1)" :cy="105 - Math.min(100, p.index)"
            r="3.5" fill="#2a8787"
          >
            <title>{{ p.diagnosis }}：RII={{ p.index }}</title>
          </circle>
        </svg>
        <p v-else class="rp-hint">完成至少两次健康事件推演后，这里会出现你的涟漪强度轨迹。</p>
      </div>
      <div class="rp-card" v-if="resolution">
        <h3>守护回执 · 涟漪消解</h3>
        <div class="rp-rate">
          <svg viewBox="0 0 90 90" class="rp-ring">
            <circle cx="45" cy="45" r="38" fill="none" stroke="#e4e6e8" stroke-width="9" />
            <circle
              cx="45" cy="45" r="38" fill="none" stroke="#2a8787" stroke-width="9"
              stroke-linecap="round" stroke-dasharray="238.6"
              :stroke-dashoffset="238.6 * (1 - Math.min(100, resolution.resolutionRate) / 100)"
              transform="rotate(-90 45 45)"
            />
            <text x="45" y="50" text-anchor="middle" class="rp-ring-num">{{ resolution.resolutionRate }}%</text>
          </svg>
          <div>
            <p class="rp-status">{{ resolution.closureStatus }}</p>
            <p class="rp-hint">已缓解 {{ resolution.resolvedCount }} / 共 {{ resolution.totalTriggers }} 项</p>
            <p v-if="resolution.escalatedCount" class="rp-warn">⚠ {{ resolution.escalatedCount }} 项已升级就医</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 守护回执列表 -->
    <section class="rp-card">
      <h3>守护事项回执</h3>
      <p class="rp-hint">这是智能体为你主动设置的守护计划——完成后点击回执，涟漪就会消解。</p>
      <div v-if="!ledger.length" class="rp-hint">暂无守护事项，完成一次就诊推演后这里会出现主动守护计划。</div>
      <ul class="rp-ledger">
        <li v-for="item in ledger" :key="item.triggerId" :class="{ done: item.feedbackStatus === 'RESOLVED' }">
          <div class="rp-ledger-main">
            <span class="rp-type" :data-type="item.chronoType">{{ TYPE_LABEL[item.chronoType] ?? item.chronoType }}</span>
            <b>{{ item.event }}</b>
            <span class="rp-hint">{{ item.action }}</span>
          </div>
          <div class="rp-ledger-card" v-if="item.timingCard?.evidenceBasis">
            📎 {{ item.timingCard.evidenceBasis }}（{{ item.timingCard.evidenceLevel }}）
          </div>
          <div class="rp-ledger-foot">
            <span class="rp-fb" :data-fb="item.feedbackStatus || 'PENDING'">
              {{ item.feedbackStatus === "RESOLVED" ? "✓ 已缓解" : item.feedbackStatus === "ESCALATED" ? "已升级就医" : item.feedbackStatus === "UNRESOLVED" ? "未缓解·加强守护中" : "待回执" }}
            </span>
            <div v-if="item.feedbackStatus !== 'RESOLVED' && item.feedbackStatus !== 'ESCALATED'" class="rp-btns">
              <button type="button" :disabled="feedbackBusy === item.triggerId" @click="sendFeedback(item, 'RESOLVED')">已缓解</button>
              <button class="warn" type="button" :disabled="feedbackBusy === item.triggerId" @click="sendFeedback(item, 'UNRESOLVED')">未缓解</button>
              <button class="danger" type="button" :disabled="feedbackBusy === item.triggerId" @click="sendFeedback(item, 'ESCALATED')">已就医</button>
            </div>
            <span v-else-if="item.feedbackStatus === 'ESCALATED'" class="rp-hint">已升级就医，等待医生跟进</span>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.ripple-page { display: flex; flex-direction: column; gap: 14px; padding-bottom: 24px; }
.rp-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; flex-wrap: wrap; border-bottom: 2px solid #211e15; padding-bottom: 10px; }
.rp-head h2 { margin: 0; font-size: 20px; letter-spacing: 0.5px; }
.rp-head p { margin: 4px 0 0; color: #8b8778; font-size: 13px; }
.rp-actions { display: flex; gap: 8px; }
.rp-actions button, .rp-btns button {
  border: 1.5px solid #211e15; background: #fbfaf5; border-radius: 9px;
  padding: 7px 12px; font-size: 13px; cursor: pointer; color: #211e15; font-weight: 600;
}
.rp-actions button:hover { background: #211e15; color: #f6f4ea; }
.rp-btns button { border-color: #2e8f63; color: #2e8f63; }
.rp-btns button.warn { border-color: #d97c2a; color: #d97c2a; }
.rp-btns button.danger { border-color: #d94f63; color: #d94f63; }
.rp-btns button:hover { background: #f5f3ea; }
.rp-btns button:disabled { opacity: 0.5; }
.rp-error { color: #d94f63; }
.rp-loading { color: #8b8778; }

.rp-weather { border: 1.5px solid #211e15; border-radius: 14px; padding: 16px 18px; background: #fbfaf5; box-shadow: 0 1px 2px rgba(38,33,18,0.05), 0 10px 28px rgba(38,33,18,0.06); }
.rp-weather-main { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.rp-weather-icon { font-size: 44px; }
.rp-weather-main h3 { margin: 0; font-size: 19px; }
.rp-headline { margin: 4px 0 0; font-size: 14px; }
.rp-index { margin-left: auto; text-align: center; }
.rp-index b { display: block; font-size: 34px; color: #211e15; font-family: "Cascadia Code", "JetBrains Mono", Consolas, monospace; }
.rp-index span { font-size: 11px; color: #8b8778; }
.rp-items { list-style: none; margin: 12px 0 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 6px; }
.rp-items li { display: flex; gap: 8px; align-items: baseline; font-size: 13px; border-top: 1px dashed #d8d4c4; padding-top: 6px; }
.rp-time { margin-left: auto; color: #8b8778; font-size: 12px; font-family: "Cascadia Code", "JetBrains Mono", Consolas, monospace; }
.rp-type { font-size: 11px; border: 1px solid #c8c3af; border-radius: 5px; padding: 1px 8px; color: #4c483c; font-family: "Cascadia Code", "JetBrains Mono", Consolas, monospace; }
.rp-family { margin: 10px 0 0; font-size: 13px; background: #f5f3ea; border-radius: 8px; padding: 8px 10px; }

.rp-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 14px; }
@media (max-width: 860px) { .rp-grid { grid-template-columns: 1fr; } }
.rp-card { border: 1.5px solid #211e15; border-radius: 14px; background: #fbfaf5; padding: 14px 16px; box-shadow: 0 1px 2px rgba(38,33,18,0.05), 0 10px 28px rgba(38,33,18,0.06); }
.rp-card h3 { margin: 0 0 8px; font-size: 15px; }
.rp-card h3::before { content: "// "; font-family: "Cascadia Code", "JetBrains Mono", Consolas, monospace; color: #b6b2a1; }
.rp-chart { width: 100%; max-height: 130px; }
.rp-ring { width: 92px; flex: none; }
.rp-ring-num { font-size: 15px; font-weight: 700; fill: #211e15; }
.rp-rate { display: flex; gap: 14px; align-items: center; }
.rp-status { margin: 0 0 4px; font-weight: 600; }
.rp-warn { color: #d94f63; font-size: 12.5px; }
.rp-hint { color: #8b8778; font-size: 12.5px; line-height: 1.6; }

.rp-ledger { list-style: none; margin: 10px 0 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.rp-ledger li { border: 1px solid #d8d4c4; border-radius: 12px; padding: 10px 12px; background: #f5f3ea; }
.rp-ledger li.done { background: #eef5ef; border-color: #2e8f63; }
.rp-ledger-main { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
.rp-ledger-card { margin-top: 6px; font-size: 12px; color: #2f93a8; background: #f0f6f4; border: 1px dashed #2f93a8; border-radius: 6px; padding: 5px 8px; }
.rp-ledger-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.rp-fb { font-size: 12px; color: #8b8778; }
.rp-fb[data-fb="RESOLVED"] { color: #2e8f63; font-weight: 600; }
.rp-fb[data-fb="ESCALATED"] { color: #d94f63; font-weight: 600; }
.rp-btns { display: flex; gap: 6px; }

/* 适老化模式：大字号 + 真高对比 + 焦点可视 + 动效可关 */
.elder-mode { font-size: 18px; }
.elder-mode .rp-head h2 { font-size: 26px; }
.elder-mode .rp-headline, .elder-mode .rp-status { font-size: 18px; }
.elder-mode .rp-event, .elder-mode .rp-ledger-main b { font-size: 18px; }
.elder-mode .rp-hint, .elder-mode .rp-ledger-card, .elder-mode .rp-time { font-size: 15px; color: #3a362a; }
.elder-mode .rp-btns button, .elder-mode .rp-actions button { font-size: 17px; padding: 12px 18px; }
.elder-mode .rp-type { font-size: 14px; }
.elder-mode .rp-head p, .elder-mode .rp-index span, .elder-mode .rp-fb { color: #3a362a; }

/* 可访问性：键盘焦点可见、屏幕阅读器友好、跟随系统减弱动效 */
.rp-btns button:focus-visible, .rp-actions button:focus-visible { outline: 3px solid #2f93a8; outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) {
  .rp-ring circle { transition: none; }
}
.rp-share-text { width: 100%; border: 1px dashed #2f93a8; border-radius: 8px; padding: 10px; font-size: 14px; font-family: inherit; background: #f0f6f4; }
</style>
