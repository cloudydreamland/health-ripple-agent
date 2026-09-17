<script setup lang="ts">
export interface LogEvent {
  time: string;
  code: string;
  color: string;
  text: string;
}

defineProps<{ events: LogEvent[] }>();
</script>

<template>
  <div class="activity-log">
    <div v-for="(e, i) in events" :key="e.time + i" class="log-row">
      <span class="time">{{ e.time }}</span>
      <span class="code" :style="{ color: e.color }">{{ e.code }}</span>
      <span class="text">{{ e.text }}</span>
    </div>
    <p v-if="!events.length" class="hint">等待推演…</p>
  </div>
</template>

<style scoped>
.activity-log {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 520px;
  overflow-y: auto;
  font-family: var(--font-mono);
}
.log-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 5.5px 2px;
  border-bottom: 1px dashed var(--line);
  font-size: 11.5px;
  line-height: 1.5;
}
.log-row:last-child { border-bottom: none; }
.time { color: var(--faint); flex: none; letter-spacing: 0.5px; }
.code { flex: none; width: 74px; font-weight: 700; letter-spacing: 0.8px; font-size: 10.5px; }
.text { color: var(--ink-soft); font-family: var(--font-sans); min-width: 0; }
</style>
