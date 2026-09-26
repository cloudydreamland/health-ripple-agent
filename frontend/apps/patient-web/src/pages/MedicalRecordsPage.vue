<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, useAuthStore, usePatientWorkflowStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState } from "@smart-cloud-brain/shared-ui";
import MedicalRecordDetailModal from "../components/MedicalRecordDetailModal.vue";
import PatientIcon from "../components/PatientIcon.vue";

const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { records } = storeToRefs(workflow);
const loading = ref(false);
const detailLoading = ref(false);
const error = ref("");
const selected = ref<DataRow | null>(null);
const latestRecord = computed(() => [...records.value].sort((a, b) => Number(b.medicalRecordId ?? 0) - Number(a.medicalRecordId ?? 0))[0] ?? null);

async function refresh() {
  loading.value = true;
  error.value = "";
  try {
    await workflow.refreshAuthenticated(auth.token());
  } catch (err) {
    error.value = formatApiError(err, "病历列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function open(item: DataRow) {
  detailLoading.value = true;
  error.value = "";
  try {
    selected.value = await api.medicalRecordDetail(auth.token(), Number(item.medicalRecordId));
  } catch (err) {
    error.value = formatApiError(err, "病历详情加载失败");
  } finally {
    detailLoading.value = false;
  }
}

refresh();
</script>

<template>
  <section class="panel patient-service-page patient-records-page">
    <header class="panel-header patient-rich-header">
      <div class="panel-title"><span class="patient-header-kicker">诊后服务 / 病历</span><h2>病历记录</h2><p v-if="latestRecord">最近主诉：{{ fieldText(latestRecord, "chiefComplaint", "暂无主诉") }}</p></div>
      <div class="patient-header-aside"><span class="patient-header-count"><PatientIcon name="records" /><strong>{{ records.length }}</strong><small>份病历</small></span><button type="button" :disabled="loading" @click="refresh">刷新</button></div>
    </header>
    <div class="panel-body stack">
      <ErrorState v-if="error" :message="error" />
      <LoadingState v-if="loading || detailLoading" />
      <div v-else-if="records.length" class="patient-records-content">
        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>病历号</th><th>主诉</th><th>诊断</th><th>方式</th><th class="actions-cell">操作</th></tr></thead>
            <tbody>
              <tr v-for="item in records" :key="String(item.medicalRecordId)">
                <td>#{{ fieldText(item, "medicalRecordId") }}</td>
                <td>{{ fieldText(item, "chiefComplaint") }}</td>
                <td>{{ fieldText(item, "diagnosis") }}</td>
                <td>{{ item.aiGenerated ? "医生确认的智能草稿" : "医生录入" }}</td>
                <td><button type="button" @click="open(item)">详情</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="patient-mobile-record-list">
          <article v-for="item in records" :key="String(item.medicalRecordId)" class="patient-mobile-record">
            <div class="patient-mobile-record-head"><strong>病历 #{{ fieldText(item, "medicalRecordId") }}</strong><span>{{ item.aiGenerated ? "医生确认的智能草稿" : "医生录入" }}</span></div>
            <dl><div><dt>主诉</dt><dd>{{ fieldText(item, "chiefComplaint") }}</dd></div><div><dt>诊断</dt><dd>{{ fieldText(item, "diagnosis") }}</dd></div></dl>
            <button type="button" @click="open(item)">查看详情</button>
          </article>
        </div>
      </div>
      <EmptyState v-else title="暂无病历" message="医生保存病历后会显示在这里。" />
    </div>
    <MedicalRecordDetailModal :open="Boolean(selected)" :record="selected" @close="selected = null" />
  </section>
</template>
