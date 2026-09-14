<script setup lang="ts">
import { ref } from "vue";
import type { ChainVerifyResult, EvidenceView } from "../types";
import { exportFhir, verifyChain } from "../api";

const props = defineProps<{ evidence: EvidenceView | null }>();

const verifying = ref(false);
const verifyResult = ref<ChainVerifyResult | null>(null);
const exporting = ref(false);
const fhirJson = ref<string>("");
const fhirError = ref("");

async function verify() {
  verifying.value = true;
  try {
    verifyResult.value = await verifyChain();
  } finally {
    verifying.value = false;
  }
}

async function exportFhirJson() {
  if (!props.evidence) return;
  exporting.value = true;
  fhirError.value = "";
  try {
    const fhir = await exportFhir(props.evidence.decisionId);
    fhirJson.value = JSON.stringify(fhir, null, 2);
  } catch (e) {
    fhirError.value = e instanceof Error ? e.message : String(e);
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <div v-if="evidence" class="evidence">
    <div class="chain-block">
      <div class="block-row">
        <span class="blk-label">决策ID</span>
        <span class="mono decision-id">{{ evidence.decisionId }}</span>
      </div>
      <div class="block-row">
        <span class="blk-label">决策类型</span>
        <span class="tag CYAN">{{ evidence.decisionType }}</span>
        <span class="blk-label" style="margin-left: 12px">置信度</span>
        <b>{{ evidence.confidence }}</b>
      </div>
      <div class="block-row">
        <span class="blk-label">prevHash</span>
        <span class="mono hash">{{ String(evidence.prevHash).slice(0, 18) }}…</span>
      </div>
      <div class="block-row">
        <span class="blk-label">hash</span>
        <span class="mono hash current">{{ String(evidence.hash).slice(0, 18) }}…</span>
      </div>
      <p class="hint chain-note">↑ 本条 hash 由 prevHash 链式计算（SHA-256），任何一条被篡改，从该条起全部校验失败。</p>
    </div>

    <div class="actions">
      <button :disabled="verifying" @click="verify">{{ verifying ? "校验中…" : "校验链完整性" }}</button>
      <button class="ghost" :disabled="exporting" @click="exportFhirJson">
        {{ exporting ? "导出中…" : "导出 FHIR Provenance" }}
      </button>
    </div>

    <div v-if="verifyResult" class="verify-result" :class="verifyResult.valid ? 'ok' : 'bad'">
      <template v-if="verifyResult.valid">
        ✅ 链完整：{{ verifyResult.count }} 条决策记录全链校验通过
      </template>
      <template v-else>
        ⚠️ 校验失败，篡改定位：{{ verifyResult.brokenAt }}
      </template>
    </div>

    <pre v-if="fhirJson" class="fhir-pre mono">{{ fhirJson }}</pre>
    <p v-if="fhirError" class="hint">{{ fhirError }}</p>
  </div>
  <p v-else class="hint">尚无证据记录。</p>
</template>

<style scoped>
.chain-block {
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--bg-inset);
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.block-row { display: flex; align-items: center; gap: 8px; }
.blk-label { font-size: 11px; color: var(--text-faint); width: 60px; }
.decision-id { color: var(--cyan); font-size: 11.5px; word-break: break-all; }
.hash { color: var(--text-dim); }
.hash.current { color: var(--green); }
.chain-note { margin: 4px 0 0; }
.actions { display: flex; gap: 10px; margin: 12px 0; }
.verify-result {
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12.5px;
  border: 1px solid;
}
.verify-result.ok { color: var(--green); border-color: rgba(62, 230, 164, 0.4); background: var(--green-bg); }
.verify-result.bad { color: var(--red); border-color: rgba(255, 93, 108, 0.4); background: var(--red-bg); }
.fhir-pre {
  max-height: 260px;
  overflow: auto;
  background: var(--bg-inset);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  color: var(--text-dim);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
