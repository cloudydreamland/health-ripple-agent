<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, useAuthStore, usePatientWorkflowStore } from "@smart-cloud-brain/shared-api";
import { FormField } from "@smart-cloud-brain/shared-ui";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import SelectButton from "primevue/selectbutton";
import Textarea from "primevue/textarea";
import Tag from "primevue/tag";
import Message from "primevue/message";
import Skeleton from "primevue/skeleton";
import TriageResultModal from "../components/TriageResultModal.vue";
import { patientStatusText } from "../format";
import { useSpeechRecognition } from "../composables/useSpeech";

const { supported: speechSupported, listening: speechListening, start: speechStart, stop: speechStop } = useSpeechRecognition();
function dictateSymptoms() {
  if (speechListening.value) { speechStop(); return; }
  speechStart((text) => {
    form.symptoms = (form.symptoms ? form.symptoms + "，" : "") + text;
  });
}

const router = useRouter();
const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { triageHistory, triage } = storeToRefs(workflow);
const form = reactive({ symptoms: "", duration: "", severity: "MEDIUM", extra: "" });
const loading = ref(false);
const error = ref("");
const notice = ref("");
const resultOpen = ref(false);
const historyExpanded = ref(false);
const flowOpen = ref(false);
const flowRef = ref<HTMLElement | null>(null);
const flowTriggerRef = ref<HTMLElement | null>(null);
let suppressFlowFocus = false;
const previewRecordId = ref<string | null>(null);
const pinnedRecordId = ref<string | null>(null);
const blockedHoverId = ref<string | null>(null);
const visibleHistory = computed(() => historyExpanded.value ? triageHistory.value : triageHistory.value.slice(0, 5));
const canSubmit = computed(() => form.symptoms.trim().length >= 6 && form.duration.trim().length > 0);
const severityLabels: Record<string, string> = { LOW: "轻度", MEDIUM: "中度", HIGH: "重度或明显加重" };
const severityOptions = Object.entries(severityLabels).map(([value, label]) => ({ value, label }));
function triageStatusText(status: unknown) {
  return patientStatusText(status);
}
function triageTagSeverity(status: unknown) {
  if (status === "ASSIGNED") return "info";
  if (status === "MANUAL_REQUIRED" || status === "PENDING") return "warn";
  return "success";
}
function recordId(item: { triageRecordId?: unknown }) { return String(item.triageRecordId); }
function isHistoryOpen(item: { triageRecordId?: unknown }) {
  const id = recordId(item);
  return pinnedRecordId.value === id || (previewRecordId.value === id && blockedHoverId.value !== id);
}
function toggleHistory(item: { triageRecordId?: unknown }) {
  const id = recordId(item);
  if (pinnedRecordId.value === id) {
    pinnedRecordId.value = null;
    blockedHoverId.value = id;
  } else {
    pinnedRecordId.value = id;
    blockedHoverId.value = null;
  }
}
function leaveHistory() { previewRecordId.value = null; blockedHoverId.value = null; }
function onFlowOutside(event: PointerEvent) {
  if (flowRef.value && !flowRef.value.contains(event.target as Node)) flowOpen.value = false;
}
function onFlowFocusOut(event: FocusEvent) {
  if (!flowRef.value?.contains(event.relatedTarget as Node | null)) flowOpen.value = false;
}
function onFlowFocus() { if (!suppressFlowFocus) flowOpen.value = true; }
function onFlowEscape() {
  suppressFlowFocus = true;
  flowOpen.value = false;
  flowTriggerRef.value?.focus();
  queueMicrotask(() => { suppressFlowFocus = false; });
}
onMounted(() => document.addEventListener("pointerdown", onFlowOutside));
onBeforeUnmount(() => document.removeEventListener("pointerdown", onFlowOutside));

