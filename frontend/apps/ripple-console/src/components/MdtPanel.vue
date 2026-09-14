<script setup lang="ts">
import { computed } from "vue";
import type { MdtResponse } from "../types";

const props = defineProps<{ mdt: MdtResponse | null }>();

const AGENT_META: Record<string, { label: string; icon: string; color: string }> = {
  triageView: { label: "分诊 Agent", icon: "🧭", color: "#3fd8f2" },
  prescriptionView: { label: "处方 Agent", icon: "💊", color: "#ff5d6c" },
  recordView: { label: "病历 Agent", icon: "📄", color: "#4f8fe6" },
  followupView: { label: "随访 Agent", icon: "📅", color: "#3ee6a4" },
  rippleView: { label: "涟漪守护 Agent", icon: "🌊", color: "#9d7bff" },
};

const views = computed(() => {
  if (!props.mdt?.consultation) return [];
  return Object.entries(props.mdt.consultation).map(([key, value]) => ({
    key,
    meta: AGENT_META[key] ?? { label: key, icon: "🤖", color: "#8aa3c4" },
    value,
  }));
});
</script>

<template>
  <div v-if="mdt" class="mdt">
    <div class="agents">
      <div
        v-for="view in views"
        :key="view.key"
        class="agent-card"
        :style="{ borderColor: view.meta.color + '44' }"
      >
        <div class="agent-head">
          <span class="agent-icon">{{ view.meta.icon }}</span>
          <b :style="{ color: view.meta.color }">{{ view.meta.label }}</b>
        </div>
        <div class="agent-body">
          <p v-for="(value, key) in view.value" :key="key" class="agent-line">
            <span class="k">{{ key }}：</span>{{ value }}
          </p>
        </div>
      </div>
    </div>

    <div v-if="mdt.consensusNotes?.length" class="consensus">
      <h4>会诊共识纪要</h4>
      <p v-for="(note, i) in mdt.consensusNotes" :key="i">▸ {{ note }}</p>
    </div>

    <div class="safety">
      <span class="tag GREEN">safetyBoundary</span>
      <span class="hint">advisoryOnly=true · 终审权在医生（DOCTOR） · 智能体不做处方/诊断终审</span>
    </div>
  </div>
  <p v-else class="hint">点击「发起MDT会诊」查看五Agent学科化会诊。</p>
</template>

<style scoped>
.agents { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 8px; }
.agent-card { border: 1px solid var(--line); border-radius: 10px; background: var(--bg-inset); padding: 9px 11px; }
.agent-head { display: flex; align-items: center; gap: 7px; margin-bottom: 6px; font-size: 12.5px; }
.agent-icon { font-size: 14px; }
.agent-line { margin: 3px 0; font-size: 11.5px; color: var(--text-dim); line-height: 1.5; }
.agent-line .k { color: var(--text-faint); }
.consensus { margin-top: 12px; border-top: 1px dashed var(--line); padding-top: 10px; }
.consensus h4 { margin: 0 0 6px; font-size: 12.5px; color: var(--text-dim); }
.consensus p { margin: 4px 0; font-size: 12.5px; line-height: 1.55; }
.safety { margin-top: 12px; display: flex; align-items: center; gap: 10px; }
</style>
