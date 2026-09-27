<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import {
  api,
  fieldText,
  formatApiError,
  medicalRecordStreamUrl,
  toNumber,
  useAuthStore,
  useDoctorWorkflowStore,
  type DataRow,
  type DrugItem,
} from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState } from "@smart-cloud-brain/shared-ui";
import DoctorStatusTag from "../components/DoctorStatusTag.vue";
import { doctorStatusText } from "../doctorStatus";
import PatientContextDrawer from "../components/PatientContextDrawer.vue";
import AiRecordPreviewModal from "../components/AiRecordPreviewModal.vue";
import SaveRecordConfirmModal from "../components/SaveRecordConfirmModal.vue";
import PrescriptionRiskModal from "../components/PrescriptionRiskModal.vue";
import HighRiskConfirmModal from "../components/HighRiskConfirmModal.vue";
import CompleteRegistrationConfirmModal from "../components/CompleteRegistrationConfirmModal.vue";
import DoctorCombobox from "../components/DoctorCombobox.vue";

const props = defineProps<{ registrationId: string }>();
const emit = defineEmits<{ refresh: [] }>();
const auth = useAuthStore();
const workflow = useDoctorWorkflowStore();
const { registrations, triageRecords, drugs, streamText, streamStatus } = storeToRefs(workflow);
const loading = reactive({ record: false, prescription: false, complete: false });
const error = ref("");
const notice = ref("");
const contextOpen = ref(false);
const previewOpen = ref(false);
const saveConfirmOpen = ref(false);
const riskOpen = ref(false);
const highRiskOpen = ref(false);
const completeOpen = ref(false);
const dialogueText = ref("");
const activeStage = ref<"record" | "prescription">("record");
const patientDetailsOpen = ref(false);
const drugNames = computed(() => drugs.value.map((drug) => String(drug.name ?? "")).filter(Boolean));
const checkResult = ref<DataRow | null>(null);
let recordStream: AbortController | null = null;

const registration = computed(() => registrations.value.find((item) => toNumber(item.registrationId) === toNumber(props.registrationId)) ?? null);
const triage = computed(() => triageRecords.value.find((item) => toNumber(item.triageRecordId) === toNumber(registration.value?.triageRecordId)) ?? null);
const patientName = computed(() => fieldText(registration.value, "patientName", `患者${fieldText(registration.value, "patientId", "-")}`));
const medicalForm = reactive({
  registrationId: toNumber(props.registrationId),
  chiefComplaint: "",
  presentIllness: "",
  pastHistory: "",
  physicalExam: "",
  diagnosis: "",
  treatmentAdvice: "",
  aiGenerated: true,
});
const prescription = reactive({
  medicalRecordId: 0,
  riskLevel: "UNREVIEWED",
  drugs: [{ drugName: "", dosage: "", frequency: "", usageMethod: "口服" }] as DrugItem[],
});
const canSaveRecord = computed(() => medicalForm.registrationId > 0 && medicalForm.chiefComplaint.trim() && medicalForm.diagnosis.trim());
const canCheck = computed(() => prescription.drugs.every((item) => item.drugName.trim() && item.dosage.trim() && item.frequency.trim() && item.usageMethod.trim()));
const canCreate = computed(() => prescription.medicalRecordId > 0 && canCheck.value);
const prescriptionInputs = computed(() => JSON.stringify({
  patientId: toNumber(registration.value?.patientId),
  medicalRecordId: prescription.medicalRecordId,
  diagnosis: medicalForm.diagnosis,
  pastHistory: medicalForm.pastHistory,
  drugs: prescription.drugs,
}));
const checkedInputs = ref("");
const riskChecked = computed(() => Boolean(checkResult.value) && checkedInputs.value === prescriptionInputs.value);

function applyRegistration() {
  medicalForm.registrationId = toNumber(props.registrationId);
  medicalForm.chiefComplaint = fieldText(triage.value, "chiefComplaint", fieldText(registration.value, "patientName", ""));
}

function setError(message: string) {
  error.value = message;
  notice.value = "";
}

function setNotice(message: string) {
  notice.value = message;
  error.value = "";
}

function applyDraft(draft: DataRow) {
  medicalForm.chiefComplaint = fieldText(draft, "chiefComplaint", medicalForm.chiefComplaint);
  medicalForm.presentIllness = fieldText(draft, "presentIllness", medicalForm.presentIllness);
  medicalForm.pastHistory = fieldText(draft, "pastHistory", medicalForm.pastHistory);
  medicalForm.physicalExam = fieldText(draft, "physicalExam", medicalForm.physicalExam);
  medicalForm.diagnosis = fieldText(draft, "diagnosis", medicalForm.diagnosis);
  medicalForm.treatmentAdvice = fieldText(draft, "treatmentAdvice", medicalForm.treatmentAdvice);
}

