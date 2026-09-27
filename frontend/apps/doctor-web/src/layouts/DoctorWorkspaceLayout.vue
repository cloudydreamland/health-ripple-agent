<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import {
  notificationWebSocketUrl,
  formatApiError,
  statusText,
  useAuthStore,
  useDoctorWorkflowStore,
} from "@smart-cloud-brain/shared-api";
import { doctorSyncKey } from "../doctorSync";

const auth = useAuthStore();
const workflow = useDoctorWorkflowStore();
const router = useRouter();
const route = useRoute();
const { session, permissionError } = storeToRefs(auth);
const { registrations, notifications } = storeToRefs(workflow);
const loading = ref(false);
const navCollapsed = ref(false);
const mobileNavOpen = ref(false);
const hasSynced = ref(false);
const syncError = ref("");
const lastSyncedAt = ref("");
const socketStatus = ref("未连接");
provide(doctorSyncKey, { hasSynced, syncError, lastSyncedAt, connectionStatus: socketStatus });
let socket: WebSocket | null = null;
let pollTimer: number | null = null;
let reconnectTimer: number | null = null;
let unbind: (() => void) | null = null;

const unread = computed(() => notifications.value.filter((item) => String(item.readStatus) !== "READ").length);
const activeQueue = computed(() => registrations.value.filter((item) => ["CREATED", "CHECKED_IN", "CONFIRMED"].includes(String(item.status))).length);
const navItems = computed(() => [
  { label: "工作台", to: "/", icon: "M3 3h8v8H3zM13 3h8v5h-8zM13 10h8v11h-8zM3 13h8v8H3z", badge: 0 },
  { label: "接诊队列", to: "/queue", icon: "M4 5h16M4 12h16M4 19h16M7 3v4M7 10v4M7 17v4", badge: activeQueue.value },
  { label: "病历", to: "/records", icon: "M6 3h9l3 3v15H6zM14 3v4h4M9 11h6M9 15h6", badge: 0 },
  { label: "处方", to: "/prescriptions", icon: "M12 4v16M4 12h16M6 3h12v18H6z", badge: 0 },
  { label: "通知", to: "/notifications", icon: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4", badge: unread.value },
  { label: "设置", to: "/settings", icon: "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4m10 10 1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0", badge: 0 },
]);
const currentPage = computed(() => route.path.startsWith("/consult/") ? "接诊工作区" : navItems.value.find((item) => item.to === route.path)?.label || "医生工作台");
watch(() => route.path, () => { mobileNavOpen.value = false; });

function toggleDesktopNav() {
  navCollapsed.value = !navCollapsed.value;
  window.localStorage.setItem("doctor-nav-collapsed", navCollapsed.value ? "1" : "0");
}

async function refresh() {
  if (!session.value || !auth.requireRole("DOCTOR")) return;
  loading.value = true;
  try {
    await workflow.refresh(auth.token());
    hasSynced.value = true;
    syncError.value = "";
    lastSyncedAt.value = new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit" }).format(new Date());
  } catch (err) {
    syncError.value = formatApiError(err, "同步失败，请重试。");
    throw err;
  } finally {
    loading.value = false;
  }
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = window.setInterval(() => refresh().catch(() => undefined), 15000);
}

function stopRealtime() {
  if (socket) {
    socket.onopen = null;
    socket.onmessage = null;
    socket.onerror = null;
    socket.onclose = null;
    socket.close();
  }
  socket = null;
  if (pollTimer) window.clearInterval(pollTimer);
  if (reconnectTimer) window.clearTimeout(reconnectTimer);
  pollTimer = null;
  reconnectTimer = null;
}

function connectNotifications() {
  if (!session.value) return;
  reconnectTimer = null;
  socketStatus.value = pollTimer ? "轮询中" : "连接中";
  socket = new WebSocket(notificationWebSocketUrl(auth.token()));
  socket.onopen = () => {
    socketStatus.value = "实时";
    if (pollTimer) window.clearInterval(pollTimer);
    pollTimer = null;
  };
  socket.onmessage = () => refresh().catch(() => undefined);
  socket.onerror = () => { socketStatus.value = "轮询"; startPolling(); };
  socket.onclose = () => {
    if (session.value) {
      socketStatus.value = "轮询中";
      startPolling();
      reconnectTimer = window.setTimeout(connectNotifications, 5000);
    }
  };
}

function logout() {
  stopRealtime();
  auth.logout();
  router.push({ name: "doctor-login" });
}

function selectNav(event: MouseEvent) {
  mobileNavOpen.value = false;
  if (event.detail > 0) (event.currentTarget as HTMLElement | null)?.blur();
}

onMounted(async () => {
  navCollapsed.value = window.localStorage.getItem("doctor-nav-collapsed") === "1";
  unbind = auth.bindUnauthorized();
  await refresh().catch(() => undefined);
  connectNotifications();
});

onBeforeUnmount(() => {
  unbind?.();
  stopRealtime();
});
</script>

<template>
  <div class="doctor-shell" :class="{ 'nav-collapsed': navCollapsed, 'mobile-nav-open': mobileNavOpen }">
    <button v-if="mobileNavOpen" class="doctor-nav-backdrop" type="button" aria-label="关闭导航" @click="mobileNavOpen = false" />
    <aside class="doctor-nav" aria-label="医生端导航" @keydown.esc="mobileNavOpen = false">
      <div class="doctor-nav-head">
        <RouterLink class="doctor-mark" to="/" aria-label="DuMate 医生工作台"><span class="brand-symbol">✚</span><span class="brand-word">DuMate<small>医生工作台</small></span></RouterLink>
        <button class="doctor-nav-toggle" type="button" :aria-label="navCollapsed ? '展开导航' : '收起导航'" :aria-expanded="!navCollapsed" aria-controls="doctor-primary-nav" @click="toggleDesktopNav">{{ navCollapsed ? '›' : '‹' }}</button>
        <button class="doctor-mobile-close" type="button" aria-label="关闭导航" @click="mobileNavOpen = false">×</button>
      </div>
      <div class="nav-section-label">临床工作区</div>
      <nav id="doctor-primary-nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          class="doctor-nav-link"
          :class="{ active: route.path === item.to || (item.to !== '/' && route.path.startsWith(item.to)) }"
          :to="item.to"
          :aria-label="item.label"
          :title="item.label"
          @click="selectNav"
        >
          <svg class="doctor-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="item.icon" /></svg>
          <span class="doctor-nav-label">{{ item.label }}</span>
          <b v-if="item.badge">{{ item.badge }}</b>
        </RouterLink>
      </nav>
    </aside>

    <div class="doctor-app">
      <header class="doctor-topline">
        <button class="doctor-mobile-menu" type="button" aria-label="打开导航" :aria-expanded="mobileNavOpen" @click="mobileNavOpen = true">☰</button>
        <span class="topline-location">{{ currentPage }}</span>
        <div class="doctor-session">
          <strong>{{ session?.name || "医生" }}</strong>
          <span>{{ statusText(session?.role, "医生") }} #{{ session?.userId || "-" }}</span>
        </div>
        <div class="doctor-topline-status">
          <span v-if="hasSynced" class="topline-count">队列 {{ activeQueue }}</span>
          <span v-if="hasSynced" class="topline-count">未读 {{ unread }}</span>
          <span class="connection-state"><i aria-hidden="true" />通知 {{ socketStatus }}</span>
          <button type="button" :disabled="loading" @click="refresh().catch(() => undefined)">{{ loading ? '同步中…' : '同步' }}</button>
          <button type="button" @click="logout">退出</button>
        </div>
      </header>

      <main class="doctor-main">
        <div v-if="permissionError" class="clinical-alert danger">{{ permissionError }}</div>
        <div v-if="syncError" class="clinical-alert danger sync-alert" role="alert"><span>{{ hasSynced ? '数据未更新：' : '' }}{{ syncError }}<small v-if="hasSynced">上次同步 {{ lastSyncedAt }}</small></span><button type="button" :disabled="loading" @click="refresh().catch(() => undefined)">重试</button></div>
        <RouterView @refresh="refresh" />
      </main>
    </div>
  </div>
</template>
