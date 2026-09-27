<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, formatApiError, useAuthStore } from "@smart-cloud-brain/shared-api";

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
  <main class="doctor-auth">
    <section class="doctor-auth-visual" aria-label="DuMate 医生工作台">
      <div class="doctor-auth-grid" aria-hidden="true"></div>
      <div class="doctor-auth-brand">
        <span class="doctor-auth-mark" aria-hidden="true"><span></span></span>
        <span class="doctor-auth-brand-name">DuMate</span>
        <span class="doctor-auth-brand-rule" aria-hidden="true"></span>
        <span class="doctor-auth-brand-role">医生工作台</span>
      </div>
      <div class="doctor-auth-story">
        <div class="doctor-auth-art" aria-hidden="true">
          <div class="doctor-auth-art-glow"></div>
          <svg class="doctor-auth-art-lines" viewBox="0 0 760 760" fill="none">
            <circle class="doctor-auth-art-circle doctor-auth-art-circle-outer" cx="380" cy="380" r="330" />
            <circle class="doctor-auth-art-circle doctor-auth-art-circle-mid" cx="380" cy="380" r="275" />
            <circle class="doctor-auth-art-circle doctor-auth-art-circle-inner" cx="380" cy="380" r="214" />
            <ellipse class="doctor-auth-art-orbit doctor-auth-art-orbit-a" cx="380" cy="380" rx="310" ry="185" transform="rotate(-29 380 380)" />
            <ellipse class="doctor-auth-art-orbit doctor-auth-art-orbit-b" cx="380" cy="380" rx="305" ry="188" transform="rotate(58 380 380)" />
            <path class="doctor-auth-art-curve" d="M95 466C169 406 214 425 276 477C341 533 421 544 503 490C563 451 614 439 676 459" />
            <path class="doctor-auth-art-curve doctor-auth-art-curve-soft" d="M86 491C178 424 220 448 281 503C350 566 433 568 516 512C577 471 624 468 685 486" />
            <circle class="doctor-auth-art-point" cx="601" cy="227" r="4" />
            <circle class="doctor-auth-art-point doctor-auth-art-point-small" cx="177" cy="252" r="2" />
          </svg>
          <div class="doctor-auth-art-core"></div>
        </div>
        <div class="doctor-auth-copy">
          <span class="doctor-auth-kicker"><i></i> DUMATE · DOCTOR ACCESS</span>
          <h1>专注，由此开始<span>.</span></h1>
        </div>
      </div>
      <div class="doctor-auth-visual-foot"><span>DUMATE © 2026</span><span>医生端 · 安全访问</span></div>
    </section>

    <section class="doctor-auth-entry" aria-labelledby="doctor-auth-title">
      <form class="doctor-auth-form" novalidate :aria-busy="loading" @submit.prevent="submit">
        <div class="doctor-auth-form-head">
          <span class="doctor-auth-eyebrow"><span class="doctor-auth-eyebrow-line"></span> 医生身份验证</span>
          <h2 id="doctor-auth-title">进入工作台</h2>
          <p>使用机构开通的医生账号登录。</p>
        </div>
        <div v-if="error" class="doctor-auth-error" role="alert"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5m0 4v.01" stroke-linecap="round"/></svg><span>{{ error }}</span></div>
        <div class="doctor-auth-fields">
          <div class="doctor-auth-field" :class="{ 'has-error': attempted && !form.account.trim() }">
            <label for="doctor-auth-account">医生账号</label>
            <div class="doctor-auth-input"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M8 10h8m-8 4h5"/></svg><input id="doctor-auth-account" v-model.trim="form.account" autocomplete="username" placeholder="输入医生账号" :aria-invalid="attempted && !form.account.trim()" :aria-describedby="attempted && !form.account.trim() ? 'doctor-account-error' : undefined" @input="error = ''" /></div>
            <span v-if="attempted && !form.account.trim()" id="doctor-account-error" class="doctor-auth-field-error">请输入医生账号</span>
          </div>
          <div class="doctor-auth-field" :class="{ 'has-error': attempted && !form.password.trim() }">
            <label for="doctor-auth-password">密码</label>
            <div class="doctor-auth-input"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/></svg><input id="doctor-auth-password" v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="输入密码" :aria-invalid="attempted && !form.password.trim()" :aria-describedby="attempted && !form.password.trim() ? 'doctor-password-error' : undefined" @input="error = ''" /><button class="doctor-auth-visibility" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3 21 21M10.6 10.7a2 2 0 0 0 2.7 2.7"/><path d="M9.3 5.3A10 10 0 0 1 12 5c5 0 8.6 3.6 10 7a12 12 0 0 1-3.2 4.2M6.3 6.3A12 12 0 0 0 2 12c1.4 3.4 5 7 10 7a10 10 0 0 0 4.1-.9"/></svg><svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12c1.4-3.5 5-7 10-7s8.6 3.5 10 7c-1.4 3.5-5 7-10 7s-8.6-3.5-10-7Z"/><circle cx="12" cy="12" r="3"/></svg></button></div>
            <span v-if="attempted && !form.password.trim()" id="doctor-password-error" class="doctor-auth-field-error">请输入密码</span>
          </div>
        </div>
        <button class="doctor-auth-submit" type="submit" :disabled="loading"><span>{{ loading ? "正在验证身份" : "进入医生工作台" }}</span><span v-if="loading" class="doctor-auth-spinner" aria-hidden="true"></span><span v-else class="doctor-auth-submit-icon" aria-hidden="true">↗</span></button>
        <div class="doctor-auth-access"><span class="doctor-auth-access-icon" aria-hidden="true">✦</span><span>医生账号由机构统一开通，请使用授权账号登录。</span></div>
        <div class="doctor-auth-form-foot"><span>DuMate 医生端</span><span aria-hidden="true">·</span><span>安全访问</span></div>
      </form>
    </section>
  </main>
</template>

<style src="./doctor-login.css"></style>
