<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, formatApiError, useAuthStore } from "@smart-cloud-brain/shared-api";
import { ErrorState, FormField } from "@smart-cloud-brain/shared-ui";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const form = reactive({ account: "", password: "" });
const loading = ref(false);
const error = ref("");

async function submit() {
  if (!form.account.trim() || !form.password.trim()) {
    error.value = "请输入医生账号和密码。";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const session = await api.loginDoctor(form.account.trim(), form.password);
    auth.save("doctor-session", session, "DOCTOR");
    if (!auth.permissionError) await router.push(String(route.query.redirect || "/"));
  } catch (err) {
    error.value = formatApiError(err, "医生登录失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="doctor-login">
    <div class="login-art" aria-hidden="true"><div class="login-brand"><span>✚</span> DuMate <small>DOCTOR STATION</small></div><div class="login-art-center"><span>CLINICAL WORKSPACE / 01</span><div class="login-orbit"><i /><i /><i /><b>✚</b></div><strong>让每一次接诊<br />都有清晰脉络</strong></div><div class="login-art-foot"><span>CARE · REVIEW · DECIDE</span><span>01 — 03</span></div></div>
    <form class="login-form" @submit.prevent="submit">
      <div class="login-form-inner">
        <span class="page-index">安全访问 <i /> DOCTOR LOGIN</span>
        <h1>进入医生工作台</h1>
        <p class="login-instruction">使用医生账号登录，继续接诊与处方审核。</p>
        <ErrorState v-if="error" :message="error" />
        <div class="login-fields"><FormField label="医生账号"><input v-model.trim="form.account" autocomplete="username" placeholder="请输入账号" /></FormField><FormField label="密码"><input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" /></FormField></div>
        <button class="primary login-submit" type="submit" :disabled="loading">{{ loading ? "正在验证…" : "进入工作台 →" }}</button>
        <span class="login-form-foot">DuMate 医生端 · 仅限授权账号</span>
      </div>
    </form>
  </main>
</template>
