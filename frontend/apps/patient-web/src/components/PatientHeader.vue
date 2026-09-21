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
      <RouterLink class="ink-brand" to="/" aria-label="涟漪守护患者端">
        <span class="brand-seal">守</span>
        <span class="brand-text">
          <b>涟漪守护 · 患者端</b>
          <span class="brand-en mono">RIU PATIENT / RIU-GUARD 5 · 复古监护仪</span>
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
/* 机身顶部面板：米黄注塑 + 底部接缝，像仪器顶盖 */
.ink-header {
  position: sticky;
  top: 0;
  z-index: 40;
  padding: 12px 24px 0;
  background:
    radial-gradient(900px 160px at 50% -60px, rgba(255, 248, 224, 0.8), transparent 70%),
    repeating-linear-gradient(0deg, rgba(120, 106, 70, 0.05) 0 1px, transparent 1px 4px),
    #e9e1cb;
  border-bottom: 2px solid #a89a74;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.55) inset, 0 3px 10px rgba(70, 58, 30, 0.18);
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

/* 电源铭牌：深绿底荧光印字（像仪器型号牌） */
.brand-seal {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  font-size: 21px;
  font-weight: 800;
  color: #a5ffc6;
  background: #0d1f14;
  border-radius: 9px;
  border: 1px solid #b5a887;
  box-shadow: 0 0 14px rgba(88, 224, 143, 0.28), 0 2px 0 #b3a67e;
  text-shadow: 0 0 8px rgba(88, 224, 143, 0.8);
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

/* 导航：仪器按键排——悬停微微抬起，选中亮"通道灯" */
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
  background: var(--phosphor);
  box-shadow: 0 0 8px rgba(88, 224, 143, 0.8);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.2s ease;
}

.ink-nav-link span:first-child { font-size: 13px; font-weight: 600; }
.nav-en { font-size: 7.5px; letter-spacing: 1.4px; color: var(--subtle); }

.ink-nav-link:hover { color: var(--ink); background: rgba(43, 42, 34, 0.05); }

.ink-nav-link.router-link-active { color: var(--primary-strong); }
.ink-nav-link.router-link-active .nav-en { color: var(--primary); opacity: 0.8; }
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
  background: rgba(255, 252, 240, 0.7);
}

/* 电源指示灯 */
.user-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--phosphor);
  box-shadow: 0 0 6px rgba(88, 224, 143, 0.9);
}

/* 实体键：可按压 */
.ghost-btn {
  border: 1px solid #8d7f5e;
  background: linear-gradient(180deg, #f7f1df, #e4dbc2);
  color: var(--ink);
  border-radius: 9px;
  padding: 7px 13px;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  box-shadow: 0 3px 0 #b3a67e;
  transition: transform 0.08s ease, box-shadow 0.08s ease;
}
.ghost-btn:active { transform: translateY(3px); box-shadow: 0 0 0 #b3a67e; }

.seal-btn {
  border: 1px solid var(--primary-strong);
  background: linear-gradient(180deg, #2c9a5e, #1d7a4a);
  color: #f2fff5;
  border-radius: 9px;
  padding: 7px 15px;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  box-shadow: 0 3px 0 var(--primary-strong), 0 5px 10px rgba(20, 92, 55, 0.28);
  transition: transform 0.08s ease, box-shadow 0.08s ease;
}
.seal-btn:active { transform: translateY(3px); box-shadow: 0 0 0 var(--primary-strong); }

.ink-sublink {
  display: inline-block;
  margin: 6px auto 0;
  max-width: 1180px;
  font-size: 12px;
  color: var(--muted);
}
</style>
