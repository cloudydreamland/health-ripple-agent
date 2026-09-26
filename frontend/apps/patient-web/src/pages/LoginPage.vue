<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, api, formatApiError, useAuthStore } from "@smart-cloud-brain/shared-api";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const form = reactive({ account: "", password: "" });
const loading = ref(false);
const error = ref("");
const attempted = ref(false);
const showPassword = ref(false);

async function submit() {
  attempted.value = true;
  if (!form.account.trim() || !form.password.trim()) {
    error.value = "请输入账号和密码。";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const session = await api.loginPatient(form.account.trim(), form.password);
    auth.save("patient-session", session, "PATIENT");
    if (!auth.permissionError) {
      await router.push(String(route.query.redirect || "/"));
    }
  } catch (err) {
    error.value = err instanceof ApiError && (err.status === 401 || err.code === 401)
      ? "账号或密码不正确，请重试。"
      : formatApiError(err, "登录失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="mayo-container auth-grid patient-login-page">
    <div class="patient-login-stage">
      <div class="patient-login-visual">
        <div class="patient-login-visual-grid" aria-hidden="true"></div>
        <div class="patient-login-aura" aria-hidden="true"><span></span><span></span><span></span></div>
        <div class="patient-login-orbit" aria-hidden="true"><span class="orbit-one"></span><span class="orbit-two"></span><span class="orbit-three"></span><span class="orbit-core"></span><span class="orbit-satellite"></span></div>
        <div class="patient-login-visual-head"><span class="patient-login-mark" aria-hidden="true"><span></span></span><span>涟漪守护 <i></i> 患者服务</span></div>
        <div class="patient-login-visual-copy">
          <span class="patient-login-index">01 / 回到就诊进程</span>
          <h1>每一步<br />都有回应<span class="patient-login-title-dot">.</span></h1>
          <p>从症状分诊、预约挂号，到查看诊后记录。</p>
        </div>
        <div class="patient-login-visual-foot"><span class="patient-login-foot-line" aria-hidden="true"></span><span>CARE CONTINUES</span><span>↗</span></div>
      </div>

      <div class="patient-login-entry">
        <form class="patient-login-form" novalidate :aria-busy="loading" @submit.prevent="submit">
          <div class="patient-login-form-head"><span class="patient-login-form-index">患者入口 <span>／</span> 安全访问</span><h2>欢迎回来</h2><p>登录后继续查看挂号安排与诊后记录。</p></div>
          <div v-if="error" class="patient-login-error" role="alert"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 3 2 21h20L12 3Z" stroke-linejoin="round"/><path d="M12 9v5m0 3v.01" stroke-linecap="round"/></svg><span>{{ error }}</span></div>
          <div class="patient-login-fields">
            <div class="patient-login-field" :class="{ 'has-error': attempted && !form.account.trim() }">
              <label for="patient-login-account">账号</label>
              <div class="patient-login-input-wrap">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M8 10h8m-8 4h5"/></svg>
                <input id="patient-login-account" v-model.trim="form.account" autocomplete="username" inputmode="text" placeholder="手机号或账号" :aria-invalid="attempted && !form.account.trim()" :aria-describedby="attempted && !form.account.trim() ? 'patient-account-error' : undefined" @input="error = ''" />
              </div>
              <span v-if="attempted && !form.account.trim()" id="patient-account-error" class="patient-login-field-error">请输入账号</span>
            </div>
            <div class="patient-login-field" :class="{ 'has-error': attempted && !form.password.trim() }">
              <label for="patient-login-password">密码</label>
              <div class="patient-login-input-wrap">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/></svg>
                <input id="patient-login-password" v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="输入密码" :aria-invalid="attempted && !form.password.trim()" :aria-describedby="attempted && !form.password.trim() ? 'patient-password-error' : undefined" @input="error = ''" />
                <button class="patient-login-visibility" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword">
                  <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3 21 21M10.6 10.7a2 2 0 0 0 2.7 2.7"/><path d="M9.3 5.3A10 10 0 0 1 12 5c5 0 8.6 3.6 10 7a12 12 0 0 1-3.2 4.2M6.3 6.3A12 12 0 0 0 2 12c1.4 3.4 5 7 10 7a10 10 0 0 0 4.1-.9"/></svg>
                  <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12c1.4-3.5 5-7 10-7s8.6 3.5 10 7c-1.4 3.5-5 7-10 7s-8.6-3.5-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
              </div>
              <span v-if="attempted && !form.password.trim()" id="patient-password-error" class="patient-login-field-error">请输入密码</span>
            </div>
          </div>
          <button class="patient-login-submit" type="submit" :disabled="loading"><span>{{ loading ? "正在登录" : "登录患者端" }}</span><span v-if="loading" class="patient-login-spinner" aria-hidden="true"></span><span v-else class="patient-login-submit-arrow" aria-hidden="true">↗</span></button>
          <div class="patient-login-form-foot"><span>还没有患者账号？</span><RouterLink to="/register">创建账号 <span aria-hidden="true">↗</span></RouterLink></div>
          <div class="patient-login-privacy"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M12 2 4 6v6c0 5 3.4 8.4 8 10 4.6-1.6 8-5 8-10V6l-8-4Z"/><path d="m9 12 2 2 4-4"/></svg><span>请勿在公共设备上保存登录密码。</span></div>
        </form>
      </div>
    </div>
  </section>
</template>
