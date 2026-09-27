<script setup lang="ts">
import { computed, ref } from "vue";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, toNumber, useAuthStore, useDoctorWorkflowStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { EmptyState, ErrorState, LoadingState } from "@smart-cloud-brain/shared-ui";
import DoctorStatusTag from "../components/DoctorStatusTag.vue";
import NotificationDetailModal from "../components/NotificationDetailModal.vue";
import DoctorPageHeader from "../components/DoctorPageHeader.vue";

const emit = defineEmits<{ refresh: [] }>();
const auth = useAuthStore();
const workflow = useDoctorWorkflowStore();
const { notifications } = storeToRefs(workflow);
const selected = ref<DataRow | null>(null);
const error = ref("");
const notice = ref("");
const loading = ref(false);
const view = ref("UNREAD");
const visibleNotifications = computed(() => notifications.value.filter((item) => view.value === "ALL" || fieldText(item, "readStatus") === view.value).sort((a, b) => fieldText(b, "createdAt").localeCompare(fieldText(a, "createdAt"))));
const unreadCount = computed(() => notifications.value.filter((item) => fieldText(item, "readStatus") !== "READ").length);

async function refresh() {
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    await workflow.refresh(auth.token());
  } catch (err) {
    error.value = formatApiError(err, "通知列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function markRead(item = selected.value) {
  if (!item) return;
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    await api.markNotificationRead(auth.token(), toNumber(item.notificationId));
    selected.value = null;
    emit("refresh");
    await refresh();
    notice.value = "通知已标记为已读。";
  } catch (err) {
    error.value = formatApiError(err, "标记通知失败");
  } finally {
    loading.value = false;
  }
}

refresh();
</script>

<template>
  <section class="doctor-page notifications-page">
    <DoctorPageHeader title="通知中心" :description="`未读 ${unreadCount} 条 · 共 ${notifications.length} 条`">
      <template #actions><button type="button" :disabled="loading" @click="refresh">{{ loading ? '同步中…' : '↻ 刷新通知' }}</button></template>
    </DoctorPageHeader>
    <div v-if="notifications.length" class="notification-controls" role="group" aria-label="筛选通知"><button type="button" :class="{ selected: view === 'UNREAD' }" :aria-pressed="view === 'UNREAD'" @click="view = 'UNREAD'">未读 <span>{{ unreadCount }}</span></button><button type="button" :class="{ selected: view === 'ALL' }" :aria-pressed="view === 'ALL'" @click="view = 'ALL'">全部</button><button type="button" :class="{ selected: view === 'READ' }" :aria-pressed="view === 'READ'" @click="view = 'READ'">已读</button></div>
    <div v-if="notifications.length || loading || error" class="notification-stream">
      <ErrorState v-if="error" :message="error" />
      <div v-if="notice" class="notice success">{{ notice }}</div>
      <LoadingState v-if="loading" title="正在同步通知" />
      <div v-else-if="visibleNotifications.length" class="list">
        <article v-for="item in visibleNotifications" :key="String(item.notificationId)" class="list-row" :class="{ unread: fieldText(item, 'readStatus') !== 'READ' }">
          <span class="event-marker" aria-hidden="true" />
          <div class="row-main">
            <strong>{{ fieldText(item, "title") }}</strong>
            <p>{{ fieldText(item, "content") }}</p>
            <div class="row-meta">
              <DoctorStatusTag :status="item.riskLevel || 'INFO'" />
              <DoctorStatusTag :status="item.readStatus" />
            </div>
          </div>
          <div class="toolbar">
            <button type="button" @click="selected = item">详情</button>
            <button type="button" :disabled="fieldText(item, 'readStatus') === 'READ'" @click="markRead(item)">已读</button>
          </div>
        </article>
      </div>
      <EmptyState v-else title="当前筛选下暂无通知" message="试试其他筛选条件。" />
    </div>
    <div v-else class="notification-empty"><span class="notification-empty-mark" aria-hidden="true">✓</span><div><strong>目前没有通知</strong><p>有新的接诊或风险提醒时，会显示在这里。</p></div></div>
    <NotificationDetailModal :open="Boolean(selected)" :notification="selected" @close="selected = null" @read="markRead()" />
  </section>
</template>
