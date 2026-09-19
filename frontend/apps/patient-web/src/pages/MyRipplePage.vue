<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { request, useAuthStore } from "@smart-cloud-brain/shared-api";
import { useSpeechRecognition } from "../composables/useSpeech";

/**
 * 我的安全涟漪 —— 患者端守护页（数字宣纸 · 水墨涟漪主题）
 *
 * 五块内容：
 * 1. 今日健康气象：用天气隐喻转译当日守护态势（晴/多云/大雨/暴雨），图标带呼吸节律
 * 2. 未来三天守护天气趋势：72小时预报聚合（与今日气象同一套天气语言）
 * 3. 涟漪曲线：历次健康事件推演的RII强度轨迹（患者健康数字孪生的雏形）
 * 4. 守护回执：时间学触达的干预回执（已缓解/未缓解/已就医），驱动涟漪消解闭环
 *
 * 关怀设计：适老化模式（大字号真高对比）+ 一键分享给家属 + 已就医二次确认
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

interface ForecastBucket {
  hourOffset: number;
  intensity: number;
  drivers: string[];
}

interface Forecast {
  horizonHours: number;
  buckets: ForecastBucket[];
  peak: { hourOffset: number; intensity: number; drivers: string[] };
  horizonAvg: number;
  activeTriggers: number;
  degraded?: boolean;
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
const forecast = ref<Forecast | null>(null);
const feedbackBusy = ref<number | null>(null);
const shared = ref(false);
const nlInputId = ref<number | null>(null);
const nlText = ref("");
const nlHint = ref("");
const familyLink = ref("");
const familyExpires = ref("");
const familyBusy = ref(false);
const { supported: speechSupported, listening: speechListening, start: speechStart, stop: speechStop } = useSpeechRecognition();
function dictateFeedback(item: { triggerId: number }) {
  if (speechListening.value) { speechStop(); return; }
  speechStart((text) => { nlText.value = text; });
}

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

/** 预报强度 → 守护天气色（与今日气象同一套语言：晴/多云/大雨/暴雨）。 */
function forecastColor(v: number): string {
  if (v >= 45) return "#bd4033";
  if (v >= 25) return "#c07a1d";
  if (v > 0) return "#a8871a";
  return "#41795f";
}

/** 72个逐小时桶聚合为24根3小时柱（取窗口内峰值），方便患者一眼看趋势。 */
const forecastColumns = computed(() => {
  if (!forecast.value?.buckets?.length) return [];
  const cols: { offset: number; intensity: number; drivers: string[] }[] = [];
  for (let i = 0; i < forecast.value.buckets.length; i += 3) {
    const slice = forecast.value.buckets.slice(i, i + 3);
    const top = slice.reduce((a, b) => (b.intensity > a.intensity ? b : a), slice[0]);
    cols.push({ offset: i, intensity: Math.round(top.intensity), drivers: top.drivers });
  }
  return cols;
});

const forecastPeakText = computed(() => {
  if (!forecast.value || !forecast.value.peak || forecast.value.peak.intensity <= 0) return "";
  const p = forecast.value.peak;
  const drivers = p.drivers.length ? p.drivers.slice(0, 2).join("、") : "守护事项";
  return p.hourOffset <= 1
    ? `接下来的1小时最需要注意：${drivers}`
    : `未来 ${p.hourOffset} 小时前后最需要注意：${drivers}`;
});

