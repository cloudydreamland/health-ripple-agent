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
    <DoctorPageHeader eyebrow="WORKSPACE STATUS" title="工作区设置" index="06" />
    <div class="settings-layout">
      <section class="settings-identity"><span class="settings-section-index">01 / 登录身份</span><div class="settings-avatar">{{ (auth.session?.name || '医').slice(0, 1) }}</div><h2>{{ auth.session?.name || '医生' }}</h2><p>医生账号 #{{ auth.session?.userId || '—' }}</p><div class="identity-stamp">医生工作区 · 已登录</div></section>
      <div class="settings-details">
        <section><span class="settings-section-index">02 / 数据同步</span><h2>连接状态</h2><dl><div><dt>数据</dt><dd>{{ sync?.syncError.value ? '同步异常' : sync?.hasSynced.value ? '已同步' : '等待同步' }}</dd></div><div><dt>通知</dt><dd>{{ sync?.connectionStatus.value || '未连接' }}</dd></div><div><dt>上次同步</dt><dd>{{ sync?.lastSyncedAt.value || '—' }}</dd></div></dl><p v-if="sync?.syncError.value" class="settings-warning">{{ sync.syncError.value }}</p></section>
        <section><span class="settings-section-index">03 / 安全说明</span><h2>临床审核</h2><p>智能草稿由医生确认。处方内容变更后须重新完成风险审核，高风险处方需要再次确认。</p></section>
      </div>
    </div>
  </section>
</template>
