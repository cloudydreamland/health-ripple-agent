<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { storeToRefs } from "pinia";
import {
  api, fieldText, formatApiError, statusClass, statusText, toNumber,
  useAdminWorkflowStore, useAuthStore, type DataRow,
} from "@smart-cloud-brain/shared-api";
import { DataTable, FormField, Modal, StatusTag } from "@smart-cloud-brain/shared-ui";

type Entity = "department" | "doctor" | "drug" | "knowledge" | "prompt" | "dict";
type FieldType = "text" | "password" | "number" | "textarea" | "checkbox";
type FieldConfig = [key: string, label: string, type: FieldType];
type Column = { key: string; label: string; kind?: "primary" | "status" | "code" | "long" };
type CatalogConfig = {
  title: string;
  singular: string;
  source: string;
  rows: () => DataRow[];
  keys: string[];
  columns: Column[];
  fields: FieldConfig[];
};

const props = defineProps<{ entity: Entity }>();
const emit = defineEmits<{ refresh: [] }>();
const auth = useAuthStore();
const workflow = useAdminWorkflowStore();
const { departments, doctors, drugs, knowledge, prompts, dicts, refreshErrors } = storeToRefs(workflow);
const keyword = ref("");
const statusFilter = ref("");
const error = ref("");
const loadError = ref("");
const notice = ref("");
const saving = ref(false);
const loading = ref(false);
const editorOpen = ref(false);
const form = reactive<Record<string, string | number | boolean | undefined>>({});

const configs: Record<Entity, CatalogConfig> = {
  department: {
    title: "科室维护", singular: "科室", source: "departments", rows: () => departments.value,
    keys: ["code", "name", "description"],
    columns: [{ key: "name", label: "科室", kind: "primary" }, { key: "code", label: "科室编码", kind: "code" }, { key: "description", label: "说明", kind: "long" }],
    fields: [["code", "科室编码", "text"], ["name", "科室名称", "text"], ["description", "说明", "textarea"]],
  },
  doctor: {
    title: "医生维护", singular: "医生", source: "doctors", rows: () => doctors.value,
    keys: ["name", "phone", "departmentName", "title", "specialty"],
    columns: [{ key: "name", label: "医生", kind: "primary" }, { key: "departmentName", label: "科室" }, { key: "title", label: "职称" }, { key: "specialty", label: "专长", kind: "long" }, { key: "status", label: "状态", kind: "status" }],
    fields: [["name", "姓名", "text"], ["phone", "手机号", "text"], ["password", "新密码", "password"], ["departmentId", "科室 ID", "number"], ["title", "职称", "text"], ["specialty", "专长", "textarea"], ["status", "状态", "text"]],
  },
  drug: {
    title: "药品维护", singular: "药品", source: "drugs", rows: () => drugs.value,
    keys: ["name", "specification", "contraindication", "status"],
    columns: [{ key: "name", label: "药品", kind: "primary" }, { key: "specification", label: "规格" }, { key: "contraindication", label: "禁忌", kind: "long" }, { key: "status", label: "状态", kind: "status" }],
    fields: [["name", "药品名称", "text"], ["specification", "规格", "text"], ["contraindication", "禁忌", "textarea"], ["interactionRule", "相互作用规则", "textarea"], ["status", "状态", "text"]],
  },
  knowledge: {
    title: "知识库维护", singular: "知识条目", source: "knowledge", rows: () => knowledge.value,
    keys: ["title", "symptoms", "riskSignals", "departmentCode"],
    columns: [{ key: "title", label: "条目", kind: "primary" }, { key: "departmentCode", label: "科室编码", kind: "code" }, { key: "riskSignals", label: "风险信号", kind: "long" }, { key: "status", label: "状态", kind: "status" }],
    fields: [["title", "标题", "text"], ["symptoms", "症状", "textarea"], ["riskSignals", "风险信号", "textarea"], ["advice", "建议", "textarea"], ["departmentCode", "科室编码", "text"], ["status", "状态", "text"]],
  },
  prompt: {
    title: "提示词维护", singular: "提示词", source: "prompts", rows: () => prompts.value,
    keys: ["taskType", "templateName", "departmentCode", "version"],
    columns: [{ key: "templateName", label: "模板", kind: "primary" }, { key: "taskType", label: "任务类型", kind: "code" }, { key: "departmentCode", label: "科室编码", kind: "code" }, { key: "version", label: "版本", kind: "code" }, { key: "enabled", label: "状态", kind: "status" }],
    fields: [["taskType", "任务类型", "text"], ["departmentCode", "科室编码", "text"], ["templateName", "模板名称", "text"], ["templateContent", "模板内容", "textarea"], ["outputSchema", "输出结构定义", "textarea"], ["version", "版本", "text"], ["enabled", "启用", "checkbox"]],
  },
  dict: {
    title: "字典维护", singular: "字典项", source: "dicts", rows: () => dicts.value,
    keys: ["dictType", "dictKey", "dictValue", "status"],
    columns: [{ key: "dictValue", label: "字典值", kind: "primary" }, { key: "dictType", label: "类型", kind: "code" }, { key: "dictKey", label: "键", kind: "code" }, { key: "sort", label: "排序" }, { key: "status", label: "状态", kind: "status" }],
    fields: [["dictType", "字典类型", "text"], ["dictKey", "字典键", "text"], ["dictValue", "字典值", "text"], ["sort", "排序", "number"], ["status", "状态", "text"]],
  },
};

