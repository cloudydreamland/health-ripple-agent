# -*- coding: utf-8 -*-
"""一次性脚本：App.vue 版式重构为英雄三栏（VitaSphere 方向）。"""
import io

P = r"E:\Dumate\frontend\apps\ripple-console\src\App.vue"
s = io.open(P, encoding="utf-8").read()

# 1. 引入新组件
s = s.replace(
    'import CommunityRadar, { type RadarData } from "./components/CommunityRadar.vue";',
    'import CommunityRadar, { type RadarData } from "./components/CommunityRadar.vue";\n'
    'import PatientOverviewCard from "./components/PatientOverviewCard.vue";\n'
    'import AIInsightsPanel from "./components/AIInsightsPanel.vue";\n'
    'import CarePathway from "./components/CarePathway.vue";',
)

# 2. computed：护理路径阶段数据
s = s.replace(
    "const radar = ref<RadarData | null>(null);",
    "const radar = ref<RadarData | null>(null);\n"
    "const hasReviewAny = computed(() => (ripple.value?.chronoTriggers ?? []).some((t) => t.reviewStatus));\n"
    'const activeTriggerCount = computed(() => (ripple.value?.chronoTriggers ?? []).filter((t) => t.status === "ACTIVE").length);\n'
    'const resolvedTriggerCount = computed(() => (ripple.value?.chronoTriggers ?? []).filter((t) => t.feedbackStatus === "RESOLVED").length);',
)

OLD_BLOCK = """    <!-- 主视觉：RII 山脊剖面（技术图纸角标） -->
    <div class="grid-hero">
      <section class="panel corner-ticks">
        <header class="sec-head">
          <span class="dot" style="background: var(--red)" />
          <h2>涟漪强度山脊剖面</h2>
          <span class="en">RII RIDGE PROFILE / RING DECAY e^(&minus;0.22·(r&minus;1))</span>
          <span class="spacer" />
          <span class="fig">FIG.01</span>
        </header>
        <div class="panel-body ridge-layout">
          <RiiSummary :intensity="intensity" />
          <RidgePlot :dimensions="dimensions" :intensity="intensity" />
        </div>
      </section>
    </div>

    <!-- 活水涟漪池 + 活动日志 -->
    <div class="grid-a">
      <section class="panel corner-ticks">
        <header class="sec-head">
          <span class="dot" style="background: var(--violet)" />
          <h2>活水涟漪池 · 五维图谱</h2>
          <span class="en">RIPPLE POND / {{ ripple?.healthEvent?.diagnosis ?? "&mdash;" }}</span>
          <span class="spacer" />
          <span v-if="ripple?.proactiveAssessment" class="tag GREEN">{{ ripple.proactiveAssessment.proactiveAction }}</span>
          <span class="fig">FIG.02</span>
        </header>
        <div class="panel-body">
          <RipplePond
            :dimensions="dimensions"
            :health-event="ripple?.healthEvent ?? { diagnosis: '', drugs: [], pastHistory: '' }"
            :intensity="intensity"
            :selected="selectedNode"
            @select="selectedNode = $event"
          />
        </div>
      </section>

      <section class="panel">
        <header class="sec-head">
          <span class="dot" style="background: var(--green)" />
          <h2>守护活动日志</h2>
          <span class="en">ACTIVITY LOG</span>
          <span class="spacer" />
          <span class="fig">{{ String(activityEvents.length).padStart(3, "0") }} EVENTS</span>
        </header>
        <div class="panel-body">
          <ActivityLog :events="activityEvents" />
        </div>
      </section>
    </div>"""

