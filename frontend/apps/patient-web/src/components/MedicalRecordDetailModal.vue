<script setup lang="ts">
import { Modal, EmptyState } from "@smart-cloud-brain/shared-ui";
import { fieldText, type DataRow } from "@smart-cloud-brain/shared-api";

defineProps<{ open: boolean; record: DataRow | null }>();
defineEmits<{ close: [] }>();
</script>

<template>
  <Modal :open="open" title="病历详情" description="以下内容以医生最终保存记录为准。" @close="$emit('close')">
    <div v-if="record" class="patient-detail-shell patient-record-detail">
      <div class="patient-detail-lead">
        <div>
          <span class="patient-detail-kicker">病历 #{{ fieldText(record, "medicalRecordId") }}</span>
          <h3>{{ fieldText(record, "diagnosis", "诊断待补充") }}</h3>
        </div>
        <span class="patient-detail-source">{{ record.aiGenerated ? "医生确认的智能草稿" : "医生录入" }}</span>
      </div>
      <dl class="patient-detail-record-grid">
        <div><dt>主诉</dt><dd>{{ fieldText(record, "chiefComplaint", "未记录") }}</dd></div>
        <div><dt>现病史</dt><dd>{{ fieldText(record, "presentIllness", "未记录") }}</dd></div>
        <div><dt>既往史</dt><dd>{{ fieldText(record, "pastHistory", "未记录") }}</dd></div>
        <div class="patient-detail-advice"><dt>处理建议</dt><dd>{{ fieldText(record, "treatmentAdvice", "未记录") }}</dd></div>
      </dl>
    </div>
    <EmptyState v-else title="未选择病历" message="请选择一条病历查看详情。" />
  </Modal>
</template>