const config = computed(() => configs[props.entity]);
const sourceRows = computed(() => config.value.rows());
const sourceError = computed(() => refreshErrors.value[config.value.source] || loadError.value);
function rawStatus(item: DataRow) {
  if (props.entity === "prompt") return item.enabled === true || item.enabled === 1 || item.enabled === "1" || item.enabled === "true" ? "ENABLED" : "DISABLED";
  return fieldText(item, "status", "");
}
const statusOptions = computed(() => [...new Set(sourceRows.value.map(rawStatus).filter(Boolean))]);
const rows = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  return sourceRows.value.filter((item) =>
    (!q || config.value.keys.some((key) => fieldText(item, key, "").toLowerCase().includes(q)))
    && (!statusFilter.value || rawStatus(item) === statusFilter.value),
  );
});
function secondary(item: DataRow) {
  if (props.entity === "doctor") return fieldText(item, "phone", "");
  if (props.entity === "department") return `#${fieldText(item, "id")}`;
  if (props.entity === "drug") return `#${fieldText(item, "id")}`;
  if (props.entity === "knowledge") return fieldText(item, "symptoms", "");
  if (props.entity === "prompt") return `#${fieldText(item, "id")}`;
  return `#${fieldText(item, "id")}`;
}
function shortText(value: string) { return value.length > 48 ? value.slice(0, 45) + "…" : value; }

async function refresh() {
  loading.value = true;
  loadError.value = "";
  try { await workflow.refresh(auth.token()); }
  catch (err) { loadError.value = formatApiError(err, "列表加载失败"); }
  finally { loading.value = false; }
}
function openEditor(item?: DataRow) {
  notice.value = "";
  error.value = "";
  Object.keys(form).forEach((key) => delete form[key]);
  if (item) {
    form.id = toNumber(item.id) || undefined;
    config.value.fields.forEach(([key]) => { form[key] = item[key] as string | number | boolean | undefined; });
  } else {
    config.value.fields.forEach(([key, , type]) => { form[key] = type === "checkbox" ? true : type === "number" ? 0 : ""; });
    if (props.entity === "doctor") form.departmentId = toNumber(departments.value[0]?.id);
    if ("status" in form) form.status = "ENABLED";
    if (props.entity === "prompt") {
      form.taskType = "MEDICAL_RECORD";
      form.outputSchema = '{"type":"object"}';
      form.version = "v1";
    }
  }
  editorOpen.value = true;
}
function setField(key: string, type: FieldType, event: Event) {
  const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
  form[key] = type === "number" ? Number(value) : value;
}
function setCheckbox(key: string, event: Event) { form[key] = (event.target as HTMLInputElement).checked; }
function requireFields() {
  const required: Record<Entity, string[]> = {
    department: ["code", "name"], doctor: ["name", "phone", "departmentId"], drug: ["name"],
    knowledge: ["title", "symptoms", "advice"], prompt: ["taskType", "templateName", "templateContent"], dict: ["dictType", "dictKey", "dictValue"],
  };
  const missing = required[props.entity].find((key) => form[key] === undefined || form[key] === "" || form[key] === 0);
  return missing ? `请填写 ${config.value.fields.find(([key]) => key === missing)?.[1] || missing}` : "";
}
async function save() {
  const invalid = requireFields();
  if (invalid) { error.value = invalid; return; }
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    if (props.entity === "department") await api.saveDepartment(auth.token(), form as never);
    if (props.entity === "doctor") await api.saveDoctor(auth.token(), form as never);
    if (props.entity === "drug") await api.saveDrug(auth.token(), form as never);
    if (props.entity === "knowledge") await api.saveKnowledgeEntry(auth.token(), form as never);
    if (props.entity === "prompt") await api.savePrompt(auth.token(), form as never);
    if (props.entity === "dict") await api.saveDict(auth.token(), form as never);
    editorOpen.value = false;
    notice.value = `${config.value.singular}已保存。`;
    emit("refresh");
    await refresh();
  } catch (err) { error.value = formatApiError(err, "保存失败"); }
  finally { saving.value = false; }
}

