<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, formatApiError, useAuthStore, useDoctorWorkflowStore } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState } from "@smart-cloud-brain/shared-ui";
import DoctorStatusTag from "../components/DoctorStatusTag.vue";
import DoctorPageHeader from "../components/DoctorPageHeader.vue";
import DoctorPager from "../components/DoctorPager.vue";
import DoctorSelect from "../components/DoctorSelect.vue";

const auth = useAuthStore();
const workflow = useDoctorWorkflowStore();
const { registrations } = storeToRefs(workflow);
const filter = ref("ACTIVE");
const keyword = ref("");
const sort = ref("appointment");
const sortOptions = [{ value: "appointment", label: "预约时间较早" }, { value: "recent", label: "预约时间较晚" }, { value: "name", label: "患者姓名" }];
const page = ref(1);
const loading = ref(false);
const error = ref("");
const loaded = ref(false);
const activeStatuses = ["CREATED", "CHECKED_IN", "CONFIRMED"];
const filters = [
  { label: "待处理", value: "ACTIVE" }, { label: "全部", value: "" },
  { label: "已签到", value: "CHECKED_IN" }, { label: "已确认", value: "CONFIRMED" },
  { label: "已完成", value: "COMPLETED" }, { label: "已取消", value: "CANCELLED" },
];
const activeCount = computed(() => registrations.value.filter((item) => activeStatuses.includes(fieldText(item, "status"))).length);
const rows = computed(() => registrations.value.filter((item) => {
  const haystack = [fieldText(item, "patientName", ""), fieldText(item, "patientId", ""), fieldText(item, "departmentName", ""), fieldText(item, "registrationId", "")].join(" ").toLowerCase();
  const status = fieldText(item, "status");
  return (filter.value === "ACTIVE" ? activeStatuses.includes(status) : !filter.value || status === filter.value)
    && (!keyword.value || haystack.includes(keyword.value.toLowerCase()));
}).sort((a, b) => {
  if (sort.value === "name") return fieldText(a, "patientName").localeCompare(fieldText(b, "patientName"), "zh-CN");
  if (sort.value === "recent") return fieldText(b, "appointmentTime").localeCompare(fieldText(a, "appointmentTime"));
  return fieldText(a, "appointmentTime").localeCompare(fieldText(b, "appointmentTime"));
}));
const visibleRows = computed(() => rows.value.slice((page.value - 1) * 20, page.value * 20));
watch([filter, keyword, sort], () => { page.value = 1; });

async function refresh() {
  loading.value = true;
  error.value = "";
  try { await workflow.refresh(auth.token()); loaded.value = true; }
  catch (err) { error.value = formatApiError(err, "队列加载失败"); }
  finally { loading.value = false; }
}
refresh();
</script>

<template>
  <section class="doctor-page queue-page">
    <DoctorPageHeader title="接诊队列" :description="loaded ? `待处理 ${activeCount} 位 · 当前匹配 ${rows.length} 位` : '正在获取队列'">
      <template #actions><button type="button" :disabled="loading" @click="refresh">{{ loading ? '同步中…' : '↻ 刷新队列' }}</button></template>
    </DoctorPageHeader>
    <div class="queue-control-bar">
      <div class="queue-tabs" role="group" aria-label="筛选接诊状态">
        <button v-for="item in filters" :key="item.label" type="button" :class="{ selected: filter === item.value }" :aria-pressed="filter === item.value" @click="filter = item.value">{{ item.label }}<span v-if="item.value === 'ACTIVE' && loaded">{{ activeCount }}</span></button>
      </div>
      <div class="queue-control-bottom">
        <label class="search-field"><span aria-hidden="true">⌕</span><input v-model.trim="keyword" aria-label="搜索姓名、患者 ID、挂号号或科室" placeholder="搜索患者、挂号号或科室" /></label>
        <div class="sort-field"><span>排序</span><DoctorSelect v-model="sort" :options="sortOptions" control-label="排序队列" /></div>
      </div>
    </div>
    <div v-if="error && loaded" class="clinical-alert danger" role="alert">数据未更新：{{ error }}</div>
    <LoadingState v-if="loading && !loaded" title="正在同步队列" />
    <div v-else-if="error && !loaded" class="queue-error"><ErrorState :message="error" /><button type="button" @click="refresh">重试</button></div>
    <div v-else-if="rows.length" class="worklist" role="list" aria-label="接诊队列">
      <div class="worklist-head"><span>患者 / 挂号</span><span>科室</span><span>预约时间</span><span>状态</span><span>操作</span></div>
      <div v-for="item in visibleRows" :key="String(item.registrationId)" class="worklist-item" role="listitem">
        <div class="worklist-main">
          <div class="worklist-patient"><span class="patient-monogram">{{ fieldText(item, 'patientName', '患').slice(0, 1) }}</span><span><strong>{{ fieldText(item, "patientName", `患者${fieldText(item, "patientId")}`) }}</strong><small>挂号 #{{ fieldText(item, "registrationId", "-") }}</small></span></div>
          <span class="worklist-department">{{ fieldText(item, "departmentName", "-") }}</span>
          <time class="worklist-time">{{ fieldText(item, "appointmentTime", "-").replace('T', ' ') }}</time>
          <DoctorStatusTag :status="item.status" />
          <div class="worklist-actions"><RouterLink class="button primary" :to="`/consult/${item.registrationId}`">{{ ['COMPLETED', 'CANCELLED'].includes(fieldText(item, 'status')) ? '查看' : '接诊' }}</RouterLink></div>
        </div>
      </div>
      <DoctorPager v-model:page="page" :total="rows.length" />
    </div>
    <EmptyState v-else title="当前筛选下暂无患者" message="可以切换状态或清除搜索条件。" />
  </section>
</template>
