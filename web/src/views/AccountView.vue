<template>
  <div class="page" style="max-width:560px">
    <h2 style="margin-top:0">账户设置</h2>

    <div class="panel">
      <div class="between" style="padding:4px 0">
        <span class="muted">用户名</span>
        <span>{{ auth.user?.username || '-' }}</span>
      </div>
      <div class="between" style="padding:4px 0">
        <span class="muted">昵称</span>
        <span>{{ auth.user?.nickname || '-' }}</span>
      </div>
      <div class="between" style="padding:4px 0">
        <span class="muted">角色</span>
        <span class="tag">{{ auth.user?.role || '-' }}</span>
      </div>
    </div>

    <h3 style="margin:28px 0 12px">修改密码</h3>
    <form class="panel" @submit.prevent="submit">
      <div v-if="error" class="error-bar">{{ error }}</div>
      <div v-if="done" class="error-bar" style="background:#e7f7ee;border-color:#bfe6cf;color:var(--ok)">
        密码已修改，正在跳转登录页…
      </div>

      <div class="field">
        <label>原密码</label>
        <input v-model="oldPassword" type="password" autocomplete="current-password" />
      </div>
      <div class="field">
        <label>新密码</label>
        <input v-model="newPassword" type="password" autocomplete="new-password" placeholder="6-32 位" />
      </div>
      <div class="field">
        <label>确认新密码</label>
        <input v-model="newPassword2" type="password" autocomplete="new-password" />
      </div>
      <button type="submit" :disabled="loading || done">
        {{ loading ? '提交中…' : '修改密码' }}
      </button>
      <p class="muted" style="font-size:12px;margin:10px 0 0">
        修改后所有设备上的登录都会失效，需要用新密码重新登录。
      </p>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const oldPassword = ref('')
const newPassword = ref('')
const newPassword2 = ref('')
const loading = ref(false)
const error = ref('')
const done = ref(false)

async function submit() {
  error.value = ''
  if (!oldPassword.value || !newPassword.value) {
    error.value = '请填写原密码和新密码'
    return
  }
  if (newPassword.value !== newPassword2.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  loading.value = true
  try {
    await changePassword(oldPassword.value, newPassword.value)
    done.value = true
    auth.clear()
    setTimeout(() => router.replace({ name: 'login', query: { changed: '1' } }), 1200)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>