refresh();
</script>

<template>
  <section class="admin-page catalog-page">
    <header class="admin-page-heading">
      <div><span class="admin-page-kicker">CATALOG / {{ entity.toUpperCase() }}</span><h1>{{ config.title }}</h1></div>
      <div class="admin-page-actions">
        <button type="button" :disabled="loading" @click="refresh">{{ loading ? "刷新中" : "刷新数据" }}</button>
        <button type="button" class="primary" @click="openEditor()">新增{{ config.singular }}</button>
      </div>
    </header>
    <div v-if="notice" class="notice success" role="status">{{ notice }}</div>
    <section class="admin-list-panel" :aria-label="config.title + '列表'">
      <div class="admin-list-toolbar">
        <label class="admin-search-field"><span class="sr-only">搜索{{ config.singular }}</span><input v-model.trim="keyword" :placeholder="`搜索${config.singular}`" type="search" /></label>
        <select v-if="statusOptions.length" v-model="statusFilter" aria-label="筛选状态">
          <option value="">全部状态</option><option v-for="status in statusOptions" :key="status" :value="status">{{ statusText(status) }}</option>
        </select>
        <span class="admin-list-count">{{ sourceError ? "数据未更新" : `显示 ${rows.length} / ${sourceRows.length} 条` }}</span>
      </div>
      <DataTable :rows="rows" :loading="loading" :error="sourceError" :empty-title="sourceRows.length ? '没有匹配的记录' : '暂无数据'" :empty-message="sourceRows.length ? '调整搜索或筛选条件后重试。' : '当前目录暂无记录。'">
        <thead><tr><th v-for="column in config.columns" :key="column.key">{{ column.label }}</th><th class="actions-cell">操作</th></tr></thead>
        <tbody>
          <tr v-for="item in rows" :key="String(item.id)">
            <td v-for="column in config.columns" :key="column.key" :class="['admin-cell', 'admin-cell-' + (column.kind || 'text')]">
              <template v-if="column.kind === 'primary'">
                <strong>{{ fieldText(item, column.key) }}</strong>
                <small v-if="secondary(item)">{{ secondary(item) }}</small>
              </template>
              <StatusTag v-else-if="column.kind === 'status'" :status="statusText(column.key === 'enabled' ? rawStatus(item) : item[column.key])" :tone="statusClass(column.key === 'enabled' ? rawStatus(item) : item[column.key])" />
              <template v-else-if="column.kind === 'long'">
                <details v-if="fieldText(item, column.key, '').length > 48" class="admin-cell-details"><summary>{{ shortText(fieldText(item, column.key)) }}</summary><p>{{ fieldText(item, column.key) }}</p></details>
                <span v-else>{{ fieldText(item, column.key) }}</span>
              </template>
              <span v-else>{{ fieldText(item, column.key) }}</span>
            </td>
            <td class="admin-row-actions"><button type="button" :aria-label="`编辑${config.singular} ${fieldText(item, config.columns[0].key)}`" @click="openEditor(item)">编辑</button></td>
          </tr>
        </tbody>
      </DataTable>
    </section>
    <Modal :open="editorOpen" :title="`${form.id ? '编辑' : '新增'}${config.singular}`" @close="editorOpen = false">
      <div class="admin-editor-fields">
        <FormField v-for="[key, label, type] in config.fields" :key="key" :label="label">
          <textarea v-if="type === 'textarea'" :value="String(form[key] ?? '')" @input="setField(key, type, $event)" />
          <input v-else-if="type === 'checkbox'" type="checkbox" :checked="Boolean(form[key])" @change="setCheckbox(key, $event)" />
          <select v-else-if="key === 'departmentId' && departments.length" :value="form[key]" @change="setField(key, 'number', $event)">
            <option v-for="department in departments" :key="String(department.id)" :value="department.id">{{ department.name }}</option>
          </select>
          <select v-else-if="key === 'status'" :value="String(form[key] ?? 'ENABLED')" @change="setField(key, type, $event)">
            <option value="ENABLED">启用</option><option value="DISABLED">停用</option>
          </select>
          <input v-else :value="String(form[key] ?? '')" :type="type" @input="setField(key, type, $event)" />
        </FormField>
        <div v-if="error" class="notice error" role="alert">{{ error }}</div>
      </div>
      <template #footer>
        <button type="button" :disabled="saving" @click="editorOpen = false">取消</button>
        <button type="button" class="primary" :disabled="saving" @click="save">{{ saving ? "保存中…" : "保存" }}</button>
      </template>
    </Modal>
  </section>
</template>
