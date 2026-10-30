<template>
  <header class="topbar">
    <div class="topbar-inner">
      <RouterLink to="/" class="brand">积分交易中心</RouterLink>
      <nav class="nav">
        <RouterLink to="/">商品</RouterLink>
        <RouterLink to="/orders">我的订单</RouterLink>
        <RouterLink to="/points">我的积分</RouterLink>
      </nav>
      <div class="user">
        <RouterLink to="/account">{{ displayName }}</RouterLink>
        <button class="ghost" :disabled="leaving" @click="handleLogout">
          {{ leaving ? '退出中…' : '退出' }}
        </button>
      </div>
    </div>
  </header>
  <main>
    <slot />
  </main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { logout } from '../api/auth'

const auth = useAuthStore()
const router = useRouter()
const leaving = ref(false)

const displayName = computed(() => (auth.user ? auth.user.nickname : '账户'))

async function handleLogout() {
  leaving.value = true
  try {
    await logout()
  } catch (e) {
    // 即使服务端失败，本地也要退出，避免用户卡在登录态
  } finally {
    auth.clear()
    leaving.value = false
    router.replace({ name: 'login' })
  }
}
</script>

<style scoped>
.topbar {
  background: #fff;
  border-bottom: 1px solid var(--line);
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
  gap: 28px;
}

.brand {
  font-weight: 600;
  font-size: 16px;
  color: var(--text);
}

.nav {
  display: flex;
  gap: 18px;
  flex: 1;
}

.nav a {
  color: var(--muted);
}

.nav a.router-link-active {
  color: var(--primary);
  font-weight: 500;
}

.user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user a {
  color: var(--text);
}

.user button {
  padding: 5px 12px;
}
</style>
