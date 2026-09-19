<script setup lang="ts">
/**
 * FIG.06 今日守护队列 —— 基层医生早上打开大屏的第一个答案："今天该先管谁"。
 * 跨患者按优先级排序：升级就医 > 未缓解加压 > 今日到期触达 > 气象指数。
 * 仅医生角色可取（患者越权 403），快照模式如实标注需实时连接。
 */
export interface QueueRow {
  patientId: number;
  priorityScore: number;
  weather: string;
  weatherLabel: string;
  guardIndex: number;
  dueTodayCount: number;
  escalatedRecent: number;
  unresolvedRecent: number;
  reason: string;
  latestDiagnosis: string;
}

const WEATHER_COLOR: Record<string, string> = {
  SUNNY: "#3ee6a4",
  CLOUDY: "#ffd94a",
  RAIN: "#ffab4a",
  STORM: "#ff5d6c",
};

defineProps<{ rows: QueueRow[] }>();
</script>

<template>
  <div class="queue">
    <template v-if="rows.length">
      <div v-for="(row, i) in rows" :key="row.patientId" class="q-row">
        <span class="q-rank" :class="{ top: i === 0 }">{{ i + 1 }}</span>
        <span class="q-dot" :style="{ background: WEATHER_COLOR[row.weather] ?? '#958d74' }" />
        <div class="q-main">
          <b>患者 #{{ row.patientId }}</b>
          <span v-if="row.latestDiagnosis" class="q-dx">{{ row.latestDiagnosis }}</span>
        </div>
        <div class="q-reason">{{ row.reason }}</div>
        <span class="q-score mono">{{ row.priorityScore }}</span>
      </div>
    </template>
    <p v-else class="q-empty">守护队列需实时连接后端（跨患者聚合，仅医生角色可读）。</p>
  </div>
</template>

<style scoped>
.queue { display: flex; flex-direction: column; gap: 7px; }
.q-row {
  display: flex; align-items: center; gap: 9px;
  border: 1px solid var(--line); border-radius: 8px;
  background: var(--card-inset); padding: 8px 10px;
}
.q-rank {
  width: 22px; height: 22px; flex: none;
  display: grid; place-items: center;
  border: 1.2px solid var(--line-strong); border-radius: 6px;
  font-family: var(--font-serif); font-size: 12px; color: var(--muted);
}
.q-rank.top { border-color: var(--red); color: var(--red); }
.q-dot { width: 9px; height: 9px; border-radius: 50%; flex: none; }
.q-main { display: flex; flex-direction: column; min-width: 92px; }
.q-main b { font-size: 12px; }
.q-dx { font-size: 9.5px; color: var(--faint); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 120px; }
.q-reason { flex: 1; font-size: 11px; color: var(--muted); min-width: 0; }
.q-score { font-size: 13px; color: var(--red); font-weight: 700; }
.q-empty { margin: 0; font-size: 11px; color: var(--faint); }
</style>
