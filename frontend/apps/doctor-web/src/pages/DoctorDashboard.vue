<script setup lang="ts">
import { computed, inject } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, useDoctorWorkflowStore } from "@smart-cloud-brain/shared-api";
import { EmptyState } from "@smart-cloud-brain/shared-ui";
import DoctorStatusTag from "../components/DoctorStatusTag.vue";
import DoctorPageHeader from "../components/DoctorPageHeader.vue";
import { doctorSyncKey } from "../doctorSync";

const workflow = useDoctorWorkflowStore();
const sync = inject(doctorSyncKey);
const hasSynced = computed(() => sync?.hasSynced.value ?? false);
const { registrations, records, prescriptions, notifications } = storeToRefs(workflow);
const unread = computed(() => notifications.value.filter((item) => String(item.readStatus) !== "READ").length);
const completed = computed(() => registrations.value.filter((item) => fieldText(item, "status") === "COMPLETED").length);
const active = computed(() => registrations.value.filter((item) => ["CREATED", "CHECKED_IN", "CONFIRMED"].includes(fieldText(item, "status"))).length);
const cancelled = computed(() => registrations.value.filter((item) => fieldText(item, "status") === "CANCELLED").length);
const other = computed(() => Math.max(0, registrations.value.length - active.value - completed.value - cancelled.value));
const dashboardRegistrations = computed(() => [...registrations.value].sort((a, b) => Number(!["CREATED", "CHECKED_IN", "CONFIRMED"].includes(fieldText(a, "status"))) - Number(!["CREATED", "CHECKED_IN", "CONFIRMED"].includes(fieldText(b, "status")))).slice(0, 8));
const completionRate = computed(() => registrations.value.length ? Math.round((completed.value / registrations.value.length) * 100) : null);
const statusComposition = computed(() => [
  { label: "待处理", value: active.value, className: "pending" },
  { label: "已完成", value: completed.value, className: "complete" },
  { label: "已取消", value: cancelled.value, className: "cancelled" },
  { label: "其他", value: other.value, className: "other" },
]);
</script>

<template>
  <section class="doctor-page dashboard-workbench">
    <DoctorPageHeader eyebrow="OVERVIEW" title="医生工作台" index="01">
      <template #actions><RouterLink to="/queue" class="button primary">进入接诊队列 ↗</RouterLink></template>
    </DoctorPageHeader>
    <div class="dashboard-overview">
      <div class="overview-lead"><span class="overview-index">当前工作量</span><strong>{{ hasSynced ? active : '—' }}</strong><span>位患者待处理</span><small>{{ hasSynced ? `该医生累计挂号 ${registrations.length}` : '等待同步' }}</small></div>
      <div class="overview-metrics"><div><span>完成率</span><strong>{{ hasSynced && completionRate !== null ? `${completionRate}%` : '—' }}</strong><small>已完成 / 该医生累计挂号</small></div><div><span>病历</span><strong>{{ hasSynced ? records.length : '—' }}</strong><small>已保存记录</small></div><div><span>处方</span><strong>{{ hasSynced ? prescriptions.length : '—' }}</strong><small>已创建处方</small></div><div :class="{ 'metric-attention': unread > 0 }"><span>未读通知</span><strong>{{ hasSynced ? unread : '—' }}</strong><small>需要查看</small></div></div>
    </div>

    <div class="dashboard-grid">
      <section class="clinical-section dashboard-queue">
        <header class="section-toolbar">
          <h2>优先接诊</h2>
          <RouterLink class="button compact-action" to="/queue">查看全部 ↗</RouterLink>
        </header>
        <div class="table-scroll">
          <p v-if="!hasSynced" class="dashboard-awaiting">数据尚未同步；可使用顶部“同步”重试。</p>
          <div v-else-if="registrations.length" class="dashboard-patient-list">
            <div v-for="(item, index) in dashboardRegistrations" :key="String(item.registrationId)" class="dashboard-patient-row">
              <div class="dashboard-patient-name"><span class="patient-row-number">{{ String(index + 1).padStart(2, '0') }}</span><span><strong>{{ fieldText(item, "patientName", `患者${fieldText(item, "patientId")}`) }}</strong><small>挂号 #{{ fieldText(item, "registrationId") }} · {{ fieldText(item, "departmentName", "-") }}</small></span></div>
              <time>{{ fieldText(item, "appointmentTime", "-").replace('T', ' ') }}</time>
              <DoctorStatusTag :status="item.status" />
              <RouterLink class="button compact-action" :to="`/consult/${item.registrationId}`">{{ ['COMPLETED', 'CANCELLED'].includes(fieldText(item, 'status')) ? '查看' : '接诊' }} ↗</RouterLink>
            </div>
          </div>
          <EmptyState v-else title="暂无队列" message="" />
        </div>
      </section>

      <aside class="clinical-section dashboard-side">
        <header class="section-toolbar">
          <h2>挂号状态构成</h2>
        </header>
        <div v-if="hasSynced" class="workload-chart">
          <div class="composition-total"><strong>{{ registrations.length }}</strong><span>该医生累计挂号</span></div>
          <div v-if="registrations.length" class="composition-track" role="img" :aria-label="statusComposition.map((item) => `${item.label} ${item.value}`).join('，')"><i v-for="item in statusComposition" :key="item.label" :class="item.className" :style="{ width: `${item.value / registrations.length * 100}%` }" /></div>
          <div v-else class="composition-empty">暂无挂号数据</div>
          <div class="composition-legend"><div v-for="item in statusComposition" :key="item.label"><i :class="item.className" /><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div></div>
        </div><p v-else class="dashboard-awaiting">等待同步</p>
      </aside>

      <aside class="clinical-section dashboard-side">
        <header class="section-toolbar">
          <h2>风险与未读</h2>
          <RouterLink class="button compact-action" to="/notifications">全部</RouterLink>
        </header>
        <div v-if="hasSynced" class="clinical-feed">
          <article v-for="item in notifications.slice(0, 6)" :key="String(item.notificationId)" class="feed-row">
            <strong>{{ item.title }}</strong>
            <span>{{ item.content }}</span>
          </article>
          <EmptyState v-if="!notifications.length" title="暂无通知" message="" />
        </div><p v-else class="dashboard-awaiting">等待同步</p>
      </aside>
    </div>
  </section>
</template>
