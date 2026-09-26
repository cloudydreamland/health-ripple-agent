<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, formatApiError, statusClass, useAuthStore, usePatientWorkflowStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState, StatusTag } from "@smart-cloud-brain/shared-ui";
import PrescriptionDetailModal from "../components/PrescriptionDetailModal.vue";
import PatientIcon from "../components/PatientIcon.vue";
import { formatPatientDate, patientStatusText } from "../format";

const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { prescriptions } = storeToRefs(workflow);
const loading = ref(false);
const error = ref("");
const selected = ref<DataRow | null>(null);
const latestPrescription = computed(() => [...prescriptions.value].sort((a, b) => Number(b.prescriptionId ?? 0) - Number(a.prescriptionId ?? 0))[0] ?? null);
function prescriptionStatus(value: unknown) {
  return patientStatusText(value);
}

async function refresh() {
  loading.value = true;
  error.value = "";
  try {
    await workflow.refreshAuthenticated(auth.token());
  } catch (err) {
    error.value = formatApiError(err, "处方列表加载失败");
  } finally {
    loading.value = false;
  }
}

refresh();
</script>

<template>
  <section class="panel patient-service-page patient-prescriptions-page">
    <header class="panel-header patient-rich-header">
      <div class="panel-title"><span class="patient-header-kicker">诊后服务 / 处方</span><h2>处方记录</h2><p v-if="latestPrescription">最近处方 #{{ fieldText(latestPrescription, "prescriptionId") }} · {{ prescriptionStatus(latestPrescription.status) }}</p></div>
      <div class="patient-header-aside"><span class="patient-header-count"><PatientIcon name="prescriptions" /><strong>{{ prescriptions.length }}</strong><small>份处方</small></span><button type="button" :disabled="loading" @click="refresh">刷新</button></div>
    </header>
    <div class="panel-body stack">
      <ErrorState v-if="error" :message="error" />
      <LoadingState v-if="loading" />
      <div v-else-if="prescriptions.length" class="list">
        <article v-for="item in prescriptions" :key="String(item.prescriptionId)" class="list-row" :data-risk="String(item.riskLevel ?? '').toUpperCase()">
          <div class="row-main"><strong>处方 #{{ fieldText(item, "prescriptionId") }}</strong><p>{{ formatPatientDate(item.createdAt) }} · {{ prescriptionStatus(item.status) }}</p></div>
          <div class="toolbar">
            <StatusTag :status="patientStatusText(item.riskLevel, '未审核')" :tone="statusClass(item.riskLevel)" />
            <button type="button" @click="selected = item">详情</button>
          </div>
        </article>
      </div>
      <EmptyState v-else title="暂无处方" message="医生确认处方后会显示在这里。" />
    </div>
    <PrescriptionDetailModal :open="Boolean(selected)" :prescription="selected" @close="selected = null" />
  </section>
</template>
