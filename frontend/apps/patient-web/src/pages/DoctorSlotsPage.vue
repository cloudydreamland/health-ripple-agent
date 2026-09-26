<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, statusClass, toNumber, useAuthStore, usePatientWorkflowStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState, StatusTag } from "@smart-cloud-brain/shared-ui";
import ConfirmAppointmentModal from "../components/ConfirmAppointmentModal.vue";
import { patientStatusText } from "../format";

const PAGE_SIZE = 5;
const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { slots, doctors, departments, triageHistory } = storeToRefs(workflow);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const notice = ref("");
const selectedSlot = ref<DataRow | null>(null);
const confirmOpen = ref(false);
const currentPage = ref(1);
const openDepartmentId = ref<string | null>(null);
const pinnedDepartmentId = ref<string | null>(null);
const blockedHoverId = ref<string | null>(null);

const recommendedDepartment = computed(() => fieldText(triageHistory.value[0], "recommendedDepartment", ""));
function isRecommended(slot: DataRow) {
  return Boolean(recommendedDepartment.value && fieldText(slot, "departmentName", "").includes(recommendedDepartment.value));
}
const orderedSlots = computed(() => [
  ...slots.value.filter(isRecommended),
  ...slots.value.filter((slot) => !isRecommended(slot)),
]);
const recommendedCount = computed(() => slots.value.filter(isRecommended).length);
const pageCount = computed(() => Math.max(1, Math.ceil(orderedSlots.value.length / PAGE_SIZE)));
const pageSlots = computed(() => orderedSlots.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE));
const pageStart = computed(() => (currentPage.value - 1) * PAGE_SIZE + 1);
const pageEnd = computed(() => Math.min(currentPage.value * PAGE_SIZE, orderedSlots.value.length));
const visiblePages = computed(() => {
  const count = Math.min(5, pageCount.value);
  const start = Math.max(1, Math.min(currentPage.value - 2, pageCount.value - count + 1));
  return Array.from({ length: count }, (_, index) => start + index);
});
watch(pageCount, (count) => { currentPage.value = Math.min(currentPage.value, count); });

function slotDate(value: unknown) {
  const timestamp = Date.parse(String(value ?? ""));
  return Number.isFinite(timestamp)
    ? new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" }).format(timestamp)
    : String(value ?? "-");
}
function slotClock(value: unknown) {
  const timestamp = Date.parse(String(value ?? ""));
  return Number.isFinite(timestamp)
    ? new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }).format(timestamp)
    : "--:--";
}
function departmentId(department: DataRow, index: number) {
  return String(department.id ?? department.departmentId ?? index);
}
function previewDepartment(id: string) {
  if (blockedHoverId.value === id) return;
  if (pinnedDepartmentId.value !== id) pinnedDepartmentId.value = null;
  openDepartmentId.value = id;
}
function leaveDepartment(id: string) {
  if (pinnedDepartmentId.value !== id && openDepartmentId.value === id) openDepartmentId.value = null;
  blockedHoverId.value = null;
}
function toggleDepartment(id: string) {
  if (pinnedDepartmentId.value === id) {
    pinnedDepartmentId.value = null;
    openDepartmentId.value = null;
    blockedHoverId.value = id;
  } else {
    pinnedDepartmentId.value = id;
    openDepartmentId.value = id;
    blockedHoverId.value = null;
  }
}

async function refresh() {
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    await workflow.refreshPublicData();
    await workflow.refreshAuthenticated(auth.token());
  } catch (err) {
    error.value = formatApiError(err, "号源加载失败");
  } finally {
    loading.value = false;
  }
}

function choose(slot: DataRow) {
  selectedSlot.value = slot;
  confirmOpen.value = true;
}

async function confirmAppointment() {
  if (!selectedSlot.value) return;
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    await api.createRegistration(auth.token(), {
      doctorId: toNumber(selectedSlot.value.doctorId),
      departmentId: toNumber(selectedSlot.value.departmentId),
      appointmentTime: fieldText(selectedSlot.value, "startTime", ""),
      slotId: toNumber(selectedSlot.value.slotId) || null,
      triageRecordId: toNumber(triageHistory.value[0]?.triageRecordId, 0) || null,
    });
    await workflow.refreshAuthenticated(auth.token());
    confirmOpen.value = false;
    notice.value = "预约已提交，可在“我的挂号”页面查看或取消。";
  } catch (err) {
    error.value = formatApiError(err, "挂号失败");
  } finally {
    saving.value = false;
  }
}

refresh();
</script>