function parseEventData(raw: string) {
  try { return JSON.parse(raw) as DataRow; } catch { return { text: raw }; }
}

function handleStreamBlock(block: string) {
  const lines = block.split(/\r?\n/);
  const eventName = lines.find((line) => line.startsWith("event:"))?.slice("event:".length).trim();
  const dataText = lines.filter((line) => line.startsWith("data:")).map((line) => line.slice("data:".length).trim()).join("\n");
  if (!dataText) return;
  if (eventName === "delta") {
    const data = parseEventData(dataText);
    streamText.value += `${fieldText(data, "text", dataText)}\n`;
  } else if (eventName === "structured") {
    applyDraft(parseEventData(dataText));
    streamStatus.value = "DRAFT_READY";
  } else if (eventName === "error") {
    throw new Error(fieldText(parseEventData(dataText), "message", "智能病历生成失败"));
  }
}

async function generateRecord() {
  if (!dialogueText.value.trim()) return setError("请先填写问诊摘要。");
  loading.record = true;
  error.value = "";
  streamText.value = "";
  streamStatus.value = "GENERATING";
  try {
    recordStream?.abort();
    recordStream = new AbortController();
    const response = await fetch(medicalRecordStreamUrl(medicalForm.registrationId, dialogueText.value, fieldText(triage.value, "departmentCode", "")), {
      headers: { Authorization: `Bearer ${auth.token()}` },
      signal: recordStream.signal,
    });
    if (!response.ok || !response.body) throw new Error("病历流式生成服务不可用");
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split("\n\n");
      buffer = blocks.pop() ?? "";
      blocks.forEach(handleStreamBlock);
    }
    if (buffer.trim()) handleStreamBlock(buffer);
    streamStatus.value = "DRAFT_READY";
    previewOpen.value = true;
    setNotice("病历草稿已生成，请确认后保存。");
  } catch (err) {
    streamStatus.value = "FAILED";
    setError(formatApiError(err, "病历生成失败"));
  } finally {
    loading.record = false;
    recordStream = null;
  }
}

async function saveRecord() {
  if (!canSaveRecord.value) return setError("主诉和诊断为必填项。");
  loading.record = true;
  try {
    const saved = await api.saveMedicalRecord(auth.token(), { ...medicalForm });
    prescription.medicalRecordId = toNumber(saved.medicalRecordId);
    emit("refresh");
    saveConfirmOpen.value = false;
    setNotice("病历已保存，可以继续处方录入和审核。");
  } catch (err) {
    setError(formatApiError(err, "病历保存失败"));
  } finally {
    loading.record = false;
  }
}

function addDrug() {
  prescription.drugs.push({ drugName: "", dosage: "", frequency: "", usageMethod: "口服" });
}

function removeDrug(index: number) {
  if (prescription.drugs.length === 1) {
    prescription.drugs[0] = { drugName: "", dosage: "", frequency: "", usageMethod: "口服" };
    return;
  }
  prescription.drugs.splice(index, 1);
}

async function checkPrescription() {
  if (!canCheck.value) return setError("请完整填写药品、剂量、频次和用法。");
  loading.prescription = true;
  checkResult.value = null;
  checkedInputs.value = "";
  try {
    const inputsAtCheck = prescriptionInputs.value;
    checkResult.value = await api.checkPrescription(auth.token(), {
      patientId: toNumber(registration.value?.patientId),
      doctorId: auth.session?.userId,
      medicalRecordId: prescription.medicalRecordId || undefined,
      diagnosis: medicalForm.diagnosis,
      pastHistory: medicalForm.pastHistory,
      drugs: prescription.drugs,
    });
    prescription.riskLevel = fieldText(checkResult.value, "riskLevel", "UNREVIEWED");
    checkedInputs.value = inputsAtCheck;
    riskOpen.value = true;
  } catch (err) {
    setError(formatApiError(err, "处方审核失败"));
  } finally {
    loading.prescription = false;
  }
}

