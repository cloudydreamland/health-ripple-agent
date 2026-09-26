<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";

defineProps<{ userName?: string; publicMode?: boolean }>();
defineEmits<{ logout: [] }>();

const route = useRoute();
const headerRef = ref<HTMLElement | null>(null);
const openMenu = ref<"visit" | "aftercare" | "account" | null>(null);
const mobileOpen = ref(false);
let suppressFocusOpen = false;
const visitRoutes = [
  { to: "/triage", label: "症状分诊", icon: "✳" },
  { to: "/doctors", label: "预约医生", icon: "↗" },
  { to: "/appointments", label: "我的挂号", icon: "◷" },
];
const aftercareRoutes = [
  { to: "/records", label: "病历", icon: "▤" },
  { to: "/prescriptions", label: "处方", icon: "✚" },
  { to: "/ripple", label: "健康涟漪", icon: "◎" },
];
const activeGroup = computed(() => visitRoutes.some((item) => item.to === route.path)
  ? "visit" : aftercareRoutes.some((item) => item.to === route.path) ? "aftercare" : null);

function closeMenus() { openMenu.value = null; mobileOpen.value = false; }
function onMenuFocus(menu: "visit" | "aftercare" | "account") {
  if (!suppressFocusOpen) openMenu.value = menu;
}
function onOutsidePointer(event: PointerEvent) {
  if (headerRef.value && !headerRef.value.contains(event.target as Node)) closeMenus();
}
function onHeaderFocusOut(event: FocusEvent) {
  if (!headerRef.value?.contains(event.relatedTarget as Node | null)) openMenu.value = null;
}
function onHeaderEscape() {
  const trigger = headerRef.value?.querySelector<HTMLElement>(`[data-menu-trigger="${openMenu.value}"]`);
  suppressFocusOpen = true;
  closeMenus();
  trigger?.focus();
  queueMicrotask(() => { suppressFocusOpen = false; });
}
onMounted(() => document.addEventListener("pointerdown", onOutsidePointer));
onBeforeUnmount(() => document.removeEventListener("pointerdown", onOutsidePointer));
watch(() => route.fullPath, closeMenus);

</script>

<template>
  <header ref="headerRef" class="patient-modern-header" @keydown.esc="onHeaderEscape" @focusout="onHeaderFocusOut">
    <div class="patient-modern-header-main">
      <RouterLink class="patient-modern-brand" to="/" aria-label="涟漪守护患者端首页">
        <span class="patient-modern-symbol" aria-hidden="true"><span></span><span></span></span>
        <strong>涟漪守护</strong>
      </RouterLink>
      <nav v-if="!publicMode" class="patient-modern-nav" aria-label="患者服务导航">
        <RouterLink class="patient-modern-nav-link" to="/" :class="{ current: route.path === '/' }" :aria-current="route.path === '/' ? 'page' : undefined">首页</RouterLink>
        <div class="patient-modern-nav-group" @pointerenter="openMenu = 'visit'" @pointerleave="openMenu = null">
          <button type="button" class="patient-modern-nav-trigger" data-menu-trigger="visit" :class="{ current: activeGroup === 'visit', expanded: openMenu === 'visit' }"
                  :aria-expanded="openMenu === 'visit'" aria-controls="patient-visit-menu" @focus="onMenuFocus('visit')" @click="openMenu = 'visit'">就诊服务 <span class="patient-modern-chevron" aria-hidden="true"></span></button>
          <Transition name="patient-nav-pop">
            <div v-if="openMenu === 'visit'" id="patient-visit-menu" class="patient-modern-dropdown">
              <RouterLink v-for="item in visitRoutes" :key="item.to" :to="item.to" class="patient-modern-dropdown-link" :class="{ current: route.path === item.to }" @click="closeMenus">
                <span class="patient-modern-menu-icon" aria-hidden="true">{{ item.icon }}</span><span>{{ item.label }}</span><span class="patient-modern-link-arrow" aria-hidden="true">↗</span>
              </RouterLink>
            </div>
          </Transition>
        </div>
        <div class="patient-modern-nav-group" @pointerenter="openMenu = 'aftercare'" @pointerleave="openMenu = null">
          <button type="button" class="patient-modern-nav-trigger" data-menu-trigger="aftercare" :class="{ current: activeGroup === 'aftercare', expanded: openMenu === 'aftercare' }"
                  :aria-expanded="openMenu === 'aftercare'" aria-controls="patient-aftercare-menu" @focus="onMenuFocus('aftercare')" @click="openMenu = 'aftercare'">诊后服务 <span class="patient-modern-chevron" aria-hidden="true"></span></button>
          <Transition name="patient-nav-pop">
            <div v-if="openMenu === 'aftercare'" id="patient-aftercare-menu" class="patient-modern-dropdown">
              <RouterLink v-for="item in aftercareRoutes" :key="item.to" :to="item.to" class="patient-modern-dropdown-link" :class="{ current: route.path === item.to }" @click="closeMenus">
                <span class="patient-modern-menu-icon" aria-hidden="true">{{ item.icon }}</span><span>{{ item.label }}</span><span class="patient-modern-link-arrow" aria-hidden="true">↗</span>
              </RouterLink>
            </div>
          </Transition>
        </div>
      </nav>
      <div class="patient-modern-actions">
        <RouterLink v-if="!publicMode" class="patient-modern-quick-link" to="/doctors">查看号源 <span aria-hidden="true">↗</span></RouterLink>
        <RouterLink v-if="publicMode" :to="route.path === '/login' ? '/register' : '/login'" class="patient-modern-account">{{ route.path === '/login' ? '注册账号' : '登录' }}</RouterLink>
        <div v-else class="patient-modern-account-group" @pointerenter="openMenu = 'account'" @pointerleave="openMenu = null">
          <button type="button" class="patient-modern-account-trigger" data-menu-trigger="account" :aria-expanded="openMenu === 'account'" aria-controls="patient-account-menu"
                  @focus="onMenuFocus('account')" @click="openMenu = 'account'"><span class="patient-modern-avatar">{{ userName?.slice(0, 1) || '我' }}</span><span class="patient-modern-user">{{ userName || '个人账号' }}</span><span class="patient-modern-chevron" aria-hidden="true"></span></button>
          <Transition name="patient-nav-pop">
            <div v-if="openMenu === 'account'" id="patient-account-menu" class="patient-modern-dropdown patient-modern-account-dropdown">
              <RouterLink class="patient-modern-dropdown-link" to="/profile" @click="closeMenus">个人资料 <span class="patient-modern-link-arrow" aria-hidden="true">↗</span></RouterLink>
              <button type="button" class="patient-modern-dropdown-link" @click="closeMenus(); $emit('logout')">退出登录 <span class="patient-modern-link-arrow" aria-hidden="true">↗</span></button>
            </div>
          </Transition>
        </div>
        <button v-if="!publicMode" class="patient-modern-mobile-toggle" type="button" :aria-expanded="mobileOpen" aria-controls="patient-mobile-menu" :aria-label="mobileOpen ? '关闭导航' : '打开导航'" @click="mobileOpen = !mobileOpen"><span></span><span></span><span></span></button>
      </div>
    </div>
    <nav v-if="!publicMode && mobileOpen" id="patient-mobile-menu" class="patient-modern-mobile-menu" aria-label="患者服务导航">
      <RouterLink v-for="item in [{ to: '/', label: '首页' }, ...visitRoutes, ...aftercareRoutes, { to: '/profile', label: '个人资料' }]" :key="item.to" :to="item.to" @click="closeMenus">{{ item.label }}</RouterLink>
      <button type="button" @click="closeMenus(); $emit('logout')">退出登录</button>
    </nav>
  </header>
