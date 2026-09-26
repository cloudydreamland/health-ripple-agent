<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, statusClass, useAuthStore, usePatientWorkflowStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState, StatusTag } from "@smart-cloud-brain/shared-ui";
import PatientIcon from "../components/PatientIcon.vue";
import PrescriptionDetailModal from "../components/PrescriptionDetailModal.vue";
import { formatPatientDate, patientStatusText } from "../format";
import { archiveId, groupVisitArchive } from "../visitArchive";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { records, prescriptions } = storeToRefs(workflow);
const loading = ref(false);
const detailLoading = ref(false);
const error = ref("");
const detailError = ref("");
const selectedId = ref<string | null>(null);
const selectedDetail = ref<DataRow | null>(null);
const selectedPrescription = ref<DataRow | null>(null);
let detailRequest = 0;

const archive = computed(() => groupVisitArchive(records.value, prescriptions.value));
const selectedVisit = computed(() => archive.value.visits.find((visit) => visit.id === selectedId.value) ?? null);
const currentRecord = computed(() => selectedDetail.value ?? selectedVisit.value?.record ?? null);

watch([archive, () => route.query.record, () => route.query.focus], () => {
  const visits = archive.value.visits;
  if (!visits.length) { selectedId.value = null; return; }
  const requested = archiveId(route.query.record);
  if (requested && visits.some((visit) => visit.id === requested)) { selectedId.value = requested; return; }
  if (selectedId.value && visits.some((visit) => visit.id === selectedId.value)) return;
  selectedId.value = (route.query.focus === "prescriptions"
    ? visits.find((visit) => visit.prescriptions.length)
    : null)?.id ?? visits[0]?.id ?? null;
}, { immediate: true });

watch(selectedId, async (id) => {
  const request = ++detailRequest;
  selectedDetail.value = null;
  detailError.value = "";
  if (!id) return;
  detailLoading.value = true;
  try {
    const detail = await api.medicalRecordDetail(auth.token(), Number(id));
    if (request === detailRequest) selectedDetail.value = detail;
  } catch (err) {
    if (request === detailRequest) detailError.value = formatApiError(err, "病历详情加载失败");
  } finally {
    if (request === detailRequest) detailLoading.value = false;
  }
}, { immediate: true });

async function selectVisit(id: string) {
  selectedId.value = id;
  await router.replace({ path: "/records", query: { ...route.query, record: id } });
}

async function refresh() {
  loading.value = true;
  error.value = "";
  try {
    await workflow.refreshAuthenticated(auth.token());
  } catch (err) {
    error.value = formatApiError(err, "就诊档案加载失败");
  } finally {
    loading.value = false;
  }
}

refresh();
</script>

