<script setup lang="ts">
import { computed, inject, reactive, ref, type Ref } from "vue";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, statusClass, statusText, toNumber, useAdminWorkflowStore, useAuthStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { DataTable, FormField, StatusTag } from "@smart-cloud-brain/shared-ui";
import TriageDetailModal from "../components/TriageDetailModal.vue";
import AssignDoctorModal from "../components/AssignDoctorModal.vue";
import CloseTriageConfirmModal from "../components/CloseTriageConfirmModal.vue";

const auth = useAuthStore();
const workflow = useAdminWorkflowStore();
const { triageDesk, doctors, refreshErrors } = storeToRefs(workflow);
const dashboardStatus = inject<{ loading: Ref<boolean>; loaded: Ref<boolean> }>("admin-dashboard-status", { loading: ref(false), loaded: ref(true) });
const filter = reactive({ keyword: "", department: "", status: "" });
const assignForm = reactive({ triageRecordId: 0, doctorId: 0 });
const selected = ref<DataRow | null>(null);
const closeTarget = ref<DataRow | null>(null);
const assignOpen = ref(false);
const busy = ref(false);
const refreshing = ref(false);
const error = ref("");
const notice = ref("");
const sourceError = computed(() => refreshErrors.value.triageDesk || "");
const initialLoading = computed(() => dashboardStatus.loading.value && !dashboardStatus.loaded.value);
const statusOptions = computed(() => [...new Set(triageDesk.value.map((item) => fieldText(item, "status", "")).filter(Boolean))]);
const rows = computed(() => triageDesk.value.filter((item) => {
  const keyword = filter.keyword.trim().toLowerCase();
  const haystack = `${fieldText(item, "chiefComplaint", "")} ${fieldText(item, "reason", "")}`.toLowerCase();
  return (!keyword || haystack.includes(keyword))
    && (!filter.department || fieldText(item, "recommendedDepartment", "").includes(filter.department))
    && (!filter.status || fieldText(item, "status") === filter.status);
}));
const buckets = computed(() => {
  const count = (statuses: string[]) => triageDesk.value.filter((item) => statuses.includes(String(item.status))).length;
  const known = ["MANUAL_REQUIRED", "HIGH", "AI_RECOMMENDED", "ASSIGNED", "COMPLETED", "CLOSED"];
  const values = [
    { label: "待人工处理", count: count(["MANUAL_REQUIRED", "HIGH"]), tone: "attention" },
    { label: "智能建议", count: count(["AI_RECOMMENDED"]), tone: "suggested" },
    { label: "已分配医生", count: count(["ASSIGNED"]), tone: "assigned" },
    { label: "已完成", count: count(["COMPLETED"]), tone: "complete" },
  ];
  const closed = count(["CLOSED"]);
  const other = triageDesk.value.filter((item) => !known.includes(String(item.status))).length;
  if (closed) values.push({ label: "已关闭", count: closed, tone: "closed" });
  if (other) values.push({ label: "其他状态", count: other, tone: "other" });
  return values;
});
function preview(value: unknown) {
  const text = String(value ?? "-");
  return text.length > 56 ? text.slice(0, 53) + "…" : text;
}
async function refresh() {
  refreshing.value = true;
  error.value = "";
  try { await workflow.refresh(auth.token()); }
  catch (err) { error.value = formatApiError(err, "分诊列表加载失败"); }
  finally { refreshing.value = false; }
}
async function detail(item: DataRow) {
  busy.value = true;
  error.value = "";
  try { selected.value = await api.triageDetail(auth.token(), toNumber(item.triageRecordId)); }
  catch (err) { error.value = formatApiError(err, "分诊详情加载失败"); }
  finally { busy.value = false; }
}
function openAssign(item: DataRow) {
  assignForm.triageRecordId = toNumber(item.triageRecordId);
  assignForm.doctorId = toNumber(item.assignedDoctorId, toNumber(doctors.value[0]?.id));
  assignOpen.value = true;
}
async function assign() {
  if (!assignForm.triageRecordId || !assignForm.doctorId) { error.value = "请选择分诊记录和医生。"; return; }
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await api.assignTriage(auth.token(), { ...assignForm });
    assignOpen.value = false;
    notice.value = "分诊已分配给医生。";
    await refresh();
  } catch (err) { error.value = formatApiError(err, "分诊分配失败"); }
  finally { busy.value = false; }
}
async function closeTriage() {
  if (!closeTarget.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await api.closeTriage(auth.token(), toNumber(closeTarget.value.triageRecordId));
    closeTarget.value = null;
    notice.value = "分诊记录已关闭。";
    await refresh();
  } catch (err) { error.value = formatApiError(err, "关闭分诊失败"); }
  finally { busy.value = false; }
}
</script>

