<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { fieldText, useAuthStore, usePatientWorkflowStore } from "@smart-cloud-brain/shared-api";
import { EmptyState } from "@smart-cloud-brain/shared-ui";

const auth = useAuthStore();
const workflow = usePatientWorkflowStore();
const { patient } = storeToRefs(workflow);
const displayName = computed(() => fieldText(patient.value, "name", auth.session?.name || "患者"));
</script>

<template>
  <section class="panel patient-service-page patient-profile-page">
    <header class="panel-header"><div class="panel-title"><h2>个人资料</h2></div><span class="patient-profile-readonly">仅供查看</span></header>
    <div class="panel-body">
      <template v-if="patient">
        <div class="patient-profile-intro">
          <span class="patient-profile-avatar" aria-hidden="true">{{ displayName.slice(0, 1) }}</span>
          <div><span>就诊人</span><h3>{{ displayName }}</h3></div>
        </div>
        <div class="patient-profile-content">
          <section class="patient-profile-section" aria-labelledby="profile-basic-title">
            <h3 id="profile-basic-title">基本信息</h3>
            <dl class="patient-profile-basic">
              <div><dt>手机号</dt><dd>{{ fieldText(patient, "phone", "未记录") }}</dd></div>
              <div><dt>年龄</dt><dd>{{ fieldText(patient, "age", "未记录") }}</dd></div>
            </dl>
          </section>
          <section class="patient-profile-section" aria-labelledby="profile-health-title">
            <h3 id="profile-health-title">健康资料</h3>
            <dl class="patient-profile-health">
              <div><dt>过敏史</dt><dd>{{ fieldText(patient, "allergyHistory", "未记录") }}</dd></div>
              <div><dt>既往史</dt><dd>{{ fieldText(patient, "pastHistory", "未记录") }}</dd></div>
            </dl>
          </section>
        </div>
        <p class="patient-profile-note">当前资料仅支持查看，暂不支持在线修改。</p>
      </template>
      <EmptyState v-else title="暂无患者资料" message="请刷新或重新登录。" />
    </div>
  </section>
</template>