<template>
  <section class="portal-grid patient-service-page patient-slots-page">
    <section class="panel patient-slots-main">
      <header class="panel-header">
        <div class="panel-title"><h2>可预约号源</h2></div>
        <button type="button" :disabled="loading" @click="refresh">刷新号源</button>
      </header>
      <div class="panel-body patient-slots-body">
        <ErrorState v-if="error" :message="error" />
        <div v-if="notice" class="notice success">{{ notice }}</div>
        <div class="patient-slots-summary" aria-label="号源概况">
          <div><span>推荐科室</span><strong>{{ recommendedDepartment || "暂无" }}</strong></div>
          <div><span>医生</span><strong>{{ doctors.length }}</strong></div>
          <div><span>号源</span><strong>{{ slots.length }}</strong></div>
        </div>
        <LoadingState v-if="loading" />
        <template v-else-if="orderedSlots.length">
          <div class="patient-slots-list-heading">
            <div><strong>当前可选</strong><span v-if="recommendedCount">推荐匹配 {{ recommendedCount }} 个</span></div>
            <small>第 {{ pageStart }}–{{ pageEnd }} 条 · 共 {{ orderedSlots.length }} 条</small>
          </div>
          <div class="patient-slots-list">
            <article v-for="slot in pageSlots" :key="String(slot.slotId)" class="patient-slot-row"
                     :class="{ 'is-recommended': isRecommended(slot), 'is-selected': selectedSlot?.slotId === slot.slotId }">
              <div class="patient-slot-time"><span>{{ slotDate(slot.startTime) }}</span><strong>{{ slotClock(slot.startTime) }}</strong></div>
              <div class="patient-slot-person">
                <div><strong>{{ fieldText(slot, "departmentName") }}</strong><span v-if="isRecommended(slot)" class="patient-slot-recommend">智能推荐</span></div>
                <span>{{ fieldText(slot, "doctorName") }}</span>
              </div>
              <div class="patient-slot-capacity"><span>余号</span><strong>{{ fieldText(slot, "remainingCapacity", "0") }} <small>/ {{ fieldText(slot, "capacity", "0") }}</small></strong></div>
              <div class="patient-slot-actions">
                <StatusTag :status="patientStatusText(slot.status)" :tone="statusClass(slot.status)" />
                <button class="primary" type="button" @click="choose(slot)">选择号源</button>
              </div>
            </article>
          </div>
          <nav v-if="pageCount > 1" class="patient-slots-pagination" aria-label="号源分页">
            <span>第 {{ currentPage }} / {{ pageCount }} 页</span>
            <div>
              <button type="button" :disabled="currentPage === 1" @click="currentPage--">上一页</button>
              <button v-for="page in visiblePages" :key="page" type="button" class="patient-page-number"
                      :class="{ active: page === currentPage }" :aria-current="page === currentPage ? 'page' : undefined"
                      :aria-label="`第 ${page} 页`" @click="currentPage = page">{{ page }}</button>
              <button type="button" :disabled="currentPage === pageCount" @click="currentPage++">下一页</button>
            </div>
          </nav>
        </template>
        <EmptyState v-else title="暂无号源" message="目前没有可预约号源，请刷新或稍后再试。" />
      </div>
    </section>
    <aside class="panel patient-departments-panel">
      <header class="panel-header"><div class="panel-title"><h2>科室信息</h2></div></header>
      <div class="panel-body patient-departments-scroll">
        <div v-if="departments.length" class="patient-departments-list">
          <div v-for="(department, index) in departments" :key="departmentId(department, index)" class="patient-department-row"
               @mouseenter="previewDepartment(departmentId(department, index))"
               @mouseleave="leaveDepartment(departmentId(department, index))">
            <button type="button" class="patient-department-trigger" :class="{ 'is-open': openDepartmentId === departmentId(department, index) }"
                    :aria-expanded="openDepartmentId === departmentId(department, index)"
                    :aria-label="`${fieldText(department, 'name')}：${fieldText(department, 'description', '暂无说明')}`"
                    @focus="previewDepartment(departmentId(department, index))"
                    @blur="leaveDepartment(departmentId(department, index))"
                    @click="toggleDepartment(departmentId(department, index))">
              <strong class="patient-department-name" aria-hidden="true">{{ fieldText(department, "name") }}</strong>
              <span class="patient-department-description" aria-hidden="true">{{ fieldText(department, "description", "暂无说明") }}</span>
              <span class="patient-department-chevron" aria-hidden="true">⌄</span>
            </button>
          </div>
        </div>
        <EmptyState v-else title="暂无科室数据" />
      </div>
    </aside>
    <ConfirmAppointmentModal :open="confirmOpen" :slot="selectedSlot" :busy="saving" @close="confirmOpen = false" @confirm="confirmAppointment" />
  </section>
</template>