<template>
  <section class="admin-page triage-page">
    <header class="admin-page-heading">
      <div><span class="admin-page-kicker">TRIAGE / DESK</span><h1>分诊工作台</h1></div>
      <button type="button" :disabled="refreshing || initialLoading" @click="refresh">{{ refreshing || initialLoading ? "刷新中" : "刷新数据" }}</button>
    </header>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div>
    <div v-if="notice" class="notice success" role="status">{{ notice }}</div>
    <div v-if="sourceError && !initialLoading" class="admin-inline-error" role="status">{{ sourceError }}。当前分诊列表未更新。</div>
    <div v-if="!sourceError && !initialLoading" class="admin-status-strip" aria-label="分诊状态概览">
      <div v-for="bucket in buckets" :key="bucket.label" :class="'tone-' + bucket.tone"><span>{{ bucket.label }}</span><strong>{{ bucket.count }}</strong></div>
    </div>
    <section class="admin-list-panel" aria-label="分诊记录列表">
      <div class="admin-list-toolbar">
        <label class="admin-search-field"><span class="sr-only">搜索主诉或原因</span><input v-model.trim="filter.keyword" type="search" placeholder="搜索主诉或原因" /></label>
        <label><span class="sr-only">筛选推荐科室</span><input v-model.trim="filter.department" placeholder="推荐科室" /></label>
        <select v-model="filter.status" aria-label="筛选分诊状态"><option value="">全部状态</option><option v-for="status in statusOptions" :key="status" :value="status">{{ statusText(status) }}</option></select>
        <span class="admin-list-count">{{ sourceError ? "数据未更新" : `显示 ${rows.length} / ${triageDesk.length} 条` }}</span>
      </div>
      <DataTable :rows="rows" :loading="initialLoading || refreshing" :error="sourceError" :empty-title="triageDesk.length ? '没有匹配的分诊记录' : '暂无分诊记录'" :empty-message="triageDesk.length ? '调整搜索或筛选条件后重试。' : '当前没有可处理的分诊记录。'">
        <thead><tr><th>记录</th><th>主诉</th><th>推荐科室</th><th>分配医生</th><th>状态</th><th class="actions-cell">操作</th></tr></thead>
        <tbody>
          <tr v-for="item in rows" :key="String(item.triageRecordId)">
            <td class="admin-cell-code">#{{ fieldText(item, "triageRecordId") }}</td>
            <td class="admin-triage-complaint" :title="fieldText(item, 'chiefComplaint')">{{ preview(item.chiefComplaint) }}</td>
            <td>{{ fieldText(item, "recommendedDepartment") }}</td>
            <td>{{ fieldText(item, "assignedDoctorName", "未分配") }}</td>
            <td><StatusTag :status="statusText(item.status)" :tone="statusClass(item.status)" /></td>
            <td class="admin-row-actions"><div class="admin-row-action-group"><button type="button" :disabled="busy" @click="detail(item)">详情</button><button type="button" :disabled="busy" @click="openAssign(item)">分配</button><button class="danger" type="button" :disabled="busy" @click="closeTarget = item">关闭</button></div></td>
          </tr>
        </tbody>
      </DataTable>
    </section>
    <TriageDetailModal :open="Boolean(selected)" :triage="selected" @close="selected = null" />
    <AssignDoctorModal :open="assignOpen" :busy="busy" @close="assignOpen = false" @confirm="assign">
      <div class="stack">
        <FormField label="分诊记录 ID"><input v-model.number="assignForm.triageRecordId" type="number" /></FormField>
        <FormField label="医生"><select v-model.number="assignForm.doctorId"><option :value="0">请选择</option><option v-for="doctor in doctors" :key="String(doctor.id)" :value="toNumber(doctor.id)">{{ doctor.name }} / {{ doctor.departmentName }}</option></select></FormField>
      </div>
    </AssignDoctorModal>
    <CloseTriageConfirmModal :open="Boolean(closeTarget)" :busy="busy" @close="closeTarget = null" @confirm="closeTriage" />
  </section>
</template>
