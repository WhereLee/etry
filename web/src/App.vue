<script setup>
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { computed, ref } from 'vue'
import { clearSession, hasRole, isLoggedIn, session } from './store/session'
import { logout } from './api/auth'

const router = useRouter()
const route = useRoute()
const busy = ref(false)

const showTeacher = computed(() => hasRole('TEACHER'))
const showAdmin = computed(() => hasRole('ADMIN'))

async function onLogout() {
  busy.value = true
  try {
    await logout()
  } catch {
    // 后端已经失效也算退出成功，本地状态照样清
  } finally {
    clearSession()
    busy.value = false
    if (route.name !== 'courses') {
      router.push({ name: 'courses' })
    }
  }
}
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <RouterLink to="/" class="brand">网课平台</RouterLink>

      <nav class="nav">
        <RouterLink to="/">课程</RouterLink>
        <RouterLink v-if="isLoggedIn()" to="/me/learning">我的学习</RouterLink>
        <RouterLink v-if="isLoggedIn()" to="/me/favorites">收藏</RouterLink>
        <RouterLink v-if="showTeacher" to="/teacher/courses">我的课程</RouterLink>
        <RouterLink v-if="showTeacher" to="/teacher/dashboard">工作台</RouterLink>
        <RouterLink v-if="showAdmin" to="/admin/review">审核</RouterLink>
      </nav>

      <div class="spacer"></div>

      <div class="row" v-if="isLoggedIn()">
        <span class="muted small">{{ session.nickname || session.username }}</span>
        <button :disabled="busy" @click="onLogout">退出</button>
      </div>
      <RouterLink v-else to="/login" class="login-link">登录</RouterLink>
    </div>
  </header>

  <RouterView />
</template>

<style scoped>
.topbar {
  background: var(--panel);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.topbar-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 20px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  font-weight: 700;
  font-size: 16px;
  color: var(--text);
}

.brand:hover {
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 16px;
}

.nav a {
  color: var(--muted);
  font-size: 14px;
}

.nav a.router-link-active {
  color: var(--primary);
  font-weight: 600;
}

.login-link {
  font-size: 14px;
}
</style>
