<script setup lang="ts">
import { Modal } from "@smart-cloud-brain/shared-ui";
import { fieldText, type DataRow } from "@smart-cloud-brain/shared-api";
import DoctorStatusTag from "./DoctorStatusTag.vue";

defineProps<{ open: boolean; notification: DataRow | null }>();
defineEmits<{ close: []; read: [] }>();
</script>

<template>
  <Modal :open="open" title="通知详情" @close="$emit('close')">
    <div v-if="notification" class="stack">
      <DoctorStatusTag :status="notification.riskLevel || 'INFO'" />
      <h3>{{ fieldText(notification, "title") }}</h3>
      <p>{{ fieldText(notification, "content") }}</p>
    </div>
    <template #footer>
      <button type="button" @click="$emit('close')">关闭</button>
      <button type="button" class="primary" @click="$emit('read')">标记已读</button>
    </template>
  </Modal>
</template>
