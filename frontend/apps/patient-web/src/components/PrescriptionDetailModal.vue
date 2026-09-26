<script setup lang="ts">
import { Modal, EmptyState, StatusTag } from "@smart-cloud-brain/shared-ui";
import { fieldText, statusClass, type DataRow } from "@smart-cloud-brain/shared-api";
import { patientStatusText } from "../format";

defineProps<{ open: boolean; prescription: DataRow | null }>();
defineEmits<{ close: [] }>();

function medicationFrequency(value: unknown) {
  const raw = String(value ?? "").trim();
  const labels: Record<string, string> = {
    qd: "每日一次", bid: "每日两次", tid: "每日三次", qid: "每日四次",
    qhs: "每晚一次", prn: "必要时使用", qod: "隔日一次",
  };
  return labels[raw.toLowerCase()] ?? (/^[a-z.]{2,6}$/i.test(raw) ? "请向医生核对" : raw || "未注明");
}

function medicationUsage(value: unknown) {
  const raw = String(value ?? "").trim();
  const labels: Record<string, string> = {
    oral: "口服", po: "口服", iv: "静脉给药", im: "肌肉注射",
    topical: "外用", inhalation: "吸入",
  };
  return labels[raw.toLowerCase()] ?? (/^[a-z.]{2,12}$/i.test(raw) ? "请向医生核对" : raw || "未注明");
}
</script>

<template>
  <Modal :open="open" title="处方详情" description="请按医嘱用药，如有疑问请联系医生。" @close="$emit('close')">
    <div v-if="prescription" class="patient-detail-shell patient-prescription-detail">
      <div class="patient-detail-lead">
        <div>
          <span class="patient-detail-kicker">处方 #{{ fieldText(prescription, "prescriptionId") }}</span>
          <h3>本次用药</h3>
        </div>
        <StatusTag :status="patientStatusText(prescription.riskLevel, '未审核')" :tone="statusClass(prescription.riskLevel)" />
      </div>
      <dl class="patient-detail-facts">
        <div><dt>处方状态</dt><dd>{{ patientStatusText(prescription.status) }}</dd></div>
        <div><dt>药品数量</dt><dd>{{ ((prescription.items as DataRow[]) || []).length }} 种</dd></div>
      </dl>
      <section class="patient-detail-section">
        <h4>药品明细</h4>
        <ol v-if="(prescription.items as DataRow[])?.length" class="patient-detail-drugs">
          <li v-for="(item, index) in (prescription.items as DataRow[])" :key="index">
            <span class="patient-detail-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="patient-detail-drug-content">
              <strong>{{ fieldText(item, "drugName") }}</strong>
              <dl>
                <div><dt>剂量</dt><dd>{{ fieldText(item, "dosage") }}</dd></div>
                <div><dt>频次</dt><dd>{{ medicationFrequency(item.frequency) }}</dd></div>
                <div><dt>方式</dt><dd>{{ medicationUsage(item.usageMethod) }}</dd></div>
              </dl>
            </div>
          </li>
        </ol>
        <EmptyState v-else title="暂无药品明细" />
      </section>
    </div>
    <EmptyState v-else title="未选择处方" message="请选择一张处方查看明细。" />
  </Modal>
</template>
