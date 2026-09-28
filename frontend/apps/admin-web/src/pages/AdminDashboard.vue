<script setup lang="ts">
import { computed, inject, ref, type Ref } from "vue";
import { storeToRefs } from "pinia";
import { useAdminWorkflowStore } from "@smart-cloud-brain/shared-api";

const emit = defineEmits<{ refresh: [] }>();
const workflow = useAdminWorkflowStore();
const { departments, doctors, drugs, schedules, triageDesk, refreshErrors } = storeToRefs(workflow);
const dashboardStatus = inject<{ loading: Ref<boolean>; loaded: Ref<boolean> }>(
  "admin-dashboard-status",
  { loading: ref(false), loaded: ref(true) },
);
const initialLoading = computed(() => dashboardStatus.loading.value && !dashboardStatus.loaded.value);
const highRisk = computed(() => triageDesk.value.filter((item) => ["MANUAL_REQUIRED", "HIGH"].includes(String(item.status))).length);

const metrics = computed(() => [
  { label: "科室", value: departments.value.length, key: "departments", to: "/departments", marker: "01" },
  { label: "医生", value: doctors.value.length, key: "doctors", to: "/doctors", marker: "02" },
  { label: "药品", value: drugs.value.length, key: "drugs", to: "/drugs", marker: "03" },
  { label: "待处理分诊", value: highRisk.value, key: "triageDesk", to: "/triage-desk", marker: "04" },
]);
const failedSources = computed(() =>
  Object.entries(refreshErrors.value).filter(([key]) => ["departments", "doctors", "drugs", "schedules", "triageDesk"].includes(key)),
);

const triageTotal = computed(() => triageDesk.value.length);
const triageBuckets = computed(() => {
  const counts = new Map<string, number>();
  triageDesk.value.forEach((item) => {
    const status = String(item.status ?? "").toUpperCase();
    counts.set(status, (counts.get(status) ?? 0) + 1);
  });
  const manual = (counts.get("MANUAL_REQUIRED") ?? 0) + (counts.get("HIGH") ?? 0);
  const known = new Set(["MANUAL_REQUIRED", "HIGH", "AI_RECOMMENDED", "ASSIGNED", "COMPLETED", "CLOSED"]);
  const other = [...counts.entries()].reduce((sum, [status, count]) => sum + (known.has(status) ? 0 : count), 0);
  return [
    { label: "待人工处理", count: manual, tone: "attention" },
    { label: "智能建议", count: counts.get("AI_RECOMMENDED") ?? 0, tone: "suggested" },
    { label: "已分配医生", count: counts.get("ASSIGNED") ?? 0, tone: "assigned" },
    { label: "已完成", count: counts.get("COMPLETED") ?? 0, tone: "complete" },
    ...(counts.get("CLOSED") ? [{ label: "已关闭", count: counts.get("CLOSED") ?? 0, tone: "closed" }] : []),
    ...(other ? [{ label: "其他状态", count: other, tone: "other" }] : []),
  ];
});

function localDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return year + "-" + month + "-" + day;
}

const sevenDays = computed(() => {
  const counts = new Map<string, number>();
  schedules.value.forEach((item) => {
    if (String(item.status ?? "").toUpperCase() !== "PUBLISHED") return;
    const key = String(item.workDate ?? "");
    counts.set(key, (counts.get(key) ?? 0) + 1);
  });
  const today = new Date();
  today.setHours(12, 0, 0, 0);
  return Array.from({ length: 7 }, (_, offset) => {
    const date = new Date(today);
    date.setDate(today.getDate() + offset);
    const key = localDateKey(date);
    return {
      key,
      label: (date.getMonth() + 1) + "/" + date.getDate(),
      weekday: "周" + "日一二三四五六"[date.getDay()],
      count: counts.get(key) ?? 0,
    };
  });
});
const sevenDayMax = computed(() => Math.max(1, ...sevenDays.value.map((day) => day.count)));
const sevenDayTotal = computed(() => sevenDays.value.reduce((total, day) => total + day.count, 0));
</script>

