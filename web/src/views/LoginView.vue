<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h2 style="margin:0 0 4px">登录</h2>
      <p class="muted" style="margin:0 0 20px">积分交易中心</p>

      <div v-if="notice" class="error-bar" style="background:#e7f7ee;border-color:#bfe6cf;color:var(--ok)">
        {{ notice }}
      </div>
      <div v-if="error" class="error-bar">{{ error }}</div>

      <form @submit.prevent="submit">
        <div class="field">
          <label>用户名</label>
          <input v-model.trim="username" autocomplete="username" placeholder="buyer" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="password" type="password" autocomplete="current-password" placeholder="demo123" />
        </div>
        <button type="submit" style="width:100%" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="muted" style="margin:16px 0 0">
        还没有账号？<RouterLink to="/register">去注册</RouterLink>
      </p>
      <p class="muted" style="font-size:12px;margin:6px 0 0">演示账号 buyer / demo123</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

const notice = computed(() => (route.query.changed === '1' ? '密码已修改，请使用新密码重新登录' : ''))

async function submit() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请填写用户名和密码'
    return
  }
  loading.value = true
  try {
    const data = await login(username.value, password.value)
    auth.setToken(data.token)
    auth.setUser({ id: data.userId, username: username.value, nickname: data.nickname, role: data.role })
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>
