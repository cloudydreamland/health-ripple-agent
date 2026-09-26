<script setup lang="ts">
import { Modal } from "@smart-cloud-brain/shared-ui";
import { fieldText, type DataRow } from "@smart-cloud-brain/shared-api";
import { formatPatientDate } from "../format";

defineProps<{ open: boolean; slot: DataRow | null; busy?: boolean }>();
defineEmits<{ close: []; confirm: [] }>();
</script>

<template>
  <Modal :open="open" title="确认预约" description="请核对科室、医生和就诊时间。" @close="$emit('close')">
    <div v-if="slot" class="patient-detail-shell patient-appointment-detail">
      <div class="patient-detail-lead">
        <div>
          <span class="patient-detail-kicker">就诊时间</span>
          <h3>{{ formatPatientDate(slot.startTime) }}</h3>
        </div>
      </div>
      <dl class="patient-detail-facts">
        <div><dt>就诊科室</dt><dd>{{ fieldText(slot, "departmentName") }}</dd></div>
        <div><dt>接诊医生</dt><dd>{{ fieldText(slot, "doctorName") }}</dd></div>
      </dl>
      <div class="patient-detail-safety">分诊结果仅作为挂号推荐，最终诊断以医生接诊结论为准。</div>
    </div>
    <template #footer>
      <button type="button" :disabled="busy" @click="$emit('close')">更换号源</button>
      <button type="button" class="primary" :disabled="busy || !slot" @click="$emit('confirm')">{{ busy ? "提交中" : "确认挂号" }}</button>
    </template>
  </Modal>
</template>
