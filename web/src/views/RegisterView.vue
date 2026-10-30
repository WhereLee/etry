<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h2 style="margin:0 0 4px">注册</h2>
      <p class="muted" style="margin:0 0 20px">注册成功后会自动登录</p>

      <div v-if="error" class="error-bar">{{ error }}</div>

      <form @submit.prevent="submit">
        <div class="field">
          <label>用户名</label>
          <input v-model.trim="username" autocomplete="username" placeholder="4-20 位字母、数字或下划线" />
        </div>
        <div class="field">
          <label>昵称</label>
          <input v-model.trim="nickname" placeholder="显示名称" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="password" type="password" autocomplete="new-password" placeholder="6-32 位" />
        </div>
        <div class="field">
          <label>确认密码</label>
          <input v-model="password2" type="password" autocomplete="new-password" />
        </div>
        <button type="submit" style="width:100%" :disabled="loading">
          {{ loading ? '注册中…' : '注册并登录' }}
        </button>
      </form>

      <p class="muted" style="margin:16px 0 0">
        已有账号？<RouterLink to="/login">去登录</RouterLink>
      </p>
      <p class="muted" style="font-size:12px;margin:6px 0 0">
        新账号初始积分为 0，充值功能尚未开放
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const nickname = ref('')
const password = ref('')
const password2 = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!username.value || !nickname.value || !password.value) {
    error.value = '请填写完整信息'
    return
  }
  if (password.value !== password2.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    const data = await register(username.value, password.value, nickname.value)
    auth.setToken(data.token)
    auth.setUser({ id: data.userId, username: username.value, nickname: data.nickname, role: data.role })
    router.replace('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>
