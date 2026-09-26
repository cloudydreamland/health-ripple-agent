<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, statusClass, useAuthStore, usePatientWorkflowStore } from "@smart-cloud-brain/shared-api";
import { EmptyState, LoadingState, StatusTag } from "@smart-cloud-brain/shared-ui";
import PatientHero from "../components/PatientHero.vue";
import PatientIcon from "../components/PatientIcon.vue";
import { patientStatusText } from "../format";

defineProps<{ bootLoading?: boolean }>();
defineEmits<{ refresh: [] }>();

const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { patient, triageHistory, registrations, records, prescriptions, slots } = storeToRefs(workflow);
const latestTriage = computed(() => triageHistory.value[0] ?? workflow.triage ?? null);
const recentRegistrations = computed(() => [...registrations.value]
  .sort((a, b) => Number(b.registrationId ?? 0) - Number(a.registrationId ?? 0))
  .slice(0, 4));
const upcomingRegistration = computed(() => registrations.value.find((item) => {
  const status = String(item.status ?? "").toUpperCase();
  const time = Date.parse(String(item.appointmentTime ?? ""));
  return !["CANCELLED", "COMPLETED", "FINISHED"].includes(status) && Number.isFinite(time) && time >= Date.now();
}) ?? null);
function appointmentTime(value: unknown) {
  const time = Date.parse(String(value ?? ""));
  return Number.isFinite(time)
    ? new Intl.DateTimeFormat("zh-CN", { month: "long", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(time)
    : String(value ?? "-");
}
const latestRecord = computed(() => [...records.value].sort((a, b) => Number(b.medicalRecordId ?? 0) - Number(a.medicalRecordId ?? 0))[0] ?? null);
const latestPrescription = computed(() => [...prescriptions.value].sort((a, b) => Number(b.prescriptionId ?? 0) - Number(a.prescriptionId ?? 0))[0] ?? null);
</script>

<template>
  <PatientHero />
  <LoadingState v-if="bootLoading" title="正在同步患者资料" />
  <template v-else>
    <section class="patient-current-task" aria-labelledby="patient-next-title">
      <div>
        <span class="patient-task-label">当前任务 · {{ fieldText(patient, "name", auth.session?.name || "患者") }}</span>
        <h2 id="patient-next-title">{{ upcomingRegistration ? "查看就诊安排" : !latestTriage ? "先描述症状" : "查看可预约号源" }}</h2>
        <p v-if="!upcomingRegistration && latestTriage">最近分诊建议：{{ fieldText(latestTriage, "recommendedDepartment", "待确认") }}。科室建议需由接诊医生再次确认。</p>
        <p v-if="upcomingRegistration">{{ fieldText(upcomingRegistration, "departmentName") }} · {{ fieldText(upcomingRegistration, "doctorName") }} · {{ appointmentTime(upcomingRegistration.appointmentTime) }}</p>
      </div>
      <RouterLink class="button primary patient-task-action" :to="upcomingRegistration ? '/appointments' : !latestTriage ? '/triage' : '/doctors'">
        {{ upcomingRegistration ? "我的挂号" : !latestTriage ? "开始分诊" : "查看号源" }} <span aria-hidden="true">↗</span>
      </RouterLink>
    </section>
    <section class="patient-card-grid" aria-label="就诊概况">
      <RouterLink class="patient-summary-card" to="/doctors"><div class="patient-summary-head"><span>可约号源</span><PatientIcon name="slots" /></div><strong>{{ slots.length }}</strong><small>选择医生 <span aria-hidden="true">↗</span></small></RouterLink>
      <RouterLink class="patient-summary-card" to="/appointments"><div class="patient-summary-head"><span>挂号记录</span><PatientIcon name="appointments" /></div><strong>{{ registrations.length }}</strong><small>查看安排 <span aria-hidden="true">↗</span></small></RouterLink>
      <RouterLink class="patient-summary-card" to="/records"><div class="patient-summary-head"><span>病历</span><PatientIcon name="records" /></div><strong>{{ records.length }}</strong><small>查看病历 <span aria-hidden="true">↗</span></small></RouterLink>
      <RouterLink class="patient-summary-card" to="/records?focus=prescriptions"><div class="patient-summary-head"><span>处方</span><PatientIcon name="prescriptions" /></div><strong>{{ prescriptions.length }}</strong><small>查看处方 <span aria-hidden="true">↗</span></small></RouterLink>
    </section>
    <section class="patient-dashboard-grid">
      <section class="panel patient-home-triage">
        <header class="panel-header"><div class="panel-title"><h2>最近分诊建议</h2></div><RouterLink to="/triage">查看全部 ↗</RouterLink></header>
        <div class="panel-body">
          <div v-if="latestTriage" class="patient-triage-summary">
            <div class="patient-triage-summary-top"><span class="patient-eyebrow">推荐就诊科室</span><StatusTag :status="patientStatusText(latestTriage.status)" :tone="statusClass(latestTriage.status)" /></div>
            <h3>{{ fieldText(latestTriage, "recommendedDepartment", "待确认") }}</h3>
            <dl><div><dt>本次主诉</dt><dd>{{ fieldText(latestTriage, "chiefComplaint", "暂无主诉") }}</dd></div><div><dt>建议依据</dt><dd>{{ fieldText(latestTriage, "reason", "暂无说明") }}</dd></div></dl>
            <p class="patient-triage-caution">科室建议用于就诊引导，最终诊断由接诊医生作出。</p>
          </div>
          <EmptyState v-else title="暂无分诊记录" message="请先提交症状信息，系统会给出科室建议。" />
        </div>
      </section>
      <section class="panel patient-home-actions">
        <header class="panel-header"><div class="panel-title"><h2>快捷操作</h2></div></header>
        <div class="panel-body patient-home-actions-body">
          <RouterLink class="patient-quick-link patient-quick-primary" :to="latestTriage ? '/doctors' : '/triage'"><PatientIcon :name="latestTriage ? 'slots' : 'triage'" /><span>{{ latestTriage ? "查看号源" : "提交分诊" }}</span><b aria-hidden="true">↗</b></RouterLink>
          <button class="patient-quick-link" type="button" @click="$emit('refresh')"><PatientIcon name="refresh" /><span>刷新资料</span><b aria-hidden="true">↗</b></button>
        </div>
      </section>
      <section class="panel patient-home-registration">
        <header class="panel-header"><div class="panel-title"><h2>最近挂号</h2></div><RouterLink to="/appointments">查看全部 ↗</RouterLink></header>
        <div class="panel-body">
          <ol v-if="recentRegistrations.length" class="patient-recent-list"><li v-for="item in recentRegistrations" :key="String(item.registrationId)" class="patient-recent-registration"><span class="patient-recent-date">{{ appointmentTime(item.appointmentTime) }}</span><span class="patient-recent-person"><strong>{{ fieldText(item, "departmentName") }}</strong><small>{{ fieldText(item, "doctorName") }} · #{{ fieldText(item, "registrationId") }}</small></span><StatusTag :status="patientStatusText(item.status)" :tone="statusClass(item.status)" /></li></ol>
          <EmptyState v-else title="暂无挂号" message="选择号源后，预约会显示在这里。" />
        </div>
      </section>
      <section class="panel patient-home-aftercare">
        <header class="panel-header"><div class="panel-title"><h2>最近病历与处方</h2></div><RouterLink to="/records">查看档案 ↗</RouterLink></header>
        <div class="panel-body patient-aftercare-list">
          <RouterLink v-if="latestRecord" :to="`/records?record=${latestRecord.medicalRecordId}`" class="patient-aftercare-item"><span class="patient-aftercare-icon"><PatientIcon name="records" /></span><span><small>医生保存的病历 · #{{ fieldText(latestRecord, "medicalRecordId") }}</small><strong>{{ fieldText(latestRecord, "diagnosis", "未填写诊断") }}</strong><em>{{ fieldText(latestRecord, "chiefComplaint", "暂无主诉") }}</em></span><b aria-hidden="true">↗</b></RouterLink>
          <RouterLink v-if="latestPrescription" :to="`/records?focus=prescriptions${latestPrescription.medicalRecordId ? `&record=${latestPrescription.medicalRecordId}` : ''}`" class="patient-aftercare-item"><span class="patient-aftercare-icon"><PatientIcon name="prescriptions" /></span><span><small>医生保存的处方 · #{{ fieldText(latestPrescription, "prescriptionId") }}</small><strong>风险等级：{{ patientStatusText(latestPrescription.riskLevel, "未审核") }}</strong><em>查看药品与用药信息</em></span><b aria-hidden="true">↗</b></RouterLink>
          <EmptyState v-if="!latestRecord && !latestPrescription" title="暂无诊后记录" />
        </div>
      </section>
    </section>
  </template>
</template>