NEW_BLOCK = """    <!-- 英雄三栏：左患者概览+AI洞察 ｜ 中·涟漪池主视觉 ｜ 右·RII+活动日志 -->
    <div class="hero-row">
      <div class="hero-left">
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--blue)" />
            <h2>患者概览</h2>
            <span class="spacer" />
            <span class="fig">CASE</span>
          </header>
          <div class="panel-body">
            <PatientOverviewCard
              :patient-id="patientId"
              :health-event="ripple?.healthEvent ?? null"
              :mode="mode"
              @run="run()"
            />
          </div>
        </section>
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--cyan)" />
            <h2>AI 洞察</h2>
            <span class="spacer" />
            <span class="fig">INSIGHTS</span>
          </header>
          <div class="panel-body">
            <AIInsightsPanel
              :proactive="ripple?.proactiveAssessment ?? null"
              :top-risks="intensity?.topRisks ?? []"
              :guardrail="ripple?.counterfactualTree?.guardrailSummary ?? null"
              :forecast-peak="forecast?.peak ?? null"
            />
          </div>
        </section>
      </div>

      <section class="panel corner-ticks pond-hero">
        <header class="sec-head">
          <span class="dot" style="background: var(--violet)" />
          <h2>活水涟漪池 · 五维图谱</h2>
          <span class="en">RIPPLE POND · LIVE</span>
          <span class="spacer" />
          <span v-if="ripple?.proactiveAssessment" class="tag GREEN">{{ ripple.proactiveAssessment.proactiveAction }}</span>
          <span class="fig">FIG.01</span>
        </header>
        <div class="panel-body pond-body">
          <RipplePond
            :dimensions="dimensions"
            :health-event="ripple?.healthEvent ?? { diagnosis: '', drugs: [], pastHistory: '' }"
            :intensity="intensity"
            :selected="selectedNode"
            @select="selectedNode = $event"
          />
        </div>
      </section>

      <div class="hero-right">
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--red)" />
            <h2>涟漪强度</h2>
            <span class="spacer" />
            <span class="fig">FIG.02</span>
          </header>
          <div class="panel-body">
            <RiiSummary :intensity="intensity" />
          </div>
        </section>
        <section class="panel">
          <header class="sec-head">
            <span class="dot" style="background: var(--green)" />
            <h2>守护活动日志</h2>
            <span class="spacer" />
            <span class="fig">{{ String(activityEvents.length).padStart(3, "0") }}</span>
          </header>
          <div class="panel-body">
            <ActivityLog :events="activityEvents" />
          </div>
        </section>
      </div>
    </div>

    <!-- 护理路径 -->
    <div class="grid-path">
      <section class="panel">
        <div class="panel-body">
          <CarePathway
            :has-ripple="!!ripple"
            :has-guardrail="!!ripple?.counterfactualTree?.guardrailSummary"
            :has-evidence="!!ripple?.evidenceChain"
            :has-review="hasReviewAny"
            :active-triggers="activeTriggerCount"
            :resolved-count="resolvedTriggerCount"
          />
        </div>
      </section>
    </div>

    <!-- 主视觉：RII 山脊剖面（技术图纸角标） -->
    <div class="grid-hero">
      <section class="panel corner-ticks">
        <header class="sec-head">
          <span class="dot" style="background: var(--red)" />
          <h2>涟漪强度山脊剖面</h2>
          <span class="en">RII RIDGE PROFILE / RING DECAY e^(&minus;0.22·(r&minus;1))</span>
          <span class="spacer" />
          <span class="fig">FIG.03</span>
        </header>
        <div class="panel-body ridge-layout">
          <RidgePlot :dimensions="dimensions" :intensity="intensity" />
        </div>
      </section>
    </div>"""

if OLD_BLOCK in s:
    s = s.replace(OLD_BLOCK, NEW_BLOCK)
    print("layout block replaced (exact)")
else:
    # 宽松匹配：定位 grid-hero 注释到 grid-a 结束
    import re
    pat = re.compile(
        r"    <!-- 主视觉：RII 山脊剖面（技术图纸角标） -->\n    <div class=\"grid-hero\">[\s\S]*?    <!-- 活水涟漪池 \+ 活动日志 -->\n    <div class=\"grid-a\">[\s\S]*?\n      </section>\n    </div>\n",
    )
    m = pat.search(s)
    assert m, "layout block not found even loosely"
    s = s[: m.start()] + NEW_BLOCK + s[m.end():]
    print("layout block replaced (loose)")

# 4. 版式 CSS：hero 三栏 + 护理路径行 + ridge 单列
OLD_CSS = ".ridge-layout { display: grid; grid-template-columns: 250px 1fr; gap: 18px; }"
NEW_CSS = (
    ".hero-row { display: grid; grid-template-columns: 300px 1fr 320px; gap: 14px; align-items: stretch; }\n"
    "@media (max-width: 1500px) { .hero-row { grid-template-columns: 280px 1fr; }\n"
    "  .hero-right { grid-column: 1 / -1; display: grid; grid-template-columns: 1fr 1fr; gap: 14px; } }\n"
    "@media (max-width: 1100px) { .hero-row { grid-template-columns: 1fr; } .hero-right { grid-template-columns: 1fr; } }\n"
    ".hero-left, .hero-right { display: flex; flex-direction: column; gap: 14px; min-width: 0; }\n"
    ".hero-right .panel-body { max-height: 320px; overflow-y: auto; }\n"
    ".pond-hero .pond-body { display: flex; justify-content: center; }\n"
    ".grid-path { display: grid; grid-template-columns: 1fr; gap: 14px; }\n"
    ".ridge-layout { display: grid; grid-template-columns: 1fr; gap: 18px; }"
)
if OLD_CSS in s:
    s = s.replace(OLD_CSS, NEW_CSS)
    print("css replaced (exact)")
else:
    import re as _re
    pat2 = _re.compile(r"\.ridge-layout \{ display: grid; grid-template-columns: [^}]+\}")
    assert pat2.search(s), "ridge-layout css not found"
    s = pat2.sub(NEW_CSS, s, count=1)
    print("css replaced (loose)")

io.open(P, "w", encoding="utf-8").write(s)
print("DONE")
