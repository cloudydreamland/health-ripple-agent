<script setup lang="ts">
defineProps<{ name: "slots" | "appointments" | "records" | "prescriptions" | "triage" | "refresh" }>();
</script>

<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <template v-if="name === 'slots'"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 10h18m-13 5h8m-4-4v8"/></template>
    <template v-else-if="name === 'appointments'"><path d="M4 4h16v16H4zM8 4v3m8-3v3M4 10h16m-11 5 2 2 4-4"/></template>
    <template v-else-if="name === 'records'"><path d="M7 3h8l4 4v14H5V3h2Zm8 0v5h4M9 12h6m-6 4h6"/></template>
    <template v-else-if="name === 'prescriptions'"><path d="M5 4h14v16H5zM9 4V2h6v2M8 11h8m-4-4v8m-3 3h6"/></template>
    <template v-else-if="name === 'triage'"><path d="M12 3 3 7v5c0 5 3.4 8 9 9 5.6-1 9-4 9-9V7l-9-4Z"/><path d="M8 12h8m-4-4v8"/></template>
    <template v-else><path d="M20 7v5h-5M4 17v-5h5"/><path d="M5.5 9a7 7 0 0 1 12-2L20 12M4 12l2.5 5a7 7 0 0 0 12-2"/></template>
  </svg>
</template>
