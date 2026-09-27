<script setup lang="ts">
defineProps<{ eyebrow: string; title: string; index: string; description?: string }>();
</script>

<template>
  <header class="doctor-page-header">
    <div class="page-heading-copy">
      <span class="page-index">{{ index }} <i /> {{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-heading-actions"><slot name="actions" /></div>
  </header>
</template>
