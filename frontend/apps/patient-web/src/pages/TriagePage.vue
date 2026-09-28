<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { api, fieldText, formatApiError, useAuthStore, usePatientWorkflowStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { FormField } from "@smart-cloud-brain/shared-ui";
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import SelectButton from "primevue/selectbutton";
import Textarea from "primevue/textarea";
import Tag from "primevue/tag";
import Message from "primevue/message";
import Skeleton from "primevue/skeleton";
import Drawer from "primevue/drawer";
import TriageResultModal from "../components/TriageResultModal.vue";
import { patientStatusText } from "../format";
import { useSpeechRecognition } from "../composables/useSpeech";

const { supported: speechSupported, listening: speechListening, error: speechError, start: speechStart, stop: speechStop } = useSpeechRecognition();
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
const extraOpen = ref(false);
const historyDrawerOpen = ref(false);
const historyQuery = ref("");
const historyPageSize = ref(20);
const selectedHistoryId = ref<string | null>(null);
const visibleHistory = computed(() => triageHistory.value.slice(0, 3));
const filteredHistory = computed(() => {
  const query = historyQuery.value.trim().toLocaleLowerCase();
  if (!query) return triageHistory.value;
  return triageHistory.value.filter((item) => [
    fieldText(item, "recommendedDepartment", ""),
    triageStatusText(item.status),
    fieldText(item, "chiefComplaint", ""),
    fieldText(item, "reason", ""),
  ].some((value) => value.toLocaleLowerCase().includes(query)));
});
const drawerHistory = computed(() => filteredHistory.value.slice(0, historyPageSize.value));
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
function openHistory(item?: DataRow) {
  historyQuery.value = "";
  historyPageSize.value = 20;
  selectedHistoryId.value = item ? recordId(item) : null;
  historyDrawerOpen.value = true;
}
function updateHistoryQuery(value: string | undefined) {
  historyQuery.value = value ?? "";
  historyPageSize.value = 20;
  selectedHistoryId.value = null;
}

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
    </header>

    <div class="triage-pilot-grid">
      <form class="triage-pilot-form" @submit.prevent="submit">
        <div class="triage-pilot-section-heading"><span class="triage-pilot-section-index" aria-hidden="true">01 / 输入</span><h2>描述本次症状</h2></div>
        <Message v-if="error" severity="error" role="alert">{{ error }}</Message>
        <Message v-if="notice" severity="success" role="status">{{ notice }}</Message>

        <div class="triage-pilot-symptom-field">
          <label for="triage-symptoms">主要症状</label>
          <div class="triage-pilot-input-shell">
            <Textarea id="triage-symptoms" v-model.trim="form.symptoms" rows="3" auto-resize placeholder="例如：左膝疼痛，走路时加重" />
            <div class="triage-pilot-input-tools">
              <span :class="{ 'is-ready': form.symptoms.trim().length >= 6 }">{{ form.symptoms.trim().length >= 6 ? '症状已填写' : '至少输入 6 个字' }}</span>
              <button v-if="speechSupported" type="button" class="triage-pilot-voice" :class="{ 'is-listening': speechListening }"
                      :aria-pressed="speechListening" :aria-label="speechListening ? '停止语音输入' : '使用语音输入'" @click="dictateSymptoms">
                <span class="triage-pilot-mic" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21m-4 0h8"/></svg></span>
                <span>{{ speechListening ? '正在聆听 · 点击停止' : '语音输入' }}</span>
              </button>
            </div>
          </div>
          <p v-if="speechError" class="triage-pilot-speech-error" role="alert">{{ speechError }}</p>
        </div>

        <div class="triage-pilot-fields">
          <FormField label="持续时间"><InputText v-model.trim="form.duration" aria-label="持续时间" placeholder="例如：2 天" /></FormField>
          <FormField label="严重程度">
            <SelectButton v-model="form.severity" aria-label="严重程度" :options="severityOptions" option-label="label" option-value="value" :allow-empty="false" />
          </FormField>
        </div>
        <div class="triage-pilot-optional" :class="{ 'is-open': extraOpen }">
          <button type="button" class="triage-pilot-optional-trigger" aria-controls="triage-extra-panel" :aria-expanded="extraOpen" @click="extraOpen = !extraOpen">
            <span class="triage-pilot-optional-copy"><strong>补充说明 <small>选填</small></strong><span>{{ form.extra.trim() ? '已填写 · 点击查看或修改' : '症状变化、诱因等' }}</span></span>
            <span class="triage-pilot-optional-indicator" aria-hidden="true">⌄</span>
          </button>
          <div id="triage-extra-panel" class="triage-pilot-optional-panel" :aria-hidden="!extraOpen" :inert="!extraOpen">
            <div class="triage-pilot-optional-panel-inner">
              <FormField label="补充说明"><Textarea v-model.trim="form.extra" aria-label="补充说明" rows="2" auto-resize placeholder="症状变化、诱因或其他需要说明的情况" /></FormField>
            </div>
          </div>
        </div>
        <div class="triage-pilot-submit-row">
          <button type="submit" :disabled="!canSubmit || loading" :aria-busy="loading" class="triage-pilot-submit">
            <span>{{ loading ? '正在生成建议' : '提交分诊' }}</span>
            <span v-if="loading" class="triage-pilot-submit-spinner" aria-hidden="true"></span>
            <span v-else class="triage-pilot-submit-arrow" aria-hidden="true">↗</span>
          </button>
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
            <article v-for="item in visibleHistory" :key="recordId(item)" class="triage-pilot-history-item">
              <button type="button" class="triage-pilot-history-summary" :aria-label="`查看${fieldText(item, 'recommendedDepartment', '待确认')}的分诊记录`" @click="openHistory(item)">
                <strong>{{ fieldText(item, "recommendedDepartment", "待确认") }}</strong>
                <Tag :value="triageStatusText(item.status)" :severity="triageTagSeverity(item.status)" />
                <span class="triage-pilot-history-arrow" aria-hidden="true">↗</span>
              </button>
            </article>
          </div>
          <Button v-if="triageHistory.length > 3" type="button" class="triage-pilot-history-toggle" text
                  :label="`查看全部 ${triageHistory.length} 条记录`" @click="openHistory()" />
          <div v-if="!triageHistory.length" class="triage-pilot-empty">暂无历史分诊</div>
        </section>
      </aside>
    </div>

    <TriageResultModal :open="resultOpen" :result="triage" @close="resultOpen = false" @doctors="router.push('/doctors')" />
    <Drawer v-model:visible="historyDrawerOpen" position="right" header="历史分诊" class="patient-triage-history-drawer" :style="{ width: 'min(100vw, 520px)' }">
      <div class="triage-drawer-intro">共 {{ triageHistory.length }} 条记录 · 最新在前</div>
      <div v-if="triageHistory.length > 8" class="triage-drawer-search">
        <label for="triage-history-search">搜索历史记录</label>
        <InputText id="triage-history-search" :model-value="historyQuery" placeholder="搜索科室、症状或建议" @update:model-value="updateHistoryQuery" />
      </div>
      <div v-if="!filteredHistory.length" class="triage-drawer-empty">没有找到匹配的记录</div>
      <div v-else class="triage-drawer-list">
        <article v-for="item in drawerHistory" :key="recordId(item)" class="triage-drawer-item" :class="{ 'is-selected': selectedHistoryId === recordId(item) }">
          <button type="button" class="triage-drawer-item-trigger" :aria-expanded="selectedHistoryId === recordId(item)" :aria-controls="`triage-drawer-detail-${recordId(item)}`"
                  @click="selectedHistoryId = selectedHistoryId === recordId(item) ? null : recordId(item)">
            <span><strong>{{ fieldText(item, "recommendedDepartment", "待确认") }}</strong><small>{{ triageStatusText(item.status) }}</small></span>
            <span class="triage-drawer-item-chevron" aria-hidden="true">⌄</span>
          </button>
          <div :id="`triage-drawer-detail-${recordId(item)}`" class="triage-drawer-detail" :aria-hidden="selectedHistoryId !== recordId(item)" :inert="selectedHistoryId !== recordId(item)">
            <div class="triage-drawer-detail-inner"><span>症状描述</span><p>{{ fieldText(item, "chiefComplaint") }}</p><span>建议依据</span><p>{{ fieldText(item, "reason", "暂无说明") }}</p></div>
          </div>
        </article>
      </div>
      <Button v-if="historyPageSize < filteredHistory.length" type="button" class="triage-drawer-more" text
              :label="`再显示 ${Math.min(20, filteredHistory.length - historyPageSize)} 条`" @click="historyPageSize += 20" />
    </Drawer>
  </section>
</template>
