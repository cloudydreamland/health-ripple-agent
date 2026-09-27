<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
  <header class="doctor-page-header">
    <div class="page-heading-copy">
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-heading-actions"><slot name="actions" /></div>
  </header>
</template>
