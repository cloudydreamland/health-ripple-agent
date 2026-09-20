<script setup lang="ts">
/**
 * 患者概览卡（左列，参照 VitaSphere Patient Overview）：
 * 印章 + PID + 诊断大字 + 用药/既往史 chips + 数据来源说明。
 */
const props = defineProps<{
  patientId: number;
  healthEvent: { diagnosis: string; drugs: string[]; pastHistory: string } | null;
  mode: string;
}>();

const emit = defineEmits<{ run: [] }>();
</script>

<template>
  <div class="pov">
    <div class="pov-head">
      <span class="pov-seal">守</span>
      <div class="pov-id">
        <b>患者 #{{ patientId }}</b>
        <span class="mono">PATIENT OVERVIEW</span>
      </div>
      <span class="tag" :class="mode === 'live' ? 'GREEN' : 'ORANGE'">{{ mode === "live" ? "● 实时" : "● 快照" }}</span>
    </div>

    <div class="pov-dx">
      <span class="label mono">当前诊断 · DIAGNOSIS</span>
      <strong>{{ healthEvent?.diagnosis || "—" }}</strong>
    </div>

    <div v-if="healthEvent?.drugs?.length" class="pov-chips">
      <span class="label mono">用药 · MEDICATION</span>
      <div class="chips">
        <span v-for="d in healthEvent.drugs" :key="d" class="chip chip-rx">{{ d }}</span>
      </div>
    </div>
    <div v-if="healthEvent?.pastHistory" class="pov-chips">
      <span class="label mono">既往史 · HISTORY</span>
      <div class="chips">
        <span v-for="h in healthEvent.pastHistory.split(/[,，]/).filter(Boolean)" :key="h" class="chip chip-his">{{ h }}</span>
      </div>
    </div>

    <button class="pov-cta" @click="emit('run')">⟲ 重新推演本患者</button>
  </div>
</template>

<style scoped>
.pov { display: flex; flex-direction: column; gap: 13px; }
.pov-head { display: flex; align-items: center; gap: 10px; }
.pov-seal {
  width: 38px; height: 38px; flex: none;
  display: grid; place-items: center;
  background: var(--red); color: #0b1524;
  border-radius: 10px; transform: rotate(-3deg);
  font-family: var(--font-serif); font-size: 20px; font-weight: 800;
  box-shadow: 0 0 18px rgba(244, 105, 92, 0.35);
}
.pov-id { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.pov-id b { font-size: 15px; letter-spacing: 0.5px; }
.pov-id span { font-size: 8.5px; color: var(--muted); letter-spacing: 1.6px; }

.pov-dx {
  border: 1px solid var(--line-strong);
  border-radius: 12px;
  padding: 12px 14px;
  background: linear-gradient(160deg, rgba(56, 191, 248, 0.08), transparent 55%);
}
.label { font-size: 8.5px; letter-spacing: 1.8px; color: var(--muted); display: block; margin-bottom: 6px; }
.pov-dx strong { font-family: var(--font-serif); font-size: 21px; letter-spacing: 1px; }

.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  font-family: var(--font-mono); font-size: 11px;
  border-radius: 999px; padding: 3px 11px;
  border: 1px solid var(--line-strong);
}
.chip-rx { color: #38cfe8; border-color: rgba(56, 207, 232, 0.4); background: rgba(56, 207, 232, 0.08); }
.chip-his { color: var(--ink-soft); }

.pov-cta {
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-soft);
  border-radius: 10px;
  padding: 9px 0;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}
.pov-cta:hover { border-color: #38bdf8; color: #38bdf8; }
</style>
