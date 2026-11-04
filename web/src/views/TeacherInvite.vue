<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { acceptTeacherInvitation, previewTeacherInvitation } from '../api/invitation'
import { passwordError } from '../utils/password'

const route = useRoute()
const router = useRouter()
const token = computed(() => String(route.params.token || ''))
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const invitation = ref(null)
const success = ref(false)
const form = ref({ password: '', confirmPassword: '' })

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    invitation.value = await previewTeacherInvitation(token.value)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  const issue = passwordError(form.value.password)
  if (issue) {
    error.value = issue
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    error.value = '两次输入的密码不一致'
    return
  }
  busy.value = true
  try {
    await acceptTeacherInvitation({ token: token.value, password: form.value.password })
    success.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="invite-page">
    <section class="invite-card">
      <div class="card-accent"></div>
      <RouterLink to="/login" class="brand-row">
        <span class="brand-seal">学</span>
        <span>学习空间</span>
      </RouterLink>

      <div class="eyebrow">TEACHER INVITATION</div>
      <h1>教师账号激活</h1>

      <div v-if="loading" class="loading-block">
        <el-skeleton animated :rows="5" />
      </div>

      <el-result v-else-if="success" icon="success" title="账号已激活" sub-title="教师账号已经创建，可以前往登录。">
        <template #extra>
          <div class="activated-account">
            <span>登录账号</span>
            <code>{{ invitation?.username }}</code>
          </div>
          <el-button type="primary" size="large" @click="router.push({ name: 'login' })">前往登录</el-button>
        </template>
      </el-result>

      <template v-else-if="invitation">
        <p class="intro">邀请绑定的身份不可修改。请为这个教师账号设置你自己的登录密码。</p>
        <div class="identity">
          <span class="identity-label">受邀教师</span>
          <strong>{{ invitation.nickname }}</strong>
          <code>{{ invitation.username }}</code>
        </div>
        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="设置密码">
            <el-input v-model="form.password" size="large" type="password" show-password autocomplete="new-password" minlength="8" maxlength="32" autofocus />
            <span class="field-hint">8～32 位，同时包含字母和数字。</span>
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPassword" size="large" type="password" show-password autocomplete="new-password" minlength="8" maxlength="32" />
          </el-form-item>
          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-error" />
          <el-button type="primary" size="large" native-type="submit" class="wide" :loading="busy">
            激活教师账号
          </el-button>
        </el-form>
      </template>

      <el-result v-else icon="error" title="邀请不可用" :sub-title="error || '邀请无效或已过期，请联系管理员重新生成。'">
        <template #extra>
          <el-button size="large" @click="router.push({ name: 'login' })">返回登录</el-button>
        </template>
      </el-result>
    </section>
  </main>
</template>

<style scoped>
.invite-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 28px;
}

.invite-card {
  position: relative;
  overflow: hidden;
  width: min(500px, 100%);
  padding: 42px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-md);
}

.card-accent {
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
  background: var(--clay);
}

.brand-row {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 38px;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
}

.brand-seal {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 9px 3px 9px 3px;
  background: var(--pine);
  color: #fff8ea;
  font-size: 17px;
}

h1 {
  margin: 9px 0 10px;
  font-family: var(--font-display);
  font-size: 32px;
  letter-spacing: -0.045em;
}

.loading-block {
  padding: 18px 0;
}

.intro {
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.identity {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 24px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--pine);
  border-radius: var(--radius-sm);
  background: #f8f5ee;
}

.identity-label {
  color: var(--muted);
  font-size: 10px;
}

.identity strong {
  font-family: var(--font-display);
  font-size: 19px;
}

.identity code {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.field-hint {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  font-size: 11px;
}

.form-error {
  margin-bottom: 16px;
}

.wide {
  width: 100%;
}

.activated-account {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 18px;
  color: var(--muted);
  font-size: 12px;
}

.activated-account code {
  color: var(--pine);
  font-family: var(--font-mono);
}

@media (max-width: 560px) {
  .invite-page {
    padding: 14px;
  }

  .invite-card {
    padding: 30px 24px;
  }
}
</style>