<template>
  <section class="panel patient-service-page patient-archive-page">
    <header class="panel-header patient-rich-header">
      <div class="panel-title"><span class="patient-header-kicker">诊后服务 / 就诊档案</span><h2>病历与处方</h2><p v-if="records.length">按每次就诊查看医生保存的病历和相关处方</p></div>
      <div class="patient-header-aside"><span class="patient-header-count"><PatientIcon name="records" /><strong>{{ records.length }}</strong><small>份病历</small></span><button type="button" :disabled="loading" @click="refresh">刷新</button></div>
    </header>
    <div class="panel-body patient-archive-body">
      <ErrorState v-if="error" :message="error" />
      <LoadingState v-if="loading" />
      <template v-else>
        <div class="patient-archive-overview" aria-label="档案概况">
          <span><PatientIcon name="records" /> {{ records.length }} 份病历</span>
          <span><PatientIcon name="prescriptions" /> {{ prescriptions.length }} 张处方</span>
          <span v-if="archive.unlinked.length">{{ archive.unlinked.length }} 张处方待关联</span>
        </div>

        <template v-if="archive.visits.length">
          <div class="patient-archive-section-head"><h3>选择一次就诊</h3><span>按最近保存排序</span></div>
          <div class="patient-visit-rail" role="group" aria-label="选择病历">
            <button v-for="visit in archive.visits" :key="visit.id" type="button" class="patient-visit-option"
                    :class="{ selected: selectedId === visit.id }" :aria-pressed="selectedId === visit.id" @click="selectVisit(visit.id)">
              <span>病历 #{{ visit.id }} · {{ formatPatientDate(visit.record.createdAt) }}</span>
              <strong>{{ fieldText(visit.record, "diagnosis", "诊断待补充") }}</strong>
              <small>{{ visit.prescriptions.length ? `${visit.prescriptions.length} 张关联处方` : "暂无处方" }}</small>
            </button>
          </div>

          <div v-if="selectedVisit" class="patient-archive-pair" :class="{ 'focus-prescriptions': route.query.focus === 'prescriptions' }">
            <section class="patient-archive-record" aria-labelledby="archive-record-title">
              <div class="patient-archive-panel-title"><span class="patient-archive-symbol"><PatientIcon name="records" /></span><div><small>就诊记录 / #{{ selectedVisit.id }}</small><h3 id="archive-record-title">病历</h3></div><span class="patient-archive-source">{{ currentRecord?.aiGenerated ? "医生确认的智能草稿" : "医生录入" }}</span></div>
              <ErrorState v-if="detailError" :message="detailError" />
              <LoadingState v-if="detailLoading" title="正在读取病历详情" />
              <template v-else-if="currentRecord">
                <div class="patient-archive-diagnosis"><span>医生记录的诊断</span><strong>{{ fieldText(currentRecord, "diagnosis", "未记录") }}</strong></div>
                <dl class="patient-archive-facts">
                  <div><dt>主诉</dt><dd>{{ fieldText(currentRecord, "chiefComplaint", "未记录") }}</dd></div>
                  <div><dt>现病史</dt><dd>{{ fieldText(currentRecord, "presentIllness", "未记录") }}</dd></div>
                  <div><dt>既往史</dt><dd>{{ fieldText(currentRecord, "pastHistory", "未记录") }}</dd></div>
                  <div><dt>体格检查</dt><dd>{{ fieldText(currentRecord, "physicalExam", "未记录") }}</dd></div>
                  <div><dt>处理建议</dt><dd>{{ fieldText(currentRecord, "treatmentAdvice", "未记录") }}</dd></div>
                </dl>
              </template>
            </section>
            <section class="patient-archive-prescriptions" aria-labelledby="archive-prescription-title">
              <div class="patient-archive-panel-title"><span class="patient-archive-symbol"><PatientIcon name="prescriptions" /></span><div><small>与本次病历关联</small><h3 id="archive-prescription-title">处方 <em>{{ selectedVisit.prescriptions.length }}</em></h3></div></div>
              <p class="patient-archive-medication-note">请按医嘱用药，如有疑问请联系医生。</p>
              <div v-if="selectedVisit.prescriptions.length" class="patient-archive-prescription-list">
                <article v-for="prescription in selectedVisit.prescriptions" :key="String(prescription.prescriptionId)" class="patient-archive-prescription" :data-risk="String(prescription.riskLevel ?? '').toUpperCase()">
                  <div class="patient-archive-prescription-top"><strong>处方 #{{ fieldText(prescription, "prescriptionId") }}</strong><StatusTag :status="patientStatusText(prescription.riskLevel, '未审核')" :tone="statusClass(prescription.riskLevel)" /></div>
                  <p>{{ formatPatientDate(prescription.createdAt) }} · {{ patientStatusText(prescription.status) }}</p>
                  <ul v-if="(prescription.items as DataRow[])?.length"><li v-for="(drug, index) in (prescription.items as DataRow[])" :key="index">{{ fieldText(drug, "drugName", "未注明药品") }}</li></ul>
                  <button type="button" @click="selectedPrescription = prescription">查看用药明细 <span aria-hidden="true">↗</span></button>
                </article>
              </div>
              <EmptyState v-else title="本次就诊暂无处方" message="医生开具并确认处方后，将显示在这里。" />
            </section>
          </div>
        </template>
        <EmptyState v-else title="暂无病历" message="医生保存病历后，会按就诊显示在这里。" />

        <section v-if="archive.unlinked.length" class="patient-archive-unlinked" aria-labelledby="archive-unlinked-title">
          <div class="patient-archive-section-head"><h3 id="archive-unlinked-title">未关联到病历的处方</h3><span>仍可独立查看用药信息</span></div>
          <div class="patient-archive-unlinked-grid"><article v-for="prescription in archive.unlinked" :key="String(prescription.prescriptionId)" class="patient-archive-prescription">
            <div class="patient-archive-prescription-top"><strong>处方 #{{ fieldText(prescription, "prescriptionId") }}</strong><StatusTag :status="patientStatusText(prescription.riskLevel, '未审核')" :tone="statusClass(prescription.riskLevel)" /></div>
            <p>{{ formatPatientDate(prescription.createdAt) }} · {{ patientStatusText(prescription.status) }}</p>
            <button type="button" @click="selectedPrescription = prescription">查看用药明细 <span aria-hidden="true">↗</span></button>
          </article></div>
        </section>
      </template>
    </div>
    <PrescriptionDetailModal :open="Boolean(selectedPrescription)" :prescription="selectedPrescription" @close="selectedPrescription = null" />
  </section>
</template>
