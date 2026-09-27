<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, formatApiError, useAuthStore, useDoctorWorkflowStore } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState } from "@smart-cloud-brain/shared-ui";
import DoctorPageHeader from "../components/DoctorPageHeader.vue";
import DoctorPager from "../components/DoctorPager.vue";
import DoctorSelect from "../components/DoctorSelect.vue";

const auth = useAuthStore();
const workflow = useDoctorWorkflowStore();
const { records } = storeToRefs(workflow);
const loading = ref(false);
const error = ref("");
const loaded = ref(false);
const keyword = ref("");
const sort = ref("recent");
const sortOptions = [{ value: "recent", label: "病历号较新" }, { value: "oldest", label: "病历号较早" }];
const page = ref(1);
const selectedId = ref("");
const rows = computed(() => records.value.filter((item) => [fieldText(item, "patientName"), fieldText(item, "patientId"), fieldText(item, "medicalRecordId"), fieldText(item, "diagnosis")].join(" ").toLowerCase().includes(keyword.value.toLowerCase())).sort((a, b) => sort.value === "oldest" ? Number(a.medicalRecordId) - Number(b.medicalRecordId) : Number(b.medicalRecordId) - Number(a.medicalRecordId)));
const visibleRows = computed(() => rows.value.slice((page.value - 1) * 20, page.value * 20));
const selected = computed(() => visibleRows.value.find((item) => String(item.medicalRecordId) === selectedId.value) ?? visibleRows.value[0]);
watch([keyword, sort], () => { page.value = 1; selectedId.value = ""; });
watch(page, () => { selectedId.value = ""; });
async function refresh() {
  loading.value = true; error.value = "";
  try { await workflow.refresh(auth.token()); loaded.value = true; }
  catch (err) { error.value = formatApiError(err, "病历列表加载失败"); }
  finally { loading.value = false; }
}
refresh();
</script>

<template>
  <section class="doctor-page records-page">
    <DoctorPageHeader title="病历档案" :description="loaded ? `已保存 ${records.length} 份病历` : '正在获取病历'">
      <template #actions><button type="button" :disabled="loading" @click="refresh">{{ loading ? '同步中…' : '↻ 刷新病历' }}</button></template>
    </DoctorPageHeader>
    <div class="library-toolbar"><label class="search-field"><span aria-hidden="true">⌕</span><input v-model.trim="keyword" aria-label="搜索患者或诊断" placeholder="搜索患者、病历号或诊断" /></label><div class="sort-field"><span>排序</span><DoctorSelect v-model="sort" :options="sortOptions" control-label="排序病历" /></div></div>
    <div v-if="error && loaded" class="clinical-alert danger" role="alert">数据未更新：{{ error }}</div>
    <LoadingState v-if="loading && !loaded" title="正在同步病历" />
    <div v-else-if="error && !loaded" class="queue-error"><ErrorState :message="error" /><button type="button" @click="refresh">重试</button></div>
    <div v-else-if="rows.length" class="record-library">
      <div class="record-index" role="list" aria-label="病历列表">
        <button v-for="item in visibleRows" :key="String(item.medicalRecordId)" type="button" class="record-index-row" :class="{ selected: selected?.medicalRecordId === item.medicalRecordId }" :aria-pressed="selected?.medicalRecordId === item.medicalRecordId" @click="selectedId = String(item.medicalRecordId)">
          <span class="record-index-number">#{{ fieldText(item, "medicalRecordId", "-") }}</span><strong>{{ fieldText(item, "patientName", fieldText(item, "patientId", "患者")) }}</strong><small>{{ fieldText(item, "diagnosis", "待补充诊断") }}</small><span class="record-index-arrow" aria-hidden="true">↗</span>
        </button>
        <DoctorPager v-model:page="page" :total="rows.length" />
      </div>
      <article v-if="selected" class="record-inspector">
        <div class="inspector-label">病历 #{{ fieldText(selected, "medicalRecordId", "-") }}</div>
        <h2>{{ fieldText(selected, "patientName", fieldText(selected, "patientId", "患者")) }}</h2>
        <div class="inspector-meta"><span>患者 ID <b>{{ fieldText(selected, "patientId", "-") }}</b></span><span>录入方式 <b>{{ selected.aiGenerated ? "智能草稿·医生确认" : "医生录入" }}</b></span></div>
        <div class="record-field"><span>主诉</span><p>{{ fieldText(selected, "chiefComplaint", "暂无记录") }}</p></div>
        <div class="record-field diagnosis"><span>诊断</span><p>{{ fieldText(selected, "diagnosis", "暂无记录") }}</p></div>
        <div v-if="fieldText(selected, 'presentIllness')" class="record-field"><span>现病史</span><p>{{ fieldText(selected, "presentIllness") }}</p></div>
        <div v-if="fieldText(selected, 'treatmentAdvice')" class="record-field"><span>处理建议</span><p>{{ fieldText(selected, "treatmentAdvice") }}</p></div>
      </article>
    </div>
    <EmptyState v-else title="暂无匹配病历" message="可以调整搜索条件；保存病历后会显示在这里。" />
  </section>
</template>
