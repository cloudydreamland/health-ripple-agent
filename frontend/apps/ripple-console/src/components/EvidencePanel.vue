<script setup lang="ts">
import { ref } from "vue";
import type { ChainVerifyResult, EvidenceView } from "../types";
import { exportFhir, verifyChain } from "../api";

/**
 * 朱砂印鉴链 —— 哈希链的东方表达。
 * prevHash 与 hash 各成一枚印，链式相扣；「校验」如逐枚验印，
 * 结论以盖印方式落定（CHAIN VERIFIED / TAMPERED）。
 */
const props = defineProps<{ evidence: EvidenceView | null }>();

const verifying = ref(false);
const verifyResult = ref<ChainVerifyResult | null>(null);
const exporting = ref(false);
const fhirJson = ref<string>("");
const fhirError = ref("");

function shortHash(h: unknown): string {
  const s = String(h ?? "");
  return s.length > 18 ? s.slice(0, 16) + "…" : s;
}

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
    <!-- 印鉴链 -->
    <div class="seal-chain">
      <div class="seal prev">
        <span class="seal-tag mono">PREV 印</span>
        <span class="seal-hash mono" :title="String(evidence.prevHash)">{{ shortHash(evidence.prevHash) }}</span>
        <span class="seal-glyph">印</span>
      </div>
      <div class="chain-link">
        <svg width="52" height="12" viewBox="0 0 52 12">
          <line x1="0" y1="6" x2="52" y2="6" stroke="#958d74" stroke-width="1.2" stroke-dasharray="3 4" />
        </svg>
        <span class="mono link-label">SHA-256 链式相扣</span>
      </div>
      <div class="seal current">
        <span class="seal-tag mono">HASH 印 · 本条</span>
        <span class="seal-hash mono" :title="String(evidence.hash)">{{ shortHash(evidence.hash) }}</span>
        <span class="seal-glyph">印</span>
      </div>
    </div>

    <div class="meta-row">
      <span class="k mono">DECISION ID</span>
      <span class="mono v id">{{ evidence.decisionId }}</span>
      <span class="tag CYAN">{{ evidence.decisionType }}</span>
      <span class="mono conf">置信度 {{ evidence.confidence }}</span>
    </div>
    <p class="hint chain-note">任何一条被篡改，从该枚印起全部验印失败，篡改点精确定位——责任证据由此固化，并可导出 HL7 FHIR R4 Provenance。</p>

    <div class="actions">
      <button :disabled="verifying" @click="verify">{{ verifying ? "验印中 …" : "逐枚验印" }}</button>
      <button class="ghost" :disabled="exporting" @click="exportFhirJson">
        {{ exporting ? "导出中 …" : "导出 FHIR Provenance" }}
      </button>
    </div>

    <!-- 盖章结论（offline=true 时如实呈现快照状态，不冒充实时校验结论） -->
    <div v-if="verifyResult && verifyResult.offline === true" class="stamp offline">
      <b>离线快照</b>
      <span class="mono">SNAPSHOT · 实时验印需连接后端</span>
      <span class="hint offline-note">{{ verifyResult.message }}</span>
    </div>
    <div v-else-if="verifyResult" class="stamp" :class="verifyResult.valid ? 'ok' : 'bad'">
      <template v-if="verifyResult.valid">
        <b>验印通过</b>
        <span class="mono">CHAIN VERIFIED · {{ verifyResult.count }} RECORDS</span>
      </template>
      <template v-else>
        <b>验印失败</b>
        <span class="mono">TAMPERED AT · {{ verifyResult.brokenAt }}</span>
      </template>
    </div>

    <pre v-if="fhirJson" class="fhir-pre mono">{{ fhirJson }}</pre>
    <p v-if="fhirError" class="hint">{{ fhirError }}</p>
  </div>
  <p v-else class="hint">尚无证据记录。</p>
</template>

<style scoped>
.evidence { display: flex; flex-direction: column; gap: 11px; }
.seal-chain { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.seal {
  position: relative;
  border: 2px solid var(--red);
  border-radius: 7px;
  padding: 8px 12px 9px;
  background: transparent;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.seal.prev { transform: rotate(-1.6deg); opacity: 0.82; }
.seal.current { transform: rotate(1.2deg); box-shadow: 0 2px 10px rgba(189, 64, 51, 0.14); }
.seal-tag { font-size: 8.5px; letter-spacing: 1.2px; color: var(--red); }
.seal-hash { font-size: 11px; color: var(--ink); }
.seal-glyph {
  position: absolute;
  right: 3px; bottom: 1px;
  font-family: var(--font-serif);
  font-size: 11px;
  color: var(--red);
  opacity: 0.45;
}
.chain-link { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.link-label { font-size: 8.5px; color: var(--faint); letter-spacing: 0.5px; white-space: nowrap; }
.meta-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.k { font-size: 9.5px; color: var(--faint); letter-spacing: 1.2px; }
.v.id { color: var(--cyan); font-size: 11.5px; word-break: break-all; }
.conf { color: var(--ink-soft); font-size: 11px; }
.chain-note { margin: 0; font-size: 10.5px; }
.actions { display: flex; gap: 10px; }
.stamp {
  align-self: flex-start;
  border: 3px solid;
  border-radius: 9px;
  padding: 8px 18px;
  transform: rotate(-3deg);
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-family: var(--font-serif);
  background: transparent;
  animation: stampIn 0.38s cubic-bezier(0.2, 1.4, 0.4, 1) both;
}
@keyframes stampIn {
  from { transform: rotate(-9deg) scale(1.9); opacity: 0; }
  to { transform: rotate(-3deg) scale(1); opacity: 1; }
}
.stamp b { font-size: 18px; letter-spacing: 4px; }
.stamp span { font-size: 9.5px; letter-spacing: 0.8px; font-family: var(--font-mono); }
.stamp.ok { color: var(--red); border-color: var(--red); }
.stamp.bad { color: var(--ink); border-color: var(--ink); }
.stamp.offline { color: #958d74; border-color: #958d74; animation: none; transform: rotate(-2deg); }
.offline-note { font-size: 10px; max-width: 280px; white-space: normal; letter-spacing: 0; }
.fhir-pre {
  max-height: 240px;
  overflow: auto;
  background: var(--card-inset);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  color: var(--ink-soft);
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 10.5px;
  margin: 0;
}
</style>
