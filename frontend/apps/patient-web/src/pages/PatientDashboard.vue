<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, statusClass, useAuthStore, usePatientWorkflowStore } from "@smart-cloud-brain/shared-api";
import { EmptyState, LoadingState, StatusTag } from "@smart-cloud-brain/shared-ui";
import PatientHero from "../components/PatientHero.vue";
import { patientStatusText } from "../format";

defineProps<{ bootLoading?: boolean }>();
defineEmits<{ refresh: [] }>();

const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { patient, triageHistory, registrations, records, prescriptions, slots } = storeToRefs(workflow);
const latestTriage = computed(() => triageHistory.value[0] ?? workflow.triage ?? null);
const latestRegistration = computed(() => registrations.value[0] ?? null);
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
const latestRecord = computed(() => records.value[0] ?? null);
const latestPrescription = computed(() => prescriptions.value[0] ?? null);
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
      <RouterLink class="patient-summary-card" to="/doctors"><span>可约号源</span><strong>{{ slots.length }}</strong><small>选择医生 <span aria-hidden="true">↗</span></small></RouterLink>
      <RouterLink class="patient-summary-card" to="/appointments"><span>挂号记录</span><strong>{{ registrations.length }}</strong><small>查看安排 <span aria-hidden="true">↗</span></small></RouterLink>
      <RouterLink class="patient-summary-card" to="/records"><span>病历</span><strong>{{ records.length }}</strong><small>查看病历 <span aria-hidden="true">↗</span></small></RouterLink>
      <RouterLink class="patient-summary-card" to="/prescriptions"><span>处方</span><strong>{{ prescriptions.length }}</strong><small>查看处方 <span aria-hidden="true">↗</span></small></RouterLink>
    </section>
    <section class="patient-dashboard-grid">
      <section class="panel patient-home-triage">
        <header class="panel-header"><div class="panel-title"><h2>最近分诊建议</h2></div><RouterLink to="/triage">查看全部 ↗</RouterLink></header>
        <div class="panel-body">
          <div v-if="latestTriage" class="clinical-note">
            <StatusTag :status="patientStatusText(latestTriage.status)" :tone="statusClass(latestTriage.status)" />
            <h3>{{ fieldText(latestTriage, "recommendedDepartment", "待确认") }}</h3>
            <p>{{ fieldText(latestTriage, "reason", "暂无说明") }}</p>
          </div>
          <EmptyState v-else title="暂无分诊记录" message="请先提交症状信息，系统会给出科室建议。" />
        </div>
      </section>
      <section class="panel patient-home-actions">
        <header class="panel-header"><div class="panel-title"><h2>快捷操作</h2></div></header>
        <div class="panel-body patient-home-actions-body">
          <RouterLink class="button primary" :to="latestTriage ? '/doctors' : '/triage'">{{ latestTriage ? "查看号源" : "提交分诊" }} <span aria-hidden="true">↗</span></RouterLink>
          <button type="button" @click="$emit('refresh')">刷新资料</button>
        </div>
      </section>
      <section class="panel patient-home-registration">
        <header class="panel-header"><div class="panel-title"><h2>最近挂号</h2></div><RouterLink to="/appointments">查看全部 ↗</RouterLink></header>
        <div class="panel-body">
          <div v-if="latestRegistration" class="patient-recent-registration">
            <div><strong>{{ fieldText(latestRegistration, "departmentName") }} · {{ fieldText(latestRegistration, "doctorName") }}</strong><StatusTag :status="patientStatusText(latestRegistration.status)" :tone="statusClass(latestRegistration.status)" /></div>
            <span>{{ appointmentTime(latestRegistration.appointmentTime) }}</span>
          </div>
          <EmptyState v-else title="暂无挂号" message="选择号源后，预约会显示在这里。" />
        </div>
      </section>
      <section class="panel patient-home-aftercare">
        <header class="panel-header"><div class="panel-title"><h2>诊后信息</h2></div><RouterLink to="/ripple">健康涟漪 ↗</RouterLink></header>
        <div class="panel-body stack">
          <div v-if="latestRecord" class="clinical-note"><strong>{{ fieldText(latestRecord, "diagnosis") }}</strong><p>{{ fieldText(latestRecord, "chiefComplaint") }}</p></div>
          <div v-if="latestPrescription" class="clinical-note"><strong>处方 #{{ fieldText(latestPrescription, "prescriptionId") }}</strong><p>风险等级：{{ patientStatusText(latestPrescription.riskLevel, "未审核") }}</p></div>
          <EmptyState v-if="!latestRecord && !latestPrescription" title="暂无诊后记录" />
        </div>
      </section>
    </section>
  </template>
</template>
