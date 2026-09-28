<script setup lang="ts">
import { reactive, ref } from "vue";
import { api, fieldText, formatApiError, useAuthStore, type DataRow } from "@smart-cloud-brain/shared-api";
import { FormField } from "@smart-cloud-brain/shared-ui";

const auth = useAuthStore();
const loading = ref(false);
const searched = ref(false);
const error = ref("");
const form = reactive({ q: "", departmentCode: "" });
const results = reactive({ knowledge: [] as DataRow[], drugs: [] as DataRow[], prompts: [] as DataRow[] });
const resultErrors = reactive({ knowledge: "", drugs: "", prompts: "" });
const groups = [
  { key: "knowledge" as const, title: "知识库", kicker: "KNOWLEDGE / 01", primary: "title", secondary: "departmentCode", detail: "advice" },
  { key: "drugs" as const, title: "药品", kicker: "DRUG / 02", primary: "name", secondary: "specification", detail: "contraindication" },
  { key: "prompts" as const, title: "提示词", kicker: "PROMPT / 03", primary: "templateName", secondary: "taskType", detail: "version" },
];
async function search() {
  if (!form.q.trim()) {
    error.value = "请输入检索关键词。";
    searched.value = false;
    results.knowledge = []; results.drugs = []; results.prompts = [];
    return;
  }
  loading.value = true;
  searched.value = true;
  error.value = "";
  results.knowledge = []; results.drugs = []; results.prompts = [];
  resultErrors.knowledge = ""; resultErrors.drugs = ""; resultErrors.prompts = "";
  const [knowledge, drugs, prompts] = await Promise.allSettled([
    api.searchKnowledge(auth.token(), form.q.trim(), form.departmentCode.trim()),
    api.searchDrugs(auth.token(), form.q.trim()),
    api.searchPrompts(auth.token(), form.q.trim()),
  ]);
  if (knowledge.status === "fulfilled") results.knowledge = knowledge.value;
  else resultErrors.knowledge = formatApiError(knowledge.reason, "知识库检索失败");
  if (drugs.status === "fulfilled") results.drugs = drugs.value;
  else resultErrors.drugs = formatApiError(drugs.reason, "药品检索失败");
  if (prompts.status === "fulfilled") results.prompts = prompts.value;
  else resultErrors.prompts = formatApiError(prompts.reason, "提示词检索失败");
  loading.value = false;
}
</script>

<template>
  <section class="admin-page search-page">
    <header class="admin-page-heading"><div><span class="admin-page-kicker">SEARCH / CATALOG</span><h1>综合检索</h1></div></header>
    <form class="admin-search-form" @submit.prevent="search">
      <FormField label="关键词"><input v-model.trim="form.q" type="search" placeholder="知识、药品或提示词" /></FormField>
      <FormField label="科室编码"><input v-model.trim="form.departmentCode" placeholder="仅用于知识库检索" /></FormField>
      <button type="submit" class="primary" :disabled="loading">{{ loading ? "检索中…" : "开始检索" }}</button>
    </form>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div>
    <div v-if="!searched" class="admin-search-idle">输入关键词后查看三个目录的检索结果。</div>
    <div v-else class="admin-search-results">
      <section v-for="group in groups" :key="group.key" class="admin-list-panel" :aria-label="group.title + '检索结果'">
        <div class="admin-list-heading"><div><span class="admin-page-kicker">{{ group.kicker }}</span><h2>{{ group.title }}</h2></div><span class="admin-list-count">{{ resultErrors[group.key] ? "未更新" : `${results[group.key].length} 条` }}</span></div>
        <div v-if="loading" class="admin-list-state">正在检索…</div>
        <div v-else-if="resultErrors[group.key]" class="admin-list-state error" role="status">{{ resultErrors[group.key] }}</div>
        <div v-else-if="!results[group.key].length" class="admin-list-state">没有匹配的{{ group.title }}结果。</div>
        <div v-else class="admin-search-result-list">
          <article v-for="item in results[group.key]" :key="String(item.id)" class="admin-search-result-row">
            <strong>{{ fieldText(item, group.primary) }}</strong>
            <span>{{ fieldText(item, group.secondary, "") }}</span>
            <p v-if="fieldText(item, group.detail, '')">{{ fieldText(item, group.detail) }}</p>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>