async function loadAll() {
  if (!patientId.value) return;
  loading.value = true;
  errorMsg.value = "";
  const token = auth.token();
  try {
    const [w, l, r, h, f] = await Promise.all([
      request<Weather>(`/api/health-weather/daily?patientId=${patientId.value}`, {}, token).catch(() => null),
      request<LedgerItem[]>(`/api/health-event/ripple/feedback-ledger?patientId=${patientId.value}`, {}, token).catch(() => []),
      request<Resolution>(`/api/health-event/ripple/resolution?patientId=${patientId.value}`, {}, token).catch(() => null),
      request<RippleHistoryItem[]>(`/api/health-event/ripple/patient/${patientId.value}`, {}, token).catch(() => []),
      request<Forecast>(`/api/health-event/ripple/forecast?patientId=${patientId.value}`, {}, token).catch(() => null),
    ]);
    weather.value = w;
    ledger.value = Array.isArray(l) ? l : [];
    resolution.value = r;
    history.value = Array.isArray(h) ? h : [];
    forecast.value = f && !f.degraded ? f : null;
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function sendFeedback(item: LedgerItem, outcome: string, noteOverride?: string) {
  // 已就医不可撤销（会计入气象警报与家属提示），必须二次确认防误触
  if (outcome === "ESCALATED"
    && !window.confirm("确认已完成就医、需要医生跟进吗？\n确认后该事项将终结并通知家属重点关注，不可撤销。")) {
    return;
  }
  feedbackBusy.value = item.triggerId;
  try {
    const note = noteOverride ?? (outcome === "RESOLVED" ? "已缓解" : outcome === "UNRESOLVED" ? "未缓解，需要加强关注" : "症状加重，已升级就医");
    await request(
      `/api/chrono/trigger/${item.triggerId}/feedback?outcome=${outcome}&note=${encodeURIComponent(note)}`,
      { method: "POST" },
      auth.token(),
    );
    await loadAll();
  } finally {
    feedbackBusy.value = null;
  }
}

/**
 * 对话式回执（第七轮）：患者用自己的话说情况，本地关键词规则解析为三态。
 * 解析完全在本地、确定性执行——解析不了就明确说"没听懂"，绝不臆造回执。
 */
function parseOutcome(text: string): "RESOLVED" | "UNRESOLVED" | "ESCALATED" | null {
  const t = text.trim();
  if (!t) {
    return null;
  }
  if (/(去医院|到医院|在医[院院]|住院|急诊|挂了?急|就[医疹]|看医生|120)/.test(t)) {
    return "ESCALATED";
  }
  if (/(没[有好]转|不见[好坏]|加重|严重|还是|依旧|仍然|反复|更[疼肿重]|老样子)/.test(t)) {
    return "UNRESOLVED";
  }
  if (/(好转|好[多了很]|缓解|消失|不[疼肿咳]|恢复|没事|正常)/.test(t)) {
    return "RESOLVED";
  }
  return null;
}

const OUTCOME_LABEL: Record<string, string> = {
  RESOLVED: "已缓解",
  UNRESOLVED: "未缓解，将加强守护",
  ESCALATED: "已升级就医",
};

async function submitNlFeedback(item: LedgerItem) {
  const text = nlText.value.trim();
  if (!text) {
    nlHint.value = "请先说说情况，或直接点下面的按钮。";
    return;
  }
  const outcome = parseOutcome(text);
  if (!outcome) {
    nlHint.value = "没听懂这句话——可以点「好转了 / 没好转 / 去了医院」快捷按钮，或换个说法。";
    return;
  }
  nlHint.value = "";
  await sendFeedback(item, outcome, `患者自述："${text.slice(0, 120)}"（解析为${OUTCOME_LABEL[outcome]}）`);
  nlText.value = "";
  nlInputId.value = null;
}

function toggleNl(item: LedgerItem) {
  nlInputId.value = nlInputId.value === item.triggerId ? null : item.triggerId;
  nlText.value = "";
  nlHint.value = "";
}

function toggleElder() {
  elderMode.value = !elderMode.value;
  localStorage.setItem("scb-elder-mode", elderMode.value ? "1" : "0");
}

/** 家属守护圈：生成 HMAC 签名的只读分享链接（7天有效）。
 *  最小披露：家属只见天气/趋势/家属须知/消解率，不含姓名/诊断/决策记录。 */
async function createFamilyLink() {
  if (familyBusy.value) {
    return;
  }
  familyBusy.value = true;
  try {
    const res = await request<{ path: string; expiresAt: string }>(
      `/api/health-event/share/link?patientId=${patientId.value}&days=7`,
      { method: "POST" },
      auth.token(),
    );
    familyLink.value = location.origin + res.path;
    familyExpires.value = res.expiresAt.replace("T", " ").slice(0, 16);
  } catch {
    errorMsg.value = "守护圈链接生成失败，请稍后再试。";
  } finally {
    familyBusy.value = false;
  }
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
    <!-- 页首：印 + 衬线标题 -->
    <header class="rp-head">
      <div class="rp-head-left">
        <span class="rp-seal">涟</span>
        <div>
          <h2>我的健康涟漪<span class="rp-en mono">MY HEALTH RIPPLE</span></h2>
          <p>每一次诊断都会在生活中激起涟漪——这里能看到它，也能让它平息</p>
        </div>
      </div>
      <div class="rp-actions">
        <button class="ghost-btn" type="button" @click="createFamilyLink" :disabled="familyBusy">
          {{ familyBusy ? "生成中…" : "家属守护圈" }}
        </button>
        <button class="ghost-btn" type="button" @click="shareToFamily">
          {{ shared ? "✓ 已复制给家属" : "分享文字摘要" }}
        </button>
        <button class="ghost-btn" type="button" @click="toggleElder">{{ elderMode ? "标准字号" : "适老化大字" }}</button>
      </div>
    </header>

    <!-- 家属守护圈链接（生成后展示 + 复制） -->
    <section v-if="familyLink" class="rp-panel" role="region" aria-label="家属守护圈链接">
      <header class="sec-head">
        <span class="dot" style="background: #41795f" />
        <h3>家属守护圈链接已生成</h3>
        <span class="en mono">FAMILY GUARDIAN CIRCLE</span>
        <span class="spacer" />
        <span class="fig mono">READ-ONLY · 7天有效</span>
      </header>
      <div class="panel-body">
        <p class="rp-hint">把下面的链接发给家属（微信/短信均可）。家属打开后可以看到你的守护态势和"家属须知"，
          <b>看不到姓名、诊断和病历记录</b>——过期自动失效（{{ familyExpires }}）。</p>
        <div class="rp-family-link">
          <input class="rp-link-input" readonly :value="familyLink" @focus="($event.target as HTMLInputElement).select()" />
          <button class="ghost-btn" type="button" @click="copyText(familyLink).then(ok => { if (ok) { shared = true; setTimeout(() => (shared = false), 2500); } })">
            {{ shared ? "✓ 已复制" : "复制链接" }}
          </button>
        </div>
      </div>
    </section>

    <p v-if="errorMsg" class="rp-error" role="status" aria-live="polite">守护数据暂时加载不出来，请稍后下拉重试；如持续失败请联系您的医生或社区工作人员。</p>
    <p v-if="loading" class="rp-loading" role="status">正在加载守护数据…</p>

    <!-- 剪贴板不可用时的手动复制兜底（http 局域网环境常见） -->
    <section v-if="shareFallback" class="rp-panel" role="region" aria-label="复制分享内容">
      <header class="sec-head">
        <span class="dot" style="background: #41795f" />
        <h3>请手动复制给家属</h3>
        <span class="spacer" />
        <span class="fig mono">SHARE</span>
      </header>
      <div class="panel-body">
        <textarea class="rp-share-text" readonly rows="5" @focus="($event.target as HTMLTextAreaElement).select()">{{ shareText }}</textarea>
        <p class="rp-hint">点击文本框全选后，用"复制"或长按复制发给家属。</p>
        <button class="ghost-btn" type="button" @click="shareFallback = false">关闭</button>
      </div>
    </section>

    <!-- FIG.P1 今日健康气象（呼吸节律） -->
    <section v-if="weather" class="rp-panel weather-panel">
      <header class="sec-head">
        <span class="dot" :style="{ background: weather.color }" />
        <h3>今日健康气象</h3>
        <span class="en mono">HEALTH WEATHER DAILY</span>
        <span class="spacer" />
        <span class="fig mono">{{ weather.date }}</span>
      </header>
      <div class="panel-body">
        <div class="rp-weather-main">
          <div class="breath-halo" :style="{ color: weather.color }" aria-hidden="true">
            <span class="halo-ring" />
            <span class="halo-ring r2" />
            <span class="halo-core">{{ WEATHER_ICON[weather.weather] ?? "☁️" }}</span>
          </div>
          <div class="rp-weather-text">
            <h3 :style="{ color: weather.color }" class="weather-label">{{ weather.weatherLabel }}</h3>
            <p class="rp-headline">{{ weather.headline }}</p>
          </div>
          <div class="rp-index">
            <b class="mono">{{ weather.index }}</b>
            <span>今日守护指数</span>
          </div>
        </div>
        <ul v-if="weather.items.length" class="rp-items">
          <li v-for="item in weather.items" :key="item.event + item.triggerTime">
            <span class="rp-type mono" :data-type="item.chronoType">{{ TYPE_LABEL[item.chronoType] ?? item.chronoType }}</span>
            <span class="rp-event">{{ item.event }}</span>
            <span class="rp-time mono">{{ item.triggerTime }}</span>
          </li>
        </ul>
        <p v-if="weather.familyTip" class="rp-family">👨‍👩‍👧 家属须知：{{ weather.familyTip }}</p>
        <p class="rp-model mono">{{ weather.model }}</p>
      </div>
    </section>

    <!-- FIG.P2 未来三天守护天气趋势 -->
    <section v-if="forecast && forecastColumns.length" class="rp-panel">
      <header class="sec-head">
        <span class="dot" style="background: #37808a" />
        <h3>未来三天守护天气趋势</h3>
        <span class="en mono">72H RIPPLE FORECAST</span>
        <span class="spacer" />
        <span class="fig mono">FIG.P2</span>
      </header>
      <div class="panel-body">
        <p class="rp-hint">每一格是 3 小时——颜色越暖表示那个时段越需要当心。这是由你的守护计划计算出来的趋势，每缓解一项，格子就会降下去。</p>
        <div class="rp-fc" role="img" aria-label="未来72小时守护强度趋势图">
          <div v-for="col in forecastColumns" :key="col.offset" class="rp-fc-col"
               :title="`${col.offset}小时后 · 强度${col.intensity}${col.drivers.length ? ' · ' + col.drivers.join('、') : ''}`">
            <div class="rp-fc-bar" :style="{ height: Math.max(4, Math.min(72, col.intensity)) + 'px', background: forecastColor(col.intensity) }" />
            <span v-if="col.offset % 24 === 0" class="rp-fc-t mono">+{{ col.offset }}h</span>
          </div>
        </div>
        <p v-if="forecastPeakText" class="rp-fc-peak">⏰ {{ forecastPeakText }}</p>
      </div>
    </section>

    <!-- FIG.P3 涟漪曲线 + FIG.P4 消解环 -->
    <section class="rp-grid">
      <div class="rp-panel">
        <header class="sec-head">
          <span class="dot" style="background: #37808a" />
          <h3>涟漪曲线（RII 轨迹）</h3>
          <span class="en mono">RII TRAJECTORY</span>
          <span class="spacer" />
          <span class="fig mono">FIG.P3</span>
        </header>
        <div class="panel-body">
          <svg v-if="riiPoints.length >= 2" viewBox="0 0 320 120" class="rp-chart">
            <polyline
              :points="riiPoints.map((p, i) => `${20 + (i * 280) / (riiPoints.length - 1)},${105 - Math.min(100, p.index)}`).join(' ')"
              fill="none" stroke="#37808a" stroke-width="2.5" stroke-linejoin="round"
            />
            <circle
              v-for="(p, i) in riiPoints" :key="i"
              :cx="20 + (i * 280) / (riiPoints.length - 1)" :cy="105 - Math.min(100, p.index)"
              r="3.5" fill="#37808a"
            >
              <title>{{ p.diagnosis }}：RII={{ p.index }}</title>
            </circle>
          </svg>
          <p v-else class="rp-hint">完成至少两次健康事件推演后，这里会出现你的涟漪强度轨迹。</p>
        </div>
      </div>

      <div class="rp-panel" v-if="resolution">
        <header class="sec-head">
          <span class="dot" style="background: #41795f" />
          <h3>守护回执 · 涟漪消解</h3>
          <span class="en mono">RESOLUTION</span>
          <span class="spacer" />
          <span class="fig mono">FIG.P4</span>
        </header>
        <div class="panel-body">
          <div class="rp-rate">
            <svg viewBox="0 0 90 90" class="rp-ring">
              <circle cx="45" cy="45" r="38" fill="none" stroke="#e5dfcb" stroke-width="9" />
              <circle
                cx="45" cy="45" r="38" fill="none" stroke="#41795f" stroke-width="9"
                stroke-linecap="round" stroke-dasharray="238.6"
                :stroke-dashoffset="238.6 * (1 - Math.min(100, resolution.resolutionRate) / 100)"
                transform="rotate(-90 45 45)"
              />
              <text x="45" y="50" text-anchor="middle" class="rp-ring-num mono">{{ resolution.resolutionRate }}%</text>
            </svg>
            <div>
              <p class="rp-status">{{ resolution.closureStatus }}</p>
              <p class="rp-hint">已缓解 {{ resolution.resolvedCount }} / 共 {{ resolution.totalTriggers }} 项</p>
              <p v-if="resolution.escalatedCount" class="rp-warn">⚠ {{ resolution.escalatedCount }} 项已升级就医</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- FIG.P5 守护事项回执 -->
    <section class="rp-panel">
      <header class="sec-head">
        <span class="dot" style="background: #bd4033" />
        <h3>守护事项回执</h3>
        <span class="en mono">GUARD LEDGER / FEEDBACK</span>
        <span class="spacer" />
        <span class="fig mono">FIG.P5</span>
      </header>
      <div class="panel-body">
        <p class="rp-hint">这是智能体为你主动设置的守护计划——完成后点击回执，涟漪就会消解。</p>
        <div v-if="!ledger.length" class="rp-hint">暂无守护事项，完成一次就诊推演后这里会出现主动守护计划。</div>
        <ul class="rp-ledger">
          <li v-for="item in ledger" :key="item.triggerId" :class="{ done: item.feedbackStatus === 'RESOLVED' }">
            <div class="rp-ledger-main">
              <span class="rp-type mono" :data-type="item.chronoType">{{ TYPE_LABEL[item.chronoType] ?? item.chronoType }}</span>
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
            <!-- 对话式回执：用自己的话说情况，本地确定性解析（不联网、不臆造） -->
            <div v-if="item.feedbackStatus !== 'RESOLVED' && item.feedbackStatus !== 'ESCALATED'" class="rp-nl">
              <button v-if="nlInputId !== item.triggerId" type="button" class="nl-toggle" @click="toggleNl(item)">✎ 说说情况（文字回执）</button>
              <div v-else class="nl-box">
                <input
                  v-model="nlText"
                  class="nl-input"
                  type="text"
                  :placeholder="'例如：好多了 / 还是没好转 / 已经去医院'"
                  @keyup.enter="submitNlFeedback(item)"
                />
                <button v-if="speechSupported" type="button" class="nl-mic" :class="{ on: speechListening }"
                        :title="speechListening ? '正在听…' : '语音说情况'"
                        @click="dictateFeedback(item)">{{ speechListening ? "● 听中" : "🎤" }}</button>
                <div class="nl-chips">
                  <button type="button" @click='nlText = "好多了"; submitNlFeedback(item)'>好转了</button>
                  <button type="button" @click='nlText = "还是没好转"; submitNlFeedback(item)'>没好转</button>
                  <button type="button" class="danger" @click='nlText = "已经去医院"; submitNlFeedback(item)'>去了医院</button>
                </div>
                <button type="button" class="nl-send" :disabled="feedbackBusy === item.triggerId" @click="submitNlFeedback(item)">提交</button>
              </div>
              <p v-if="nlInputId === item.triggerId && nlHint" class="nl-hint" role="status">{{ nlHint }}</p>
            </div>
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ripple-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 20px 24px 30px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  color: var(--ink);
}

/* ---------- 页首 ---------- */
.rp-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  border-bottom: 2px solid var(--ink);
  padding-bottom: 12px;
}

.rp-head-left { display: flex; align-items: center; gap: 14px; }

.rp-seal {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  font-size: 24px;
  font-weight: 800;
  color: #f6f3e8;
  background: var(--primary);
  border-radius: 10px;
  transform: rotate(-3deg);
  box-shadow: 0 2px 10px rgba(189, 64, 51, 0.26);
  flex: none;
}

.rp-head h2 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 23px;
  letter-spacing: 1px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.rp-en { font-size: 9px; color: var(--muted); letter-spacing: 2px; font-family: var(--font-mono); }

.rp-head p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }

