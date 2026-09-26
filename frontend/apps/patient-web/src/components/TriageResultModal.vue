<script setup lang="ts">
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import Tag from "primevue/tag";
import { fieldText, type DataRow } from "@smart-cloud-brain/shared-api";
import { patientStatusText } from "../format";

defineProps<{ open: boolean; result: DataRow | null }>();
defineEmits<{ close: []; doctors: [] }>();
</script>

<template>
  <Dialog :visible="open" modal header="分诊结果" class="patient-triage-dialog" :style="{ width: 'min(92vw, 620px)' }"
          @update:visible="!$event && $emit('close')">
    <div v-if="result" class="triage-result patient-triage-detail">
      <div class="patient-detail-lead">
        <div>
          <span class="patient-detail-kicker">建议就诊科室</span>
          <h3>{{ fieldText(result, "recommendedDepartment", "待人工确认") }}</h3>
        </div>
        <Tag :value="patientStatusText(result.status)" :severity="result.status === 'MANUAL_REQUIRED' ? 'warn' : 'success'" />
      </div>
      <section class="patient-detail-reason">
        <h4>推荐依据</h4>
        <p>{{ fieldText(result, "reason", "暂无说明") }}</p>
      </section>
      <div class="triage-result-safety">分诊建议用于挂号引导，最终诊断以医生接诊为准。</div>
    </div>
    <template #footer>
      <Button type="button" label="继续编辑" severity="secondary" text @click="$emit('close')" />
      <Button type="button" label="查看号源" @click="$emit('doctors')" />
    </template>
  </Dialog>
</template>
