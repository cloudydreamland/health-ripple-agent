<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { api, formatApiError } from "@smart-cloud-brain/shared-api";

const router = useRouter();
const loading = ref(false);
const error = ref("");
const notice = ref("");
const attempted = ref(false);
const showPassword = ref(false);
const form = reactive({
  name: "",
  phone: "",
  password: "",
  gender: "FEMALE",
  age: 30,
  allergyHistory: "",
  pastHistory: "",
});

const fieldErrors = computed(() => ({
  name: !form.name.trim() ? "请输入姓名" : "",
  phone: !/^1\d{10}$/.test(form.phone.trim()) ? "请输入 11 位手机号" : "",
  password: (form.password.length < 6 ? "密码至少 6 位" : ""),
  age: !Number.isInteger(form.age) || form.age < 0 || form.age > 120 ? "请输入 0 至 120 岁之间的整数" : "",
}));

function togglePasswordVisibility() {
  showPassword.value = !showPassword.value;
}

async function submit() {
  if (notice.value) return;
  attempted.value = true;
  const invalid = Object.values(fieldErrors.value).find(Boolean);
  if (invalid) {
    error.value = invalid;
    return;
  }
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    await api.registerPatient({ ...form, phone: form.phone.trim() });
    notice.value = "注册成功，请登录后继续分诊和挂号。";
    window.setTimeout(() => router.push({ name: "patient-login" }), 600);
  } catch (err) {
    error.value = formatApiError(err, "注册失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="mayo-container auth-grid patient-login-page patient-register-page">
    <div class="patient-login-stage patient-register-stage">
      <div class="patient-login-visual patient-register-visual">
        <div class="patient-login-visual-grid" aria-hidden="true"></div>
        <div class="patient-login-aura" aria-hidden="true"><span></span><span></span><span></span></div>
        <div class="patient-login-orbit" aria-hidden="true"><span class="orbit-one"></span><span class="orbit-two"></span><span class="orbit-three"></span><span class="orbit-core"></span><span class="orbit-satellite"></span></div>
        <div class="patient-login-visual-head"><span class="patient-login-mark" aria-hidden="true"><span></span></span><span>涟漪守护 <i></i> 患者服务</span></div>
        <div class="patient-login-visual-copy">
          <span class="patient-login-index">新患者 / 建立就诊档案</span>
          <h1>让关键信息<br />有处可循<span class="patient-login-title-dot">.</span></h1>
          <p>姓名与手机号用于识别就诊人；健康资料帮助医生了解病史。</p>
        </div>
        <div class="patient-login-visual-foot"><span class="patient-login-foot-line" aria-hidden="true"></span><span>CARE BEGINS HERE</span><span>↗</span></div>
      </div>

      <div class="patient-login-entry patient-register-entry">
        <form class="patient-login-form patient-register-form" novalidate :aria-busy="loading" @submit.prevent="submit">
          <div class="patient-login-form-head patient-register-form-head">
            <span class="patient-login-form-index">患者入口 <span>／</span> 建立档案</span>
            <h2>创建患者档案</h2>
            <p>请填写真实资料，供挂号和接诊时核对。</p>
          </div>
          <div v-if="error" class="patient-login-error" role="alert"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 3 2 21h20L12 3Z" stroke-linejoin="round"/><path d="M12 9v5m0 3v.01" stroke-linecap="round"/></svg><span>{{ error }}</span></div>
          <div v-if="notice" class="patient-register-success" role="status">{{ notice }}</div>

          <fieldset class="patient-register-section">
            <legend><span>01</span> 账号与身份</legend>
            <div class="patient-register-fields">
              <div class="patient-login-field" :class="{ 'has-error': attempted && fieldErrors.name }">
                <label for="patient-register-name">姓名</label>
                <div class="patient-login-input-wrap"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/></svg><input id="patient-register-name" v-model.trim="form.name" autocomplete="name" placeholder="就诊人姓名" :aria-invalid="attempted && !!fieldErrors.name" :aria-describedby="attempted && fieldErrors.name ? 'register-name-error' : undefined" @input="error = ''" /></div>
                <span v-if="attempted && fieldErrors.name" id="register-name-error" class="patient-login-field-error">{{ fieldErrors.name }}</span>
              </div>
              <div class="patient-login-field" :class="{ 'has-error': attempted && fieldErrors.phone }">
                <label for="patient-register-phone">手机号</label>
                <div class="patient-login-input-wrap"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><rect x="6" y="2" width="12" height="20" rx="3"/><path d="M10 18h4"/></svg><input id="patient-register-phone" v-model.trim="form.phone" type="tel" inputmode="tel" autocomplete="tel" placeholder="11 位手机号" :aria-invalid="attempted && !!fieldErrors.phone" :aria-describedby="attempted && fieldErrors.phone ? 'register-phone-error' : undefined" @input="error = ''" /></div>
                <span v-if="attempted && fieldErrors.phone" id="register-phone-error" class="patient-login-field-error">{{ fieldErrors.phone }}</span>
              </div>
              <div class="patient-login-field patient-register-wide" :class="{ 'has-error': attempted && fieldErrors.password }">
                <label for="patient-register-password">设置密码</label>
                <div class="patient-login-input-wrap"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg><input id="patient-register-password" v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 6 位" :aria-invalid="attempted && !!fieldErrors.password" :aria-describedby="attempted && fieldErrors.password ? 'register-password-error' : undefined" @input="error = ''" /><button class="patient-login-visibility" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="togglePasswordVisibility"><svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M3 3 21 21M10.6 10.7a2 2 0 0 0 2.7 2.7"/><path d="M9.3 5.3A10 10 0 0 1 12 5c5 0 8.6 3.6 10 7a12 12 0 0 1-3.2 4.2M6.3 6.3A12 12 0 0 0 2 12c1.4 3.4 5 7 10 7a10 10 0 0 0 4.1-.9"/></svg><svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M2 12c1.4-3.5 5-7 10-7s8.6 3.5 10 7c-1.4 3.5-5 7-10 7s-8.6-3.5-10-7Z"/><circle cx="12" cy="12" r="3"/></svg></button></div>
                <span v-if="attempted && fieldErrors.password" id="register-password-error" class="patient-login-field-error">{{ fieldErrors.password }}</span>
              </div>
            </div>
          </fieldset>

          <fieldset class="patient-register-section patient-register-health">
            <legend><span>02</span> 健康资料</legend>
            <div class="patient-register-fields">
              <div class="patient-login-field" :class="{ 'has-error': attempted && fieldErrors.age }">
                <label for="patient-register-age">年龄</label>
                <div class="patient-login-input-wrap"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><path d="M4 19h16M7 19V6l5-3 5 3v13M10 10h4m-4 4h4"/></svg><input id="patient-register-age" v-model.number="form.age" type="number" min="0" max="120" inputmode="numeric" :aria-invalid="attempted && !!fieldErrors.age" :aria-describedby="attempted && fieldErrors.age ? 'register-age-error' : undefined" @input="error = ''" /></div>
                <span v-if="attempted && fieldErrors.age" id="register-age-error" class="patient-login-field-error">{{ fieldErrors.age }}</span>
              </div>
              <div class="patient-login-field">
                <label for="patient-register-gender">性别</label>
                <div class="patient-login-input-wrap patient-register-select-wrap"><select id="patient-register-gender" v-model="form.gender"><option value="FEMALE">女</option><option value="MALE">男</option><option value="UNKNOWN">未说明</option></select><span class="patient-register-select-chevron" aria-hidden="true"></span></div>
              </div>
              <div class="patient-login-field patient-register-wide">
                <label for="patient-register-allergy">过敏史 <span class="patient-register-optional">选填</span></label>
                <div class="patient-login-input-wrap"><input id="patient-register-allergy" v-model.trim="form.allergyHistory" placeholder="如无可留空" /></div>
              </div>
              <div class="patient-login-field patient-register-wide">
                <label for="patient-register-history">既往史 <span class="patient-register-optional">选填</span></label>
                <div class="patient-login-input-wrap patient-register-textarea-wrap"><textarea id="patient-register-history" v-model.trim="form.pastHistory" rows="3" placeholder="填写曾患疾病、手术等信息；如无可留空"></textarea></div>
              </div>
            </div>
          </fieldset>

          <button class="patient-login-submit patient-register-submit" type="submit" :disabled="loading || !!notice"><span>{{ loading ? "正在创建档案" : "创建档案" }}</span><span v-if="loading" class="patient-login-spinner" aria-hidden="true"></span><span v-else class="patient-login-submit-arrow" aria-hidden="true">↗</span></button>
          <div class="patient-login-form-foot patient-register-form-foot"><span>已有患者账号？</span><RouterLink to="/login">返回登录 <span aria-hidden="true">↗</span></RouterLink></div>
        </form>
      </div>
    </div>
  </section>
</template>