.rp-actions { display: flex; gap: 8px; }

.rp-family-link { display: flex; gap: 8px; align-items: center; }
.rp-link-input {
  flex: 1;
  min-width: 0;
  border: 1px dashed var(--info);
  border-radius: 8px;
  padding: 9px 11px;
  font-size: 13px;
  font-family: var(--font-mono);
  color: var(--info);
  background: rgba(51, 98, 143, 0.05);
}

.ghost-btn {
  border: 1.5px solid var(--ink);
  background: transparent;
  color: var(--ink);
  border-radius: 9px;
  padding: 7px 13px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.ghost-btn:hover { background: var(--ink); color: #f6f3e8; }

.rp-error { color: var(--danger); }
.rp-loading { color: var(--muted); }

/* ---------- 面板（与大屏 .panel 同构） ---------- */
.rp-panel {
  border: 1.5px solid var(--line-strong);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.sec-head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--line);
}

.sec-head .dot { width: 9px; height: 9px; border-radius: 50%; flex: none; }

.sec-head h3 {
  margin: 0;
  font-family: var(--font-serif);
  font-size: 15.5px;
  letter-spacing: 0.6px;
  color: var(--ink);
}

.sec-head .en { font-size: 8.5px; color: var(--subtle); letter-spacing: 1.6px; }
.sec-head .spacer { flex: 1; }
.fig { font-size: 9px; color: var(--muted); letter-spacing: 1.2px; border: 1px solid var(--line-strong); border-radius: 5px; padding: 2px 7px; }
.panel-body { padding: 13px 16px; display: flex; flex-direction: column; gap: 10px; }
.mono { font-family: var(--font-mono); }

/* ---------- FIG.P1 气象：一块晕开的天空 ---------- */
.rp-weather-main {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  position: relative;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background:
    radial-gradient(460px 150px at 14% 0%, rgba(51, 98, 143, 0.075), transparent 72%),
    radial-gradient(340px 130px at 88% 4%, rgba(189, 64, 51, 0.055), transparent 70%),
    var(--surface-alt);
  overflow: hidden;
}
.weather-label { margin: 0; font-family: var(--font-serif); font-size: 24px; letter-spacing: 1px; }
.rp-headline { margin: 5px 0 0; font-size: 14px; color: var(--ink-soft); line-height: 1.6; max-width: 560px; }

.rp-index { margin-left: auto; text-align: center; }
.rp-index b { display: block; font-size: 38px; line-height: 1; color: var(--ink); font-variant-numeric: tabular-nums; }
.rp-index span { font-size: 11px; color: var(--muted); letter-spacing: 1px; }

.rp-items {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 6px;
}

.rp-items li {
  display: flex;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
  border-top: 1px dashed var(--line-strong);
  padding-top: 6px;
}

.rp-time { margin-left: auto; color: var(--muted); font-size: 12px; }

.rp-type {
  font-size: 11px;
  border: 1px solid var(--line-strong);
  border-radius: 5px;
  padding: 1px 8px;
  color: var(--ink-soft);
  background: var(--surface-alt);
}

.rp-family { margin: 4px 0 0; font-size: 13px; background: var(--surface-alt); border: 1px dashed var(--line-strong); border-radius: 8px; padding: 8px 10px; }
.rp-model { margin: 0; font-size: 9.5px; color: var(--subtle); letter-spacing: 0.4px; }

/* ---------- FIG.P2 趋势条 ---------- */
.rp-fc {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 104px;
  padding: 8px 4px 0;
  border-bottom: 2px solid var(--ink);
}

.rp-fc-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; min-width: 0; }
.rp-fc-bar {
  width: 100%;
  border-radius: 4px 4px 0 0;
  transition: filter 0.15s ease, transform 0.15s ease;
}
.rp-fc-col:hover .rp-fc-bar { filter: brightness(1.08) saturate(1.1); transform: translateY(-1px); }
.rp-fc-t { font-size: 9px; color: var(--muted); margin-top: 3px; }
.rp-fc-peak { margin: 0; font-size: 13.5px; color: var(--primary); font-weight: 700; font-family: var(--font-serif); }

