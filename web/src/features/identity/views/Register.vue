<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { register } from '@/features/identity/api/auth'
import { setSession } from '@/store/session'

const router = useRouter()
const route = useRoute()
const form = ref({ username: '', password: '', confirm: '', nickname: '' })
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  const username = form.value.username.trim()
  if (username.length < 3 || username.length > 30) {
    error.value = '用户名需为 3-30 个字符'
    return
  }
  if (form.value.password.length < 8 || !/[a-zA-Z]/.test(form.value.password) || !/\d/.test(form.value.password)) {
    error.value = '密码至少 8 位，且需同时包含字母和数字'
    return
  }
  if (form.value.password !== form.value.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }
  busy.value = true
  try {
    const data = await register(username, form.value.password, form.value.nickname.trim())
    setSession(data)
    router.push(route.query.redirect || '/materials')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="register-page">
    <section class="register-card">
      <div class="eyebrow">CREATE ACCOUNT</div>
      <h1>注册买家账号</h1>
      <p class="intro">注册后即可登录，浏览资料商城并加入购物车。</p>

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input
            v-model.trim="form.username"
            size="large"
            autocomplete="username"
            autofocus
            maxlength="30"
            placeholder="3-30 个字符"
          />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model.trim="form.nickname" size="large" maxlength="30" placeholder="不填则使用用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            autocomplete="new-password"
            maxlength="32"
            placeholder="至少 8 位，包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="form.confirm"
            size="large"
            type="password"
            show-password
            autocomplete="new-password"
            maxlength="32"
            placeholder="再次输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="register-error" />
        <el-button type="primary" size="large" native-type="submit" class="register-button" :loading="busy">
          注册并登录
          <el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
      </el-form>

      <div class="login-hint">
        已有账号？
        <RouterLink :to="{ name: 'login', query: route.query }">直接登录</RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped>
.register-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 42px 24px;
  background: var(--paper);
}

.register-card {
  width: min(440px, 100%);
}

.register-card h1 {
  margin: 10px 0 8px;
  font-family: var(--font-display);
  font-size: 32px;
  letter-spacing: -0.045em;
}

.intro {
  margin: 0 0 28px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.register-error {
  margin-bottom: 16px;
}

.register-button {
  width: 100%;
  margin-top: 4px;
}

.login-hint {
  margin-top: 16px;
  color: var(--muted);
  font-size: 13px;
  text-align: center;
}

.login-hint a {
  color: var(--pine);
  font-weight: 700;
}
</style>