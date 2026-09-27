<script setup lang="ts">
import { computed } from "vue";
const props = defineProps<{ page: number; total: number; pageSize?: number }>();
const emit = defineEmits<{ "update:page": [value: number] }>();
const size = computed(() => props.pageSize || 20);
const pages = computed(() => Math.max(1, Math.ceil(props.total / size.value)));
</script>

<template>
  <footer v-if="total > size" class="doctor-pager">
    <span>显示 {{ (page - 1) * size + 1 }}–{{ Math.min(page * size, total) }} / {{ total }} 条</span>
    <div>
      <button type="button" :disabled="page <= 1" aria-label="上一页" @click="emit('update:page', page - 1)">‹</button>
      <span>{{ page }} / {{ pages }}</span>
      <button type="button" :disabled="page >= pages" aria-label="下一页" @click="emit('update:page', page + 1)">›</button>
    </div>
  </footer>
</template>
