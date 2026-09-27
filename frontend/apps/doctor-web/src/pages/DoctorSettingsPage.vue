<script setup lang="ts">
import { inject } from "vue";
import { useAuthStore } from "@smart-cloud-brain/shared-api";
import DoctorPageHeader from "../components/DoctorPageHeader.vue";
import { doctorSyncKey } from "../doctorSync";

const auth = useAuthStore();
const sync = inject(doctorSyncKey);
</script>

<template>
  <section class="doctor-page settings-page">
    <DoctorPageHeader title="工作区设置" />
    <div class="settings-layout">
      <section class="settings-identity"><div class="settings-avatar">{{ (auth.session?.name || '医').slice(0, 1) }}</div><div class="settings-person"><h2>{{ auth.session?.name || '医生' }}</h2><p>医生账号 #{{ auth.session?.userId || '—' }}</p></div><div class="identity-stamp"><i aria-hidden="true" /> 已登录</div></section>
      <div class="settings-details">
        <section><h2>连接状态</h2><dl><div><dt>数据</dt><dd>{{ sync?.syncError.value ? '同步异常' : sync?.hasSynced.value ? '已同步' : '等待同步' }}</dd></div><div><dt>通知</dt><dd>{{ sync?.connectionStatus.value || '未连接' }}</dd></div><div><dt>上次同步</dt><dd>{{ sync?.lastSyncedAt.value || '—' }}</dd></div></dl><p v-if="sync?.syncError.value" class="settings-warning">{{ sync.syncError.value }}</p></section>
        <section><h2>临床审核</h2><p>智能草稿由医生确认。处方内容变更后须重新完成风险审核，高风险处方需要再次确认。</p></section>
      </div>
    </div>
  </section>
</template>