function complaint() {
  return [
    `症状：${form.symptoms.trim()}`,
    `持续时间：${form.duration.trim()}`,
    `严重程度：${severityLabels[form.severity] ?? form.severity}`,
    form.extra.trim() ? `补充说明：${form.extra.trim()}` : "",
  ].filter(Boolean).join("；");
}

async function submit() {
  if (!canSubmit.value) {
    error.value = "请至少填写症状和持续时间。";
    return;
  }
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    const result = await api.triage(auth.token(), { chiefComplaint: complaint() });
    await workflow.refreshAuthenticated(auth.token());
    // 以本次推演返回为准：refresh 会用历史列表头回填 store，异步同步未完成时会拿到旧记录
    triage.value = result;
    notice.value = "分诊已提交，请根据推荐科室继续选择号源。";
    resultOpen.value = true;
  } catch (err) {
    error.value = formatApiError(err, "分诊提交失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="triage-pilot">
    <header class="triage-pilot-topline">
      <div class="triage-pilot-page-title">
        <span class="triage-pilot-title-mark" aria-hidden="true"><span></span><span></span></span>
        <h1>症状分诊</h1>
      </div>
      <div ref="flowRef" class="triage-pilot-flow-control" @pointerenter="flowOpen = true" @pointerleave="flowOpen = false"
           @focusout="onFlowFocusOut" @keydown.esc="onFlowEscape">
        <button ref="flowTriggerRef" type="button" class="triage-pilot-flow-trigger" aria-controls="triage-flow-menu" :aria-expanded="flowOpen"
                @focus="onFlowFocus" @click="flowOpen = true">就诊流程 <span aria-hidden="true">⌄</span></button>
        <Transition name="patient-nav-pop">
          <div v-if="flowOpen" id="triage-flow-menu" class="triage-pilot-flow-menu">
            <a href="#triage-symptoms" @click="flowOpen = false"><span>01</span> 描述症状 <b aria-hidden="true">↗</b></a>
            <a href="#triage-latest-title" @click="flowOpen = false"><span>02</span> 查看建议 <b aria-hidden="true">↗</b></a>
            <RouterLink to="/doctors" @click="flowOpen = false"><span>03</span> 选择号源 <b aria-hidden="true">↗</b></RouterLink>
          </div>
        </Transition>
      </div>
    </header>

    <div class="triage-pilot-grid">
      <form class="triage-pilot-form" @submit.prevent="submit">
        <div class="triage-pilot-section-heading"><span class="triage-pilot-section-index" aria-hidden="true">01 / 输入</span><h2>描述本次症状</h2></div>
        <Message v-if="error" severity="error" role="alert">{{ error }}</Message>
        <Message v-if="notice" severity="success" role="status">{{ notice }}</Message>

        <div class="triage-pilot-symptom-field">
          <label for="triage-symptoms">主要症状</label>
          <div class="triage-pilot-input-shell">
            <Textarea id="triage-symptoms" v-model.trim="form.symptoms" rows="5" placeholder="例如：左膝疼痛，走路时加重" />
            <div class="triage-pilot-input-tools">
              <span :class="{ 'is-ready': form.symptoms.trim().length >= 6 }">{{ form.symptoms.trim().length >= 6 ? '症状已填写' : '至少输入 6 个字' }}</span>
              <Button v-if="speechSupported" type="button" class="triage-pilot-voice" text :aria-pressed="speechListening"
                      :title="speechListening ? '正在听，说完自动停止' : '语音说症状'"
                      :label="speechListening ? '停止语音输入' : '使用语音输入'" @click="dictateSymptoms" />
            </div>
          </div>
        </div>

        <div class="triage-pilot-fields">
          <FormField label="持续时间"><InputText v-model.trim="form.duration" aria-label="持续时间" placeholder="例如：2 天" /></FormField>
          <FormField label="严重程度">
            <SelectButton v-model="form.severity" aria-label="严重程度" :options="severityOptions" option-label="label" option-value="value" :allow-empty="false" />
          </FormField>
        </div>
        <FormField label="补充说明（选填）"><Textarea v-model.trim="form.extra" aria-label="补充说明" rows="3" placeholder="症状变化、诱因或其他需要说明的情况" /></FormField>
        <div class="triage-pilot-submit-row">
          <Button type="submit" :loading="loading" :disabled="!canSubmit" label="提交分诊" class="triage-pilot-submit" />
        </div>
      </form>

      <aside class="triage-pilot-aside">
        <section class="triage-pilot-result" aria-labelledby="triage-latest-title">
          <div class="triage-pilot-section-heading"><span class="triage-pilot-section-index" aria-hidden="true">02 / 建议</span><h2 id="triage-latest-title">最新结果</h2></div>
          <div v-if="loading" class="triage-pilot-loading" role="status" aria-live="polite">
            <span>正在分析症状</span><Skeleton width="62%" height="2.6rem" /><Skeleton width="100%" height="1rem" /><Skeleton width="76%" height="1rem" />
          </div>
          <div v-else-if="triage" class="triage-pilot-result-content">
            <Tag :value="triageStatusText(triage.status)" :severity="triageTagSeverity(triage.status)" />
            <strong>{{ fieldText(triage, "recommendedDepartment", "待确认") }}</strong>
            <p>{{ fieldText(triage, "reason", "暂无说明") }}</p>
            <div class="triage-pilot-safety">科室建议仅用于就诊引导，最终诊断由接诊医生作出。</div>
            <RouterLink class="triage-pilot-next" to="/doctors">查看可预约号源 <span aria-hidden="true">→</span></RouterLink>
          </div>
          <div v-else class="triage-pilot-empty">暂无分诊结果。提交症状后可在这里查看建议。</div>
        </section>

        <section class="triage-pilot-history" aria-labelledby="triage-history-title">
          <div class="triage-pilot-section-heading"><div><span class="triage-pilot-section-index" aria-hidden="true">03 / 记录</span><h2 id="triage-history-title">历史分诊</h2></div><span v-if="triageHistory.length" class="triage-pilot-history-count">{{ triageHistory.length }}</span></div>
          <div v-if="triageHistory.length" class="triage-pilot-history-list">
            <article v-for="item in visibleHistory" :key="recordId(item)" class="triage-pilot-history-item" :class="{ expanded: isHistoryOpen(item) }"
                     @pointerenter="previewRecordId = recordId(item)" @pointerleave="leaveHistory" @focusin="previewRecordId = recordId(item)" @focusout="leaveHistory">
              <button type="button" class="triage-pilot-history-summary" :aria-expanded="isHistoryOpen(item)" @click="toggleHistory(item)">
                <strong>{{ fieldText(item, "recommendedDepartment", "待确认") }}</strong>
                <Tag :value="triageStatusText(item.status)" :severity="triageTagSeverity(item.status)" />
                <span class="triage-pilot-history-chevron" aria-hidden="true">⌄</span>
              </button>
              <div class="triage-pilot-history-detail" :aria-hidden="!isHistoryOpen(item)"><div><span>症状描述</span><p>{{ fieldText(item, "chiefComplaint") }}</p><span>建议依据</span><p>{{ fieldText(item, "reason", "暂无说明") }}</p></div></div>
            </article>
          </div>
          <Button v-if="triageHistory.length > 5" type="button" class="triage-pilot-history-toggle" text :aria-expanded="historyExpanded"
                  :label="historyExpanded ? '收起历史记录' : `查看全部 ${triageHistory.length} 条记录`" @click="historyExpanded = !historyExpanded" />
          <div v-if="!triageHistory.length" class="triage-pilot-empty">暂无历史分诊</div>
        </section>
      </aside>
    </div>

    <TriageResultModal :open="resultOpen" :result="triage" @close="resultOpen = false" @doctors="router.push('/doctors')" />
  </section>
</template>