<template>
  <div class="admin-dashboard">
    <div v-if="!initialLoading && failedSources.length" class="admin-dashboard-data-warning" role="status">
      <strong>部分数据未更新</strong>
      <span v-for="[key, message] in failedSources" :key="key">{{ message }}</span>
    </div>

    <section class="admin-dashboard-overview" aria-labelledby="admin-dashboard-overview-title">
      <div class="admin-dashboard-section-heading">
        <div><span class="admin-dashboard-kicker">OVERVIEW / 01</span><h2 id="admin-dashboard-overview-title">运营概览</h2></div>
        <button class="admin-dashboard-refresh" type="button" :disabled="dashboardStatus.loading.value" @click="emit('refresh')">
          <span class="admin-dashboard-refresh-icon" aria-hidden="true">↻</span>
          {{ dashboardStatus.loading.value ? "正在刷新" : "刷新数据" }}
        </button>
      </div>
      <div class="admin-dashboard-metrics">
        <RouterLink v-for="metric in metrics" :key="metric.key" class="admin-dashboard-metric" :to="metric.to">
          <div class="admin-dashboard-metric-top"><span>{{ metric.label }}</span><span>{{ metric.marker }}</span></div>
          <strong v-if="initialLoading" class="admin-dashboard-skeleton" aria-label="加载中"></strong>
          <strong v-else-if="refreshErrors[metric.key]" class="admin-dashboard-metric-unavailable">—</strong>
          <strong v-else>{{ metric.value }}</strong>
          <div class="admin-dashboard-metric-foot">
            <span>{{ refreshErrors[metric.key] ? "更新失败" : initialLoading ? "加载中" : "查看详情" }}</span>
            <span aria-hidden="true">↗</span>
          </div>
        </RouterLink>
      </div>
    </section>

    <div class="admin-dashboard-content">
      <div class="admin-dashboard-insights">
        <section class="admin-dashboard-triage" aria-labelledby="admin-dashboard-triage-title">
          <div class="admin-dashboard-section-heading">
            <div><span class="admin-dashboard-kicker">TRIAGE / 02</span><h2 id="admin-dashboard-triage-title">分诊流转</h2></div>
            <span v-if="!initialLoading && !refreshErrors.triageDesk" class="admin-dashboard-data-total">共 {{ triageTotal }} 条</span>
          </div>
          <div v-if="initialLoading" class="admin-dashboard-chart-state">正在读取分诊记录…</div>
          <div v-else-if="refreshErrors.triageDesk" class="admin-dashboard-chart-state error">分诊数据暂不可用。</div>
          <div v-else-if="!triageTotal" class="admin-dashboard-chart-state">暂无分诊记录。</div>
          <ul v-else class="admin-dashboard-triage-chart">
            <li v-for="bucket in triageBuckets" :key="bucket.label" :class="'tone-' + bucket.tone">
              <div class="admin-dashboard-triage-label"><span>{{ bucket.label }}</span><strong>{{ bucket.count }}</strong></div>
              <div class="admin-dashboard-triage-track" aria-hidden="true">
                <span :style="{ width: (bucket.count / triageTotal * 100) + '%' }"></span>
              </div>
            </li>
          </ul>
        </section>

        <section class="admin-dashboard-coverage" aria-labelledby="admin-dashboard-coverage-title">
          <div class="admin-dashboard-section-heading">
            <div><span class="admin-dashboard-kicker">COVERAGE / 03</span><h2 id="admin-dashboard-coverage-title">七日号源</h2></div>
            <span v-if="!initialLoading && !refreshErrors.schedules" class="admin-dashboard-data-total">共 {{ sevenDayTotal }} 班次</span>
          </div>
          <div v-if="initialLoading" class="admin-dashboard-chart-state">正在读取排班…</div>
          <div v-else-if="refreshErrors.schedules" class="admin-dashboard-chart-state error">排班数据暂不可用。</div>
          <div v-else class="admin-dashboard-coverage-chart">
            <div v-for="day in sevenDays" :key="day.key" class="admin-dashboard-coverage-day" :aria-label="day.key + '，已发布 ' + day.count + ' 个班次'">
              <strong>{{ day.count }}</strong>
              <div class="admin-dashboard-coverage-bar" aria-hidden="true"><span :style="{ height: day.count ? (day.count / sevenDayMax * 100) + '%' : '0%' }"></span></div>
              <span class="admin-dashboard-coverage-date">{{ day.label }}</span>
              <small>{{ day.weekday }}</small>
            </div>
            <p v-if="!sevenDayTotal" class="admin-dashboard-coverage-empty">未来七天暂无已发布号源。</p>
          </div>
        </section>
      </div>

      <section class="admin-dashboard-schedules" aria-labelledby="admin-dashboard-schedules-title">
        <div class="admin-dashboard-section-heading">
          <div><span class="admin-dashboard-kicker">SCHEDULE / 04</span><h2 id="admin-dashboard-schedules-title">最近号源</h2></div>
          <RouterLink class="admin-dashboard-section-link" to="/schedule">查看排班 <span aria-hidden="true">↗</span></RouterLink>
        </div>
        <div v-if="initialLoading" class="admin-dashboard-schedule-state">正在读取号源…</div>
        <div v-else-if="refreshErrors.schedules" class="admin-dashboard-schedule-state error">
          号源数据暂不可用。请刷新后重试。
        </div>
        <div v-else-if="!schedules.length" class="admin-dashboard-schedule-state">
          暂无已发布号源。
        </div>
        <div v-else class="admin-dashboard-schedule-list">
          <article v-for="item in schedules.slice(0, 5)" :key="String(item.id)" class="admin-dashboard-schedule">
            <div class="admin-dashboard-schedule-date"><strong>{{ item.workDate || "日期未提供" }}</strong><span>{{ item.timeRange || "时段未提供" }}</span></div>
            <div class="admin-dashboard-schedule-doctor"><strong>{{ item.doctorName || "医生未提供" }}</strong><span>容量 {{ item.capacity ?? "未提供" }}</span></div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>
