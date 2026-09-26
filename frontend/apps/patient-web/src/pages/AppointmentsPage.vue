<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, statusClass, toNumber, useAuthStore, usePatientWorkflowStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState, StatusTag } from "@smart-cloud-brain/shared-ui";
import CancelAppointmentModal from "../components/CancelAppointmentModal.vue";
import PatientIcon from "../components/PatientIcon.vue";
import { patientStatusText } from "../format";

type AppointmentFilter = "ALL" | "ACTIVE" | "COMPLETED" | "CANCELLED";
const PAGE_SIZE = 8;
const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { registrations } = storeToRefs(workflow);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const notice = ref("");
const selected = ref<DataRow | null>(null);
const activeFilter = ref<AppointmentFilter>("ALL");
const currentPage = ref(1);
const listAnchor = ref<HTMLElement | null>(null);

function category(item: DataRow): AppointmentFilter {
  const status = String(item.status ?? "").toUpperCase();
  if (status === "CANCELLED") return "CANCELLED";
  if (status === "COMPLETED" || status === "FINISHED") return "COMPLETED";
  return "ACTIVE";
}
function canCancel(item: DataRow) {
  return category(item) === "ACTIVE";
}
const filters = computed(() => ([
  { key: "ALL" as const, label: "全部", count: registrations.value.length },
  { key: "ACTIVE" as const, label: "进行中", count: registrations.value.filter((item) => category(item) === "ACTIVE").length },
  { key: "COMPLETED" as const, label: "已完成", count: registrations.value.filter((item) => category(item) === "COMPLETED").length },
  { key: "CANCELLED" as const, label: "已取消", count: registrations.value.filter((item) => category(item) === "CANCELLED").length },
]));
const filteredRegistrations = computed(() => activeFilter.value === "ALL"
  ? registrations.value
  : registrations.value.filter((item) => category(item) === activeFilter.value));
const pageCount = computed(() => Math.max(1, Math.ceil(filteredRegistrations.value.length / PAGE_SIZE)));
const pageRegistrations = computed(() => filteredRegistrations.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE));
const visiblePages = computed(() => {
  const count = Math.min(5, pageCount.value);
  const start = Math.max(1, Math.min(currentPage.value - 2, pageCount.value - count + 1));
  return Array.from({ length: count }, (_, index) => start + index);
});
watch(activeFilter, () => { currentPage.value = 1; });
watch(pageCount, (count) => { currentPage.value = Math.min(currentPage.value, count); });

function appointmentDay(value: unknown) {
  const timestamp = Date.parse(String(value ?? ""));
  return Number.isFinite(timestamp)
    ? new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" }).format(timestamp)
    : String(value ?? "-");
}
function appointmentClock(value: unknown) {
  const timestamp = Date.parse(String(value ?? ""));
  return Number.isFinite(timestamp)
    ? new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }).format(timestamp)
    : "--:--";
}
async function goPage(page: number) {
  currentPage.value = page;
  await nextTick();
  listAnchor.value?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
}
async function refresh() {
  loading.value = true;
  error.value = "";
  try {
    await workflow.refreshAuthenticated(auth.token());
  } catch (err) {
    error.value = formatApiError(err, "挂号记录加载失败");
  } finally {
    loading.value = false;
  }
}
async function cancel() {
  if (!selected.value) return;
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    await api.cancelRegistration(auth.token(), toNumber(selected.value.registrationId));
    selected.value = null;
    await refresh();
    notice.value = "挂号已取消。";
  } catch (err) {
    error.value = formatApiError(err, "取消挂号失败");
  } finally {
    saving.value = false;
  }
}
refresh();
</script>

<template>
  <section class="panel patient-service-page patient-appointments-page">
    <header class="panel-header patient-rich-header">
      <div class="panel-title"><span class="patient-header-kicker">就诊服务 / 挂号</span><h2>我的挂号</h2><p v-if="filters[1].count">{{ filters[1].count }} 条进行中 · {{ filters[2].count }} 条已完成</p></div>
      <div class="patient-header-aside"><span class="patient-header-count"><PatientIcon name="appointments" /><strong>{{ registrations.length }}</strong><small>条记录</small></span><button type="button" :disabled="loading" @click="refresh">刷新</button></div>
    </header>
    <div class="panel-body patient-appointments-body">
      <ErrorState v-if="error" :message="error" />
      <div v-if="notice" class="notice success">{{ notice }}</div>
      <LoadingState v-if="loading" />
      <template v-else-if="registrations.length">
        <div class="patient-appointment-filters" role="group" aria-label="筛选挂号记录">
          <button v-for="filter in filters" :key="filter.key" type="button" class="patient-appointment-filter"
                  :class="{ active: activeFilter === filter.key }" :aria-pressed="activeFilter === filter.key"
                  @click="activeFilter = filter.key">
            <span>{{ filter.label }}</span><strong>{{ filter.count }}</strong>
          </button>
        </div>
        <div ref="listAnchor" class="patient-appointment-list-anchor">
          <div class="patient-appointment-list-heading"><strong>{{ filters.find((filter) => filter.key === activeFilter)?.label }}记录</strong><span>共 {{ filteredRegistrations.length }} 条</span></div>
          <div v-if="pageRegistrations.length" class="patient-appointment-grid">
            <article v-for="item in pageRegistrations" :key="String(item.registrationId)" class="patient-appointment-card" :data-status="String(item.status ?? '').toUpperCase()">
              <div class="patient-appointment-card-top">
                <div class="patient-appointment-when"><span>{{ appointmentDay(item.appointmentTime) }}</span><strong>{{ appointmentClock(item.appointmentTime) }}</strong></div>
                <StatusTag :status="patientStatusText(item.status)" :tone="statusClass(item.status)" />
              </div>
              <div class="patient-appointment-identity"><strong>{{ fieldText(item, "departmentName") }}</strong><span>{{ fieldText(item, "doctorName") }}</span></div>
              <div class="patient-appointment-card-foot">
                <span>挂号 #{{ fieldText(item, "registrationId") }}</span>
                <button v-if="canCancel(item)" class="danger" type="button" :aria-label="`取消挂号 ${fieldText(item, 'registrationId')}`" @click="selected = item">取消挂号</button>
              </div>
            </article>
          </div>
          <EmptyState v-else title="此分类暂无记录" message="可切换上方分类查看其他挂号。" />
        </div>
        <nav v-if="pageCount > 1" class="patient-appointments-pagination" aria-label="挂号记录分页">
          <span>第 {{ currentPage }} / {{ pageCount }} 页</span>
          <div>
            <button type="button" :disabled="currentPage === 1" @click="goPage(currentPage - 1)">上一页</button>
            <button v-for="page in visiblePages" :key="page" type="button" :class="{ active: page === currentPage }"
                    :aria-current="page === currentPage ? 'page' : undefined" :aria-label="`第 ${page} 页`" @click="goPage(page)">{{ page }}</button>
            <button type="button" :disabled="currentPage === pageCount" @click="goPage(currentPage + 1)">下一页</button>
          </div>
        </nav>
      </template>
      <EmptyState v-else title="暂无挂号记录" message="从号源页面选择医生后，记录会显示在这里。" />
    </div>
    <CancelAppointmentModal :open="Boolean(selected)" :busy="saving" @close="selected = null" @confirm="cancel" />
  </section>
</template>