async function createPrescription() {
  if (!canCreate.value) return setError("请先保存病历并完成处方药品录入。");
  if (!riskChecked.value) return setError("处方内容已变更或尚未审核，请重新进行风险审核。");
  if (String(prescription.riskLevel).toUpperCase() === "HIGH" && !highRiskOpen.value) {
    riskOpen.value = false;
    highRiskOpen.value = true;
    return;
  }
  loading.prescription = true;
  try {
    await api.createPrescription(auth.token(), {
      patientId: toNumber(registration.value?.patientId),
      medicalRecordId: prescription.medicalRecordId,
      registrationId: toNumber(registration.value?.registrationId),
      riskLevel: prescription.riskLevel,
      drugs: prescription.drugs,
    });
    riskOpen.value = false;
    highRiskOpen.value = false;
    checkResult.value = null;
    checkedInputs.value = "";
    prescription.riskLevel = "UNREVIEWED";
    emit("refresh");
    setNotice("处方已创建。");
  } catch (err) {
    setError(formatApiError(err, "处方创建失败"));
  } finally {
    loading.prescription = false;
  }
}

async function completeRegistration() {
  loading.complete = true;
  try {
    await api.completeRegistration(auth.token(), medicalForm.registrationId);
    completeOpen.value = false;
    emit("refresh");
    setNotice("接诊已完成。");
  } catch (err) {
    setError(formatApiError(err, "完成接诊失败"));
  } finally {
    loading.complete = false;
  }
}

watch(() => props.registrationId, applyRegistration, { immediate: true });
</script>