</template>

<style scoped>
.patient-modern-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255, 255, 255, .96);
  border-bottom: 1px solid #d9e6e0;
  box-shadow: 0 8px 28px rgba(12, 57, 43, .035);
  backdrop-filter: blur(16px);
}
.patient-modern-header-main {
  width: min(100%, 1280px);
  min-height: 76px;
  margin: auto;
  padding: 0 28px;
  display: flex;
  align-items: center;
  gap: clamp(28px, 4vw, 68px);
}
.patient-modern-brand { display: inline-flex; align-items: center; gap: 12px; color: #113c31; white-space: nowrap; text-decoration: none; }
.patient-modern-brand strong { font-size: 20px; font-weight: 760; letter-spacing: -.035em; }
.patient-modern-symbol { position: relative; display: grid; place-items: center; width: 39px; height: 39px; border-radius: 12px; background: #087858; box-shadow: 0 6px 14px rgba(8, 120, 88, .17); }
.patient-modern-symbol span { position: absolute; width: 20px; height: 20px; border: 1.8px solid #fff; border-radius: 50%; }
.patient-modern-symbol span:last-child { width: 9px; height: 9px; }
.patient-modern-nav { align-self: stretch; display: flex; align-items: stretch; gap: 6px; }
.patient-modern-nav-group, .patient-modern-account-group { position: relative; display: flex; align-items: center; }
.patient-modern-nav-link, .patient-modern-nav-trigger {
  position: relative; display: inline-flex; align-items: center; justify-content: center; gap: 9px;
  min-height: 42px; margin: auto 0; padding: 0 15px; border: 0; border-radius: 10px;
  color: #46665b; background: transparent; font: inherit; font-size: 15px; font-weight: 600;
  text-decoration: none; white-space: nowrap; cursor: pointer;
  transition: color .18s ease, background .18s ease;
}
.patient-modern-nav-link::after, .patient-modern-nav-trigger::after {
  content: ''; position: absolute; left: 16px; right: 16px; bottom: -17px; height: 3px;
  border-radius: 3px 3px 0 0; background: #078762; transform: scaleX(0); opacity: 0;
  transition: transform .18s ease, opacity .18s ease;
}
.patient-modern-nav-link:hover, .patient-modern-nav-trigger:hover, .patient-modern-nav-trigger.expanded { background: #eff8f3; color: #075d45; }
.patient-modern-nav-link.current, .patient-modern-nav-trigger.current { color: #075d45; background: #e9f5ef; }
.patient-modern-nav-link.current::after, .patient-modern-nav-trigger.current::after { transform: scaleX(1); opacity: 1; }
.patient-modern-chevron { display: inline-block; width: 7px; height: 7px; margin-top: -4px; border-right: 1.7px solid currentColor; border-bottom: 1.7px solid currentColor; transform: rotate(45deg); transition: transform .18s ease; }
.expanded > .patient-modern-chevron, [aria-expanded='true'] > .patient-modern-chevron { transform: translateY(3px) rotate(225deg); }
.patient-modern-actions { margin-left: auto; display: flex; align-items: center; gap: 17px; }
.patient-modern-quick-link { display: inline-flex; align-items: center; gap: 8px; padding: 9px 12px; color: #087858; font-size: 14px; font-weight: 700; text-decoration: none; white-space: nowrap; border-radius: 9px; transition: background .18s ease, gap .18s ease; }
.patient-modern-quick-link:hover { gap: 13px; background: #eaf7f0; }
.patient-modern-account-trigger { display: flex; align-items: center; gap: 9px; min-height: 42px; padding: 3px 8px 3px 4px; border: 0; border-radius: 22px; background: transparent; color: #23483d; cursor: pointer; transition: background .18s ease; }
.patient-modern-account-trigger:hover, .patient-modern-account-trigger[aria-expanded='true'] { background: #eff8f3; }
.patient-modern-avatar { display: grid; place-items: center; flex: none; width: 34px; height: 34px; border-radius: 50%; background: #d9eee2; color: #086348; font-size: 14px; font-weight: 750; }
.patient-modern-user { font-size: 14px; font-weight: 650; white-space: nowrap; }
.patient-modern-account { color: #087858; text-decoration: none; }
.patient-modern-dropdown { position: absolute; top: 100%; left: 0; z-index: 80; width: 306px; padding: 9px; border: 1px solid #d9e8e0; border-radius: 15px; background: #fff; box-shadow: 0 22px 56px rgba(12, 53, 41, .16); }
.patient-modern-dropdown-link { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 51px; padding: 8px 11px; border: 0; border-radius: 10px; background: transparent; color: #20483c; font: inherit; font-size: 15px; font-weight: 620; text-align: left; text-decoration: none; cursor: pointer; transition: background .18s ease, color .18s ease, transform .18s ease; }
.patient-modern-dropdown-link:hover, .patient-modern-dropdown-link:focus-visible, .patient-modern-dropdown-link.current { background: #edf8f1; color: #08684d; transform: translateX(2px); }
.patient-modern-menu-icon { display: grid; place-items: center; flex: none; width: 32px; height: 32px; border-radius: 9px; background: #e7f3eb; color: #0b7355; font-size: 17px; line-height: 1; }
.patient-modern-link-arrow { margin-left: auto; color: #6c9986; opacity: 0; transform: translate(-4px, 4px); transition: opacity .18s ease, transform .18s ease; }
.patient-modern-dropdown-link:hover .patient-modern-link-arrow, .patient-modern-dropdown-link:focus-visible .patient-modern-link-arrow { opacity: 1; transform: translate(0); }
.patient-modern-account-dropdown { left: auto; right: 0; width: 196px; }
.patient-modern-mobile-toggle, .patient-modern-mobile-menu { display: none; }
.patient-nav-pop-enter-active, .patient-nav-pop-leave-active { transition: opacity .18s ease, transform .18s ease; }
.patient-nav-pop-enter-from, .patient-nav-pop-leave-to { opacity: 0; transform: translateY(-5px) scale(.985); }
.patient-modern-header :is(a, button):focus-visible { outline: 3px solid #84d7bb; outline-offset: 2px; }
@media (max-width: 1050px) {
  .patient-modern-header-main { gap: 20px; }
  .patient-modern-quick-link { display: none; }
}
@media (max-width: 790px) {
  .patient-modern-header-main { min-height: 66px; padding: 0 18px; }
  .patient-modern-nav, .patient-modern-account-group { display: none; }
  .patient-modern-mobile-toggle { display: grid; gap: 4px; place-content: center; width: 42px; height: 42px; border: 0; border-radius: 9px; background: #edf6f1; cursor: pointer; }
  .patient-modern-mobile-toggle span { display: block; width: 18px; height: 2px; border-radius: 2px; background: #0a644d; }
  .patient-modern-mobile-menu { display: grid; gap: 2px; padding: 8px 18px 18px; border-top: 1px solid #e2ebe6; background: #fff; }
  .patient-modern-mobile-menu a, .patient-modern-mobile-menu button { display: block; width: 100%; padding: 12px 15px; border: 0; border-radius: 9px; background: transparent; color: #20483c; font: inherit; font-size: 15px; text-align: left; text-decoration: none; }
  .patient-modern-mobile-menu a.router-link-exact-active { background: #e8f6ed; color: #076b50; font-weight: 700; }
}
@media (prefers-reduced-motion: reduce) {
  .patient-modern-header *, .patient-modern-header *::before, .patient-modern-header *::after { transition-duration: .01ms !important; animation-duration: .01ms !important; }
}

</style>
