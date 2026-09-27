<script setup lang="ts">
import { computed } from "vue";
import { statusClass } from "@smart-cloud-brain/shared-api";
import { doctorStatusText } from "../doctorStatus";

const props = defineProps<{ status: unknown; tone?: string }>();
const label = computed(() => doctorStatusText(props.status));
const tone = computed(() => props.tone || (String(props.status).toUpperCase() === "CHECKED_IN" ? "info" : statusClass(props.status)));
const code = computed(() => String(props.status ?? "").toUpperCase());
const symbol = computed(() => ({ CHECKED_IN: "↳", CONFIRMED: "✓", COMPLETED: "✓", CANCELLED: "×", CREATED: "+", HIGH: "!", MEDIUM: "!", LOW: "✓" }[code.value] ?? "·"));
</script>

<template>
  <span class="tag doctor-status" :class="[tone, `status-${code.toLowerCase().replace(/[^a-z0-9_-]/g, '')}`]"><span class="status-symbol" aria-hidden="true">{{ symbol }}</span>{{ label }}</span>
</template>