<template>
  <section class="clinical-page consultation-workbench">
    <header class="encounter-heading"><div><span>接诊 · 挂号 #{{ registrationId }}</span><h1>{{ registration ? patientName : "未选择患者" }}</h1></div><div class="encounter-actions"><DoctorStatusTag v-if="registration" :status="registration.status" /><button type="button" :disabled="loading.complete" @click="completeOpen = true">完成接诊</button></div></header>

    <ErrorState v-if="error" :message="error" />
    <div v-if="notice" class="clinical-alert success">{{ notice }}</div>
    <EmptyState v-if="!registration" title="未找到挂号" message="" />

    <div v-else class="encounter-layout">
      <aside class="clinical-section patient-rail">
        <header class="section-toolbar">
          <h2>患者与分诊</h2>
          <button type="button" class="compact-action patient-full-detail" @click="contextOpen = true">完整信息</button>
        </header>
        <div class="patient-rail-summary"><strong>{{ patientName }}</strong><span>#{{ registrationId }} · {{ fieldText(registration, "departmentName", "-") }}</span><button type="button" class="patient-rail-toggle" :aria-expanded="patientDetailsOpen" @click="patientDetailsOpen = !patientDetailsOpen">{{ patientDetailsOpen ? '收起信息' : '查看分诊信息' }} <span aria-hidden="true">{{ patientDetailsOpen ? '⌃' : '⌄' }}</span></button></div>
        <dl class="clinical-dl" :class="{ 'mobile-expanded': patientDetailsOpen }">
          <div><dt>患者 ID</dt><dd>{{ fieldText(registration, "patientId", "-") }}</dd></div>
          <div class="span"><dt>预约时间</dt><dd>{{ fieldText(registration, "appointmentTime", "-").replace('T', ' ') }}</dd></div>
          <div><dt>分诊状态</dt><dd>{{ doctorStatusText(triage?.status, "—") }}</dd></div>
          <div class="span"><dt>主诉</dt><dd>{{ fieldText(triage, "chiefComplaint", medicalForm.chiefComplaint || "-") }}</dd></div>
          <div class="span"><dt>历史信息</dt><dd>{{ fieldText(triage, "pastHistory", medicalForm.pastHistory || "-") }}</dd></div>
        </dl>
      </aside>

      <div class="encounter-main">
      <nav class="encounter-stage-tabs" aria-label="接诊阶段"><button type="button" :class="{ active: activeStage === 'record' }" :aria-current="activeStage === 'record' ? 'step' : undefined" @click="activeStage = 'record'"><b>01</b><span>病历编辑</span><small>{{ prescription.medicalRecordId ? '已保存' : '待保存' }}</small></button><button type="button" :class="{ active: activeStage === 'prescription' }" :aria-current="activeStage === 'prescription' ? 'step' : undefined" @click="activeStage = 'prescription'"><b>02</b><span>用药审核</span><small>{{ riskChecked ? '已审核' : '待审核' }}</small></button></nav>
      <main v-show="activeStage === 'record'" class="clinical-section record-workspace">
        <header class="section-toolbar">
          <h2>病历编辑</h2>
          <DoctorStatusTag :status="streamStatus" tone="info" />
        </header>

        <div class="record-editor-grid">
          <label class="clinical-field full">
            <span>问诊摘要 · 智能草稿依据</span>
            <textarea v-model.trim="dialogueText" class="consultation-textarea" rows="3" placeholder="记录本次问诊的症状、时长与关键发现" />
          </label>

          <div v-if="loading.record || streamText || streamStatus === 'FAILED'" class="ai-draft-pane full">
            <div class="inline-toolbar">
              <strong>智能草稿</strong>
              <button type="button" class="compact-action" :disabled="!streamText" @click="previewOpen = true">预览</button>
            </div>
            <LoadingState v-if="loading.record" title="正在处理病历" />
            <pre v-else-if="streamText" class="stream-box">{{ streamText }}</pre>
            <span v-else class="muted-line">草稿生成失败，可重新生成。</span>
          </div>

          <div class="record-form-label full">病历必填项 <small>医生确认后保存</small></div>
          <label class="clinical-field">
            <span>主诉 *</span>
            <input v-model.trim="medicalForm.chiefComplaint" />
          </label>
          <label class="clinical-field">
            <span>诊断 *</span>
            <input v-model.trim="medicalForm.diagnosis" />
          </label>
          <div class="record-form-label full">补充记录 <small>按临床需要填写</small></div>
          <label class="clinical-field">
            <span>现病史</span>
            <textarea v-model.trim="medicalForm.presentIllness" />
          </label>
          <label class="clinical-field">
            <span>既往史</span>
            <textarea v-model.trim="medicalForm.pastHistory" />
          </label>
          <label class="clinical-field">
            <span>体格检查</span>
            <textarea v-model.trim="medicalForm.physicalExam" />
          </label>
          <label class="clinical-field">
            <span>处理建议</span>
            <textarea v-model.trim="medicalForm.treatmentAdvice" />
          </label>
        </div>

        <footer class="area-actions">
          <button class="primary" type="button" :disabled="loading.record" @click="generateRecord">{{ loading.record ? '生成中…' : '生成病历' }}</button>
          <button type="button" :disabled="!canSaveRecord || loading.record" @click="saveConfirmOpen = true">保存病历</button>
        </footer>
      </main>

      <section v-show="activeStage === 'prescription'" class="clinical-section prescription-rail">
        <header class="section-toolbar">
          <h2>药品录入与审核</h2>
          <DoctorStatusTag :status="riskChecked ? prescription.riskLevel : 'UNREVIEWED'" />
        </header>

        <div class="medication-list">
          <div v-for="(drug, index) in prescription.drugs" :key="index" class="medication-row">
            <div class="medication-row-head"><strong><span>{{ String(index + 1).padStart(2, '0') }}</span> 药品明细</strong><button class="danger compact-action" type="button" :aria-label="`删除第 ${index + 1} 种药品`" @click="removeDrug(index)">删除</button></div>
            <div class="medication-fields">
              <label class="drug-name">药品<DoctorCombobox v-model="drug.drugName" :options="drugNames" :control-label="`第 ${index + 1} 种药品`" placeholder="搜索或输入药品名称" /></label>
              <label>剂量<input v-model.trim="drug.dosage" placeholder="例如 1 片" /></label>
              <label>频次<input v-model.trim="drug.frequency" placeholder="例如 每日两次" /></label>
              <label>用法<input v-model.trim="drug.usageMethod" /></label>
            </div>
          </div>
        </div>

        <div v-if="checkResult && riskChecked" class="risk-result">
          {{ fieldText(checkResult, "suggestions", "请医生复核用药风险。") }}
        </div>
        <div v-else-if="checkResult" class="risk-result">处方内容已变更，请重新进行风险审核。</div>

        <footer class="area-actions prescription-actions">
          <button type="button" @click="addDrug">新增药品</button>
          <button class="primary" type="button" :disabled="loading.prescription || !canCheck" @click="checkPrescription">风险审核</button>
          <button type="button" :disabled="loading.prescription || !canCreate || !riskChecked" @click="createPrescription">创建处方</button>
        </footer>
      </section>
      </div>
    </div>

    <PatientContextDrawer :open="contextOpen" :registration="registration" :triage="triage" @close="contextOpen = false" />
    <AiRecordPreviewModal :open="previewOpen" :text="streamText" @close="previewOpen = false" />
    <SaveRecordConfirmModal :open="saveConfirmOpen" :busy="loading.record" @close="saveConfirmOpen = false" @confirm="saveRecord" />
    <PrescriptionRiskModal :open="riskOpen" :result="checkResult" @close="riskOpen = false" @confirm="createPrescription" />
    <HighRiskConfirmModal :open="highRiskOpen" :busy="loading.prescription" @close="highRiskOpen = false" @confirm="createPrescription" />
    <CompleteRegistrationConfirmModal :open="completeOpen" :busy="loading.complete" @close="completeOpen = false" @confirm="completeRegistration" />
  </section>
</template>
