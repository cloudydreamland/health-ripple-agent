<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { statusText, useAdminWorkflowStore, useAuthStore } from "@smart-cloud-brain/shared-api";
import { AppShell, TopBar } from "@smart-cloud-brain/shared-ui";

const auth = useAuthStore();
const workflow = useAdminWorkflowStore();
const router = useRouter();
const route = useRoute();
const { session, permissionError } = storeToRefs(auth);
const { departments, doctors, triageDesk, refreshErrors } = storeToRefs(workflow);
const loading = ref(false);
const loaded = ref(false);
const isDashboard = computed(() => route.name === "admin-dashboard");
provide("admin-dashboard-status", { loading, loaded });
let unbind: (() => void) | null = null;

const highRisk = computed(() => triageDesk.value.filter((item) => ["MANUAL_REQUIRED", "HIGH"].includes(String(item.status))).length);
const navGroups = computed(() => [
  { label: "运维入口", items: [
    { label: "工作台", to: "/" },
    { label: "科室", to: "/departments", badge: isDashboard.value && refreshErrors.value.departments ? "!" : departments.value.length },
    { label: "医生", to: "/doctors", badge: isDashboard.value && refreshErrors.value.doctors ? "!" : doctors.value.length },
    { label: "药品", to: "/drugs" },
    { label: "排班", to: "/schedule" },
    { label: "分诊台", to: "/triage-desk", badge: isDashboard.value && refreshErrors.value.triageDesk ? "!" : highRisk.value },
  ] },
  { label: "配置", items: [
    { label: "知识库", to: "/knowledge" },
    { label: "提示词", to: "/prompts" },
    { label: "字典", to: "/dicts" },
    { label: "搜索", to: "/search" },
  ] },
]);

async function refresh() {
  if (!session.value || !auth.requireRole("ADMIN")) return;
  loading.value = true;
  try {
    await workflow.refresh(auth.token());
  } finally {
    loading.value = false;
    loaded.value = true;
  }
}

function logout() {
  auth.logout();
  router.push({ name: "admin-login" });
}

onMounted(async () => {
  unbind = auth.bindUnauthorized();
  await refresh();
});

onBeforeUnmount(() => unbind?.());
</script>

<template>
  <AppShell
    :class="{ 'admin-dashboard-shell': isDashboard }"
    mark="管"
    :title="isDashboard ? 'DuMate' : '运营管理工作台'"
    :subtitle="isDashboard ? '管理端' : '基础数据 · 号源 · 知识库'"
    :user-name="session?.name"
    :user-meta="`${statusText(session?.role, '')} #${session?.userId || ''}`"
    :nav-groups="navGroups"
    @logout="logout"
  >
    <template #user>
      <div class="row-meta"><span class="tag success">已登录</span><span class="tag warning">{{ highRisk }} 条需关注</span></div>
    </template>
    <TopBar v-if="!isDashboard" eyebrow="管理端" title="基础数据、号源与智能配置统一维护" description="管理端强调批量浏览、快速编辑、分诊改派和数据发布状态。">
      <template #actions><button type="button" :disabled="loading" @click="refresh">{{ loading ? '正在刷新' : '刷新数据' }}</button></template>
    </TopBar>
    <div class="admin-notices"><div v-if="permissionError" class="notice error">{{ permissionError }}</div></div>
    <RouterView @refresh="refresh" />
  </AppShell>
</template>
