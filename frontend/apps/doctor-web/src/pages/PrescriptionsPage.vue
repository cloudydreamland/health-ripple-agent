<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, formatApiError, useAuthStore, useDoctorWorkflowStore } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState } from "@smart-cloud-brain/shared-ui";
import DoctorStatusTag from "../components/DoctorStatusTag.vue";
import DoctorPageHeader from "../components/DoctorPageHeader.vue";
import DoctorPager from "../components/DoctorPager.vue";
import DoctorSelect from "../components/DoctorSelect.vue";

const auth = useAuthStore();
const workflow = useDoctorWorkflowStore();
const { prescriptions } = storeToRefs(workflow);
const loading = ref(false);
const error = ref("");
const loaded = ref(false);
const risk = ref("");
const keyword = ref("");
const sort = ref("recent");
const sortOptions = [{ value: "recent", label: "最近创建" }, { value: "oldest", label: "较早创建" }];
const page = ref(1);
const selectedId = ref("");
const riskFilters = [{ label: "全部", value: "" }, { label: "高风险", value: "HIGH" }, { label: "中风险", value: "MEDIUM" }, { label: "低风险", value: "LOW" }];
const highCount = computed(() => prescriptions.value.filter((item) => fieldText(item, "riskLevel").toUpperCase() === "HIGH").length);
const patientLabel = (item: Record<string, unknown>) => fieldText(item, "patientName", "") || `患者 #${fieldText(item, "patientId", "—")}`;
const displayTime = (value: unknown) => String(value ?? "").replace("T", " ").slice(0, 16) || "—";
const rows = computed(() => prescriptions.value.filter((item) => (!risk.value || fieldText(item, "riskLevel").toUpperCase() === risk.value) && [fieldText(item, "patientName"), fieldText(item, "patientId"), fieldText(item, "prescriptionId")].join(" ").toLowerCase().includes(keyword.value.toLowerCase())).sort((a, b) => sort.value === "oldest" ? fieldText(a, "createdAt").localeCompare(fieldText(b, "createdAt")) : fieldText(b, "createdAt").localeCompare(fieldText(a, "createdAt"))));
const visibleRows = computed(() => rows.value.slice((page.value - 1) * 20, page.value * 20));
const selected = computed(() => visibleRows.value.find((item) => String(item.prescriptionId) === selectedId.value) ?? visibleRows.value[0]);
watch([risk, keyword, sort], () => { page.value = 1; selectedId.value = ""; });
watch(page, () => { selectedId.value = ""; });
async function refresh() {
  loading.value = true; error.value = "";
  try { await workflow.refresh(auth.token()); loaded.value = true; }
  catch (err) { error.value = formatApiError(err, "处方列表加载失败"); }
  finally { loading.value = false; }
}
refresh();
</script>

<template>
  <section class="doctor-page prescriptions-page">
    <DoctorPageHeader eyebrow="PRESCRIPTION SAFETY" title="处方审核台" index="04" :description="loaded ? `处方 ${prescriptions.length} 份 · 高风险 ${highCount} 份` : '正在获取处方'">
      <template #actions><button type="button" :disabled="loading" @click="refresh">{{ loading ? '同步中…' : '↻ 刷新处方' }}</button></template>
    </DoctorPageHeader>
    <div class="risk-filter-bar">
      <div class="risk-tabs" role="group" aria-label="筛选处方风险"><button v-for="item in riskFilters" :key="item.label" type="button" :class="{ selected: risk === item.value }" :aria-pressed="risk === item.value" @click="risk = item.value">{{ item.label }}<span v-if="item.value === 'HIGH' && loaded">{{ highCount }}</span></button></div>
      <label class="search-field"><span aria-hidden="true">⌕</span><input v-model.trim="keyword" aria-label="搜索处方患者" placeholder="搜索患者或处方号" /></label>
      <div class="sort-field"><span>排序</span><DoctorSelect v-model="sort" :options="sortOptions" control-label="排序处方" /></div>
    </div>
    <div v-if="error && loaded" class="clinical-alert danger" role="alert">数据未更新：{{ error }}</div>
    <LoadingState v-if="loading && !loaded" title="正在同步处方" />
    <div v-else-if="error && !loaded" class="queue-error"><ErrorState :message="error" /><button type="button" @click="refresh">重试</button></div>
    <div v-else-if="rows.length" class="prescription-board">
      <div class="prescription-index">
        <button v-for="item in visibleRows" :key="String(item.prescriptionId)" type="button" class="prescription-row" :class="{ selected: selected?.prescriptionId === item.prescriptionId }" :aria-pressed="selected?.prescriptionId === item.prescriptionId" @click="selectedId = String(item.prescriptionId)">
          <span class="prescription-row-rail" :class="fieldText(item, 'riskLevel', 'UNREVIEWED').toLowerCase()" />
          <span class="prescription-row-main"><strong>{{ patientLabel(item) }}</strong><small>处方 #{{ fieldText(item, "prescriptionId", "-") }} · {{ displayTime(item.createdAt) }}</small></span>
          <DoctorStatusTag :status="item.riskLevel || 'UNREVIEWED'" /><span class="record-index-arrow" aria-hidden="true">↗</span>
        </button>
        <DoctorPager v-model:page="page" :total="rows.length" />
      </div>
      <article v-if="selected" class="prescription-inspector">
        <div class="inspector-label">处方档案 <span>RX / {{ fieldText(selected, "prescriptionId", "-") }}</span></div>
        <div class="prescription-inspector-head"><h2>{{ patientLabel(selected) }}</h2><DoctorStatusTag :status="selected.riskLevel || 'UNREVIEWED'" /></div>
        <div class="inspector-meta"><span>患者 ID <b>{{ fieldText(selected, "patientId", "-") }}</b></span><span>病历号 <b>#{{ fieldText(selected, "medicalRecordId", "-") }}</b></span></div>
        <div class="prescription-facts"><div><span>处方状态</span><DoctorStatusTag :status="selected.status" /></div><div><span>风险等级</span><DoctorStatusTag :status="selected.riskLevel || 'UNREVIEWED'" /></div><div><span>创建时间</span><strong>{{ displayTime(selected.createdAt) }}</strong></div></div>
      </article>
    </div>
    <EmptyState v-else title="暂无匹配处方" message="可以调整筛选条件；处方创建后会显示在这里。" />
  </section>
</template>
