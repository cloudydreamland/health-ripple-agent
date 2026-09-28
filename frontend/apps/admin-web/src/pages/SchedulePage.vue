<script setup lang="ts">
import { computed, inject, reactive, ref, type Ref } from "vue";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, statusClass, statusText, toNumber, useAdminWorkflowStore, useAuthStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { DataTable, FormField, StatusTag } from "@smart-cloud-brain/shared-ui";
import ScheduleSuggestionDetailModal from "../components/ScheduleSuggestionDetailModal.vue";
import PublishScheduleConfirmModal from "../components/PublishScheduleConfirmModal.vue";

const auth = useAuthStore();
const workflow = useAdminWorkflowStore();
const { suggestions, schedules, refreshErrors } = storeToRefs(workflow);
const dashboardStatus = inject<{ loading: Ref<boolean>; loaded: Ref<boolean> }>("admin-dashboard-status", { loading: ref(false), loaded: ref(true) });
const form = reactive({ startDate: new Date(Date.now() + 86400000).toISOString().slice(0, 10), days: 3 });
const filter = reactive({ date: "", doctor: "" });
const busy = ref(false);
const refreshing = ref(false);
const error = ref("");
const notice = ref("");
const selected = ref<DataRow | null>(null);
const publishOpen = ref(false);
const initialLoading = computed(() => dashboardStatus.loading.value && !dashboardStatus.loaded.value);
const sourceError = computed(() => refreshErrors.value.schedules || "");
const publishedRows = computed(() => schedules.value.filter((item) =>
  (!filter.date || fieldText(item, "workDate", "") === filter.date)
  && (!filter.doctor || fieldText(item, "doctorName", "").toLowerCase().includes(filter.doctor.trim().toLowerCase())),
));
async function refresh() {
  refreshing.value = true;
  error.value = "";
  try { await workflow.refresh(auth.token()); }
  catch (err) { error.value = formatApiError(err, "排班列表加载失败"); }
  finally { refreshing.value = false; }
}
async function generate() {
  busy.value = true;
  error.value = "";
  notice.value = "";
  suggestions.value = [];
  try {
    suggestions.value = await api.generateSchedule(auth.token(), { ...form });
    notice.value = `已生成 ${suggestions.value.length} 条排班建议。`;
  } catch (err) { error.value = formatApiError(err, "排班建议生成失败"); }
  finally { busy.value = false; }
}
async function publish() {
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const ids = suggestions.value.map((item) => toNumber(item.id)).filter(Boolean);
    schedules.value = await api.publishSchedule(auth.token(), { suggestionIds: ids });
    suggestions.value = [];
    publishOpen.value = false;
    notice.value = "号源已发布。";
    await refresh();
  } catch (err) { error.value = formatApiError(err, "号源发布失败"); }
  finally { busy.value = false; }
}
async function openDetail(item: DataRow) {
  busy.value = true;
  error.value = "";
  try { selected.value = await api.scheduleSuggestionDetail(auth.token(), toNumber(item.id)); }
  catch (err) { error.value = formatApiError(err, "排班建议详情加载失败"); }
  finally { busy.value = false; }
}
</script>

<template>
  <section class="admin-page schedule-page">
    <header class="admin-page-heading">
      <div><span class="admin-page-kicker">SCHEDULE / PUBLISH</span><h1>智能排班与号源发布</h1></div>
      <button type="button" :disabled="refreshing || initialLoading" @click="refresh">{{ refreshing || initialLoading ? "刷新中" : "刷新数据" }}</button>
    </header>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div>
    <div v-if="notice" class="notice success" role="status">{{ notice }}</div>
    <section class="admin-schedule-generator" aria-label="生成排班建议">
      <FormField label="开始日期"><input v-model="form.startDate" type="date" /></FormField>
      <FormField label="生成天数"><input v-model.number="form.days" type="number" min="1" max="14" /></FormField>
      <div class="admin-generator-actions"><button type="button" :disabled="busy" @click="generate">{{ busy ? "处理中…" : "生成建议" }}</button><button class="primary" type="button" :disabled="busy || !suggestions.length" @click="publishOpen = true">发布号源 <span v-if="suggestions.length">{{ suggestions.length }}</span></button></div>
    </section>
    <section class="admin-list-panel" aria-labelledby="admin-suggestions-title">
      <div class="admin-list-heading"><div><span class="admin-page-kicker">DRAFT / 01</span><h2 id="admin-suggestions-title">待发布建议</h2></div><span class="admin-list-count">{{ suggestions.length }} 条</span></div>
      <DataTable :rows="suggestions" :loading="busy && !publishOpen && !selected" empty-title="暂无待发布建议" empty-message="设置日期和天数后生成建议，发布前可逐条查看。">
        <thead><tr><th>日期</th><th>时段</th><th>医生</th><th>科室</th><th>容量</th><th class="actions-cell">操作</th></tr></thead>
        <tbody><tr v-for="item in suggestions" :key="String(item.id)"><td class="admin-cell-code">{{ fieldText(item, "workDate") }}</td><td>{{ fieldText(item, "timeRange") }}</td><td><strong>{{ fieldText(item, "doctorName") }}</strong></td><td>{{ fieldText(item, "departmentName") }}</td><td>{{ fieldText(item, "capacity") }}</td><td class="admin-row-actions"><button type="button" :disabled="busy" @click="openDetail(item)">详情</button></td></tr></tbody>
      </DataTable>
    </section>
    <section class="admin-list-panel" aria-labelledby="admin-published-title">
      <div class="admin-list-heading"><div><span class="admin-page-kicker">PUBLISHED / 02</span><h2 id="admin-published-title">已发布号源</h2></div><span class="admin-list-count">{{ sourceError ? "数据未更新" : `显示 ${publishedRows.length} / ${schedules.length} 班次` }}</span></div>
      <div class="admin-list-toolbar"><label><span class="sr-only">按日期筛选号源</span><input v-model="filter.date" type="date" /></label><label class="admin-search-field"><span class="sr-only">搜索医生</span><input v-model.trim="filter.doctor" type="search" placeholder="搜索医生" /></label></div>
      <div v-if="sourceError && !initialLoading" class="admin-inline-error" role="status">{{ sourceError }}。已发布号源未更新。</div>
      <DataTable :rows="publishedRows" :loading="initialLoading || refreshing" :error="sourceError" :empty-title="schedules.length ? '没有匹配的号源' : '暂无已发布号源'" :empty-message="schedules.length ? '调整日期或医生筛选后重试。' : '当前没有已发布排班。'" scroll-class="admin-published-scroll">
        <thead><tr><th>日期</th><th>时段</th><th>医生</th><th>科室</th><th>容量</th><th>状态</th></tr></thead>
        <tbody><tr v-for="item in publishedRows" :key="String(item.id)"><td class="admin-cell-code">{{ fieldText(item, "workDate") }}</td><td>{{ fieldText(item, "timeRange") }}</td><td><strong>{{ fieldText(item, "doctorName") }}</strong></td><td>{{ fieldText(item, "departmentName") }}</td><td>{{ fieldText(item, "capacity") }}</td><td><StatusTag :status="statusText(item.status, '已发布')" :tone="statusClass(item.status || 'PUBLISHED')" /></td></tr></tbody>
      </DataTable>
    </section>
    <ScheduleSuggestionDetailModal :open="Boolean(selected)" :suggestion="selected" @close="selected = null" />
    <PublishScheduleConfirmModal :open="publishOpen" :busy="busy" @close="publishOpen = false" @confirm="publish" />
  </section>
</template>