/* ---------- FIG.P3/P4 ---------- */
.rp-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 14px; }
@media (max-width: 860px) { .rp-grid { grid-template-columns: 1fr; } }
.rp-chart { width: 100%; max-height: 130px; }
.rp-ring { width: 92px; flex: none; }
.rp-ring-num { font-size: 15px; font-weight: 700; fill: var(--ink); }
.rp-rate { display: flex; gap: 14px; align-items: center; }
.rp-status { margin: 0 0 4px; font-weight: 700; font-family: var(--font-serif); }
.rp-warn { color: var(--danger); font-size: 12.5px; }
.rp-hint { color: var(--muted); font-size: 12.5px; line-height: 1.6; margin: 0; }

/* ---------- FIG.P5 回执 ---------- */
.rp-ledger { list-style: none; margin: 4px 0 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }

.rp-ledger li {
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--surface-alt);
}

.rp-ledger li.done { background: rgba(65, 121, 95, 0.09); border-color: var(--success); }
.rp-ledger-main { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
.rp-ledger-main b { font-size: 14px; }

.rp-ledger-card {
  margin-top: 6px;
  font-size: 12px;
  color: var(--info);
  background: rgba(51, 98, 143, 0.07);
  border: 1px dashed var(--info);
  border-radius: 6px;
  padding: 5px 8px;
}

.rp-ledger-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.rp-fb { font-size: 12px; color: var(--muted); }
.rp-fb[data-fb="RESOLVED"] { color: var(--success); font-weight: 700; }
.rp-fb[data-fb="ESCALATED"] { color: var(--danger); font-weight: 700; }

.rp-btns { display: flex; gap: 6px; }
.rp-btns button {
  border: 1.5px solid var(--success);
  background: transparent;
  color: var(--success);
  border-radius: 9px;
  padding: 6px 12px;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.rp-btns button.warn { border-color: var(--warning); color: var(--warning); }
.rp-btns button.danger { border-color: var(--danger); color: var(--danger); }
.rp-btns button:hover { background: var(--surface); }
.rp-btns button:disabled { opacity: 0.5; }

/* 对话式回执（文字） */
.rp-nl { margin-top: 8px; }
.nl-toggle {
  border: 1px dashed var(--line-strong);
  background: transparent;
  color: var(--muted);
  border-radius: 8px;
  padding: 5px 11px;
  font-size: 12px;
  cursor: pointer;
}
.nl-toggle:hover { color: var(--primary); border-color: var(--primary); }
.nl-box { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.nl-input {
  flex: 1;
  min-width: 200px;
  border: 1.5px solid var(--line-strong);
  border-radius: 8px;
  padding: 8px 11px;
  font-size: 14px;
  font-family: inherit;
  background: var(--surface);
  color: var(--ink);
}
.nl-input:focus { border-color: var(--primary); outline: var(--focus); }
.nl-chips { display: flex; gap: 6px; }
.nl-chips button {
  border: 1.5px solid var(--success);
  background: transparent;
  color: var(--success);
  border-radius: 999px;
  padding: 6px 13px;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}
.nl-chips button.danger { border-color: var(--danger); color: var(--danger); }
.nl-chips button:hover { background: var(--surface); }
.nl-send {
  border: none;
  background: var(--primary);
  color: #f6f3e8;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.nl-send:disabled { opacity: 0.5; }
.nl-mic {
  border: 1.5px dashed var(--line-strong);
  background: transparent;
  color: var(--muted);
  border-radius: 999px;
  padding: 8px 13px;
  font-size: 14px;
  cursor: pointer;
}
.nl-mic.on { color: var(--primary); border-color: var(--primary); animation: mic-pulse 1.2s ease-in-out infinite; }
@keyframes mic-pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.55; } }
@media (prefers-reduced-motion: reduce) { .nl-mic.on { animation: none; } }
.nl-hint { margin: 6px 0 0; font-size: 12.5px; color: var(--muted); }

.rp-share-text {
  width: 100%;
  border: 1px dashed var(--info);
  border-radius: 8px;
  padding: 10px;
  font-size: 14px;
  font-family: inherit;
  background: rgba(51, 98, 143, 0.06);
  color: var(--ink);
}

/* ---------- 适老化：大字号 + 真高对比 + 焦点可视 ---------- */
.elder-mode { font-size: 18px; }
.elder-mode .rp-head h2 { font-size: 27px; }
.elder-mode .rp-headline, .elder-mode .rp-status { font-size: 18px; }
.elder-mode .rp-ledger-main b { font-size: 18px; }
.elder-mode .weather-label { font-size: 24px; }
.elder-mode .rp-hint, .elder-mode .rp-ledger-card, .elder-mode .rp-time { font-size: 15px; color: #3a362a; }
.elder-mode .rp-btns button, .elder-mode .ghost-btn { font-size: 17px; padding: 12px 18px; }
.elder-mode .nl-input { font-size: 17px; padding: 12px 14px; }
.elder-mode .nl-chips button, .elder-mode .nl-toggle, .elder-mode .nl-send { font-size: 16px; padding: 10px 16px; }
.elder-mode .rp-type { font-size: 14px; }
.elder-mode .rp-head p, .elder-mode .rp-index span, .elder-mode .rp-fb { color: #3a362a; }
.elder-mode .sec-head h3 { font-size: 19px; }

.ghost-btn:focus-visible, .rp-btns button:focus-visible { outline: 3px solid var(--info); outline-offset: 2px; }

@media (prefers-reduced-motion: reduce) {
  .rp-fc-bar, .halo-core { transition: none; }
}
</style>
