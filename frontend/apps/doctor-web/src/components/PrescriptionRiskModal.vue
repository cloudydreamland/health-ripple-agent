<script setup lang="ts">
import { Modal } from "@smart-cloud-brain/shared-ui";
import { fieldText, type DataRow } from "@smart-cloud-brain/shared-api";
import DoctorStatusTag from "./DoctorStatusTag.vue";

defineProps<{ open: boolean; result: DataRow | null }>();
defineEmits<{ close: []; confirm: [] }>();
</script>

<template>
  <Modal :open="open" title="处方审核结果" description="高风险处方需要再次确认。" @close="$emit('close')">
    <div v-if="result" class="stack">
      <DoctorStatusTag :status="result.riskLevel" />
      <p>{{ fieldText(result, "suggestions", "请医生复核用药风险。") }}</p>
    </div>
    <template #footer>
      <button type="button" @click="$emit('close')">返回修改</button>
      <button type="button" class="primary" @click="$emit('confirm')">继续确认</button>
    </template>
  </Modal>
</template>
