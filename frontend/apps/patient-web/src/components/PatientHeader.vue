<script setup lang="ts">
defineProps<{ userName?: string; publicMode?: boolean }>();
defineEmits<{ logout: [] }>();

const NAV = [
  { to: "/triage", label: "症状分诊", en: "TRIAGE" },
  { to: "/doctors", label: "预约医生", en: "DOCTORS" },
  { to: "/appointments", label: "我的挂号", en: "VISITS" },
  { to: "/ripple", label: "健康涟漪", en: "RIPPLE" },
  { to: "/records", label: "病历处方", en: "RECORDS" },
  { to: "/profile", label: "个人资料", en: "PROFILE" },
];
</script>

<template>
  <header class="ink-header">
    <div class="ink-header-bar">
      <RouterLink class="ink-brand" to="/" aria-label="智慧云脑患者端">
        <span class="brand-seal">守</span>
        <span class="brand-text">
          <b>智慧云脑 · 患者守护</b>
          <span class="brand-en mono">PATIENT GUARDIAN / 数字宣纸 · 水墨涟漪</span>
        </span>
      </RouterLink>

      <nav v-if="!publicMode" class="ink-nav" aria-label="患者服务导航">
        <RouterLink v-for="item in NAV" :key="item.to" :to="item.to" class="ink-nav-link">
          <span>{{ item.label }}</span>
          <span class="nav-en mono">{{ item.en }}</span>
        </RouterLink>
      </nav>

      <div class="ink-actions">
        <span v-if="userName" class="user-chip">
          <span class="user-dot" />
          {{ userName }}
        </span>
        <RouterLink v-if="publicMode" class="ghost-btn" to="/register">注册</RouterLink>
        <RouterLink v-if="publicMode" class="seal-btn" to="/login">登录</RouterLink>
        <RouterLink v-else class="seal-btn" to="/triage">开始预约</RouterLink>
        <button v-if="!publicMode" type="button" class="ghost-btn" @click="$emit('logout')">退出</button>
      </div>
    </div>
    <RouterLink v-if="publicMode" to="/" class="ink-sublink">患者服务首页</RouterLink>
  </header>
</template>

<style scoped>
.ink-header {
  position: sticky;
  top: 0;
  z-index: 40;
  padding: 12px 24px 0;
  background: rgba(243, 239, 226, 0.88);
  backdrop-filter: blur(10px);
  border-bottom: 1.5px solid var(--ink);
}

.ink-header-bar {
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding-bottom: 10px;
}

.ink-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--ink);
}

.brand-seal {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 800;
  color: #f6f3e8;
  background: var(--primary);
  border-radius: 9px;
  transform: rotate(-3deg);
  box-shadow: 0 2px 10px rgba(189, 64, 51, 0.28);
}

.brand-text { display: flex; flex-direction: column; line-height: 1.25; }
.brand-text b { font-family: var(--font-serif); font-size: 16.5px; letter-spacing: 1px; }
.brand-en { font-size: 8.5px; color: var(--muted); letter-spacing: 1.6px; }
.mono { font-family: var(--font-mono); }

.ink-nav {
  display: flex;
  gap: 2px;
  flex-wrap: wrap;
  margin-left: 6px;
}

/* 导航：墨线落点而非盒子——悬停晕染，选中朱砂压墨线 */
.ink-nav-link {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  padding: 8px 12px 9px;
  border-radius: 8px 8px 0 0;
  text-decoration: none;
  color: var(--ink-soft);
  transition: color 0.18s ease, background 0.18s ease;
}

.ink-nav-link::after {
  content: "";
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 2.5px;
  background: var(--primary);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.2s ease;
}

.ink-nav-link span:first-child { font-size: 13px; font-weight: 600; }
.nav-en { font-size: 7.5px; letter-spacing: 1.4px; color: var(--subtle); }

.ink-nav-link:hover { color: var(--ink); background: rgba(38, 35, 27, 0.045); }

.ink-nav-link.router-link-active { color: var(--primary); }
.ink-nav-link.router-link-active .nav-en { color: var(--primary); opacity: 0.72; }
.ink-nav-link.router-link-active::after { transform: scaleX(1); }

.ink-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 10px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ink-soft);
  border: 1px dashed var(--line-strong);
  border-radius: 999px;
  padding: 5px 12px;
  background: var(--surface);
}

.user-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); }

.ghost-btn {
  border: 1.5px solid var(--ink);
  background: transparent;
  color: var(--ink);
  border-radius: 9px;
  padding: 7px 13px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}

.ghost-btn:hover { background: var(--ink); color: #f6f3e8; }

.seal-btn {
  border: 1.5px solid var(--primary);
  background: var(--primary);
  color: #f6f3e8;
  border-radius: 9px;
  padding: 7px 15px;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  box-shadow: 0 2px 8px rgba(189, 64, 51, 0.22);
}

.seal-btn:hover { background: var(--primary-strong); border-color: var(--primary-strong); }

.ink-sublink {
  display: inline-block;
  margin: 6px auto 0;
  max-width: 1180px;
  font-size: 12px;
  color: var(--muted);
}
</style>
