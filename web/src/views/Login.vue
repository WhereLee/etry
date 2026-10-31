<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../api/auth'
import { setSession } from '../store/session'

const router = useRouter()
const route = useRoute()

const mode = ref('login')
const form = ref({ username: '', password: '', nickname: '' })
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  if (!form.value.username || !form.value.password) {
    error.value = '用户名和密码都要填'
    return
  }
  busy.value = true
  try {
    const data =
      mode.value === 'login'
        ? await login(form.value.username, form.value.password)
        : await register(form.value.username, form.value.password, form.value.nickname || form.value.username)

    setSession(data)
    if (data.mustChangePassword) {
      router.push({ name: 'password', query: { forced: '1' } })
      return
    }
    router.push(route.query.redirect || '/')
  } catch (e) {
    // 注册被关闭、用户名冲突、密码错误都走这里
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  error.value = ''
}
</script>

<template>
  <div class="page">
    <div class="panel form-panel">
      <h2>{{ mode === 'login' ? '登录' : '注册' }}</h2>
      <p class="muted small">课程列表和详情不用登录也能看，播放和学习要登录。</p>

      <div class="field">
        <label>用户名</label>
        <input v-model.trim="form.username" autocomplete="username" @keyup.enter="submit" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="form.password" type="password" autocomplete="current-password" @keyup.enter="submit" />
      </div>
      <div class="field" v-if="mode === 'register'">
        <label>昵称</label>
        <input v-model.trim="form.nickname" placeholder="不填就用用户名" />
      </div>

      <p class="error" v-if="error">{{ error }}</p>

      <div class="row" style="margin-top: 8px">
        <button class="primary" :disabled="busy" @click="submit">
          {{ busy ? '处理中…' : mode === 'login' ? '登录' : '注册' }}
        </button>
        <button :disabled="busy" @click="switchMode">
          {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-panel {
  max-width: 380px;
  margin: 40px auto;
}

h2 {
  margin: 0 0 4px;
}
</style>
