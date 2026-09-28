<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, formatApiError, useAuthStore } from "@smart-cloud-brain/shared-api";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const form = reactive({ account: "", password: "" });
const loading = ref(false);
const error = ref("");
const attempted = ref(false);
const showPassword = ref(false);

async function submit() {
  attempted.value = true;
  if (!form.account.trim() || !form.password.trim()) {
    error.value = "请输入管理员账号和密码。";
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const session = await api.loginAdmin(form.account.trim(), form.password);
    auth.save("admin-session", session, "ADMIN");
    if (!auth.permissionError) await router.push(String(route.query.redirect || "/"));
  } catch (err) {
    error.value = formatApiError(err, "管理员登录失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="admin-auth">
    <section class="admin-auth-visual" aria-label="DuMate 管理端">
      <div class="admin-auth-visual-top">
        <span class="admin-auth-brand-mark" aria-hidden="true">
          <svg viewBox="0 0 36 36" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M8 10.5 18 5l10 5.5v15L18 31 8 25.5v-15Z" />
            <path d="M13 13.5h10M13 18h10M13 22.5h6" />
            <path d="m23 22.5 2.5 2.5" />
          </svg>
        </span>
        <div class="admin-auth-brand"><strong>DuMate</strong><span>管理工作台</span></div>
      </div>
      <div class="admin-auth-orbit" aria-hidden="true">
        <span class="admin-auth-orbit-ring ring-one"></span>
        <span class="admin-auth-orbit-ring ring-two"></span>
        <span class="admin-auth-orbit-ring ring-three"></span>
        <span class="admin-auth-orbit-core"></span>
        <span class="admin-auth-orbit-point"></span>
      </div>
      <div class="admin-auth-visual-copy">
        <span class="admin-auth-kicker">DUMATE / OPERATIONS</span>
        <h1>让运营<br />有序发生<span>。</span></h1>
      </div>
      <div class="admin-auth-visual-foot"><span>管理每一步，衔接每一程</span><span>© 2026 DUMATE</span></div>
    </section>

    <section class="admin-auth-entry" aria-labelledby="admin-auth-title">
      <form class="admin-auth-form" novalidate :aria-busy="loading" @submit.prevent="submit">
        <div class="admin-auth-form-top"><span class="admin-auth-form-rule"></span><span>管理员身份验证</span></div>
        <h2 id="admin-auth-title">进入管理端</h2>
        <div v-if="error" class="admin-auth-error" role="alert">
          <span class="admin-auth-error-mark" aria-hidden="true">!</span><span>{{ error }}</span>
        </div>
        <div class="admin-auth-fields">
          <div class="admin-auth-field" :class="{ 'has-error': attempted && !form.account.trim() }">
            <label for="admin-auth-account">管理员账号</label>
            <div class="admin-auth-input">
              <span class="admin-auth-input-icon" aria-hidden="true">ID</span>
              <input id="admin-auth-account" v-model.trim="form.account" autocomplete="username" placeholder="输入管理员账号" :aria-invalid="attempted && !form.account.trim()" :aria-describedby="attempted && !form.account.trim() ? 'admin-account-error' : undefined" @input="error = ''" />
            </div>
            <span v-if="attempted && !form.account.trim()" id="admin-account-error" class="admin-auth-field-error">请输入管理员账号</span>
          </div>
          <div class="admin-auth-field" :class="{ 'has-error': attempted && !form.password.trim() }">
            <label for="admin-auth-password">密码</label>
            <div class="admin-auth-input">
              <span class="admin-auth-input-icon admin-auth-lock" aria-hidden="true">✳</span>
              <input id="admin-auth-password" v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="输入密码" :aria-invalid="attempted && !form.password.trim()" :aria-describedby="attempted && !form.password.trim() ? 'admin-password-error' : undefined" @input="error = ''" />
              <button class="admin-auth-visibility" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button>
            </div>
            <span v-if="attempted && !form.password.trim()" id="admin-password-error" class="admin-auth-field-error">请输入密码</span>
          </div>
        </div>
        <button class="admin-auth-submit" type="submit" :disabled="loading">
          <span>{{ loading ? "正在验证身份" : "进入管理工作台" }}</span>
          <span v-if="loading" class="admin-auth-spinner" aria-hidden="true"></span>
          <span v-else class="admin-auth-submit-arrow" aria-hidden="true">↗</span>
        </button>
        <p class="admin-auth-access">请使用机构分配的管理员账号登录。</p>
        <div class="admin-auth-form-foot"><span>DuMate 管理端</span><span>安全访问</span></div>
      </form>
    </section>
  </main>
</template>
