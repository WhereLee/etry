<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { changePassword } from '@/features/identity/api/auth'
import { clearSession } from '@/store/session'
import { passwordError } from '@/utils/password'

const route = useRoute()
const router = useRouter()
const forced = computed(() => route.query.forced === '1')
const form = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!form.value.oldPassword || !form.value.newPassword) {
    error.value = '请填写原密码和新密码'
    return
  }
  const passwordIssue = passwordError(form.value.newPassword)
  if (passwordIssue) {
    error.value = passwordIssue
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    error.value = '两次输入的新密码不一致'
    return
  }
  busy.value = true
  try {
    await changePassword(form.value.oldPassword, form.value.newPassword)
    clearSession()
    router.push({ name: 'login', query: { changed: '1' } })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <RouterLink to="/" class="brand-row">
        <span class="brand-seal">学</span>
        <span>学习空间</span>
      </RouterLink>

      <div class="eyebrow">ACCOUNT SECURITY</div>
      <h1>{{ forced ? '首次登录，请设置新密码' : '修改密码' }}</h1>
      <p class="intro">
        {{ forced ? '当前账号仍在使用初始密码。完成修改后会退出登录，请使用新密码重新进入。' : '修改成功后，当前设备会退出登录。' }}
      </p>

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="原密码">
          <el-input v-model="form.oldPassword" size="large" type="password" show-password autocomplete="current-password" autofocus />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" size="large" type="password" show-password autocomplete="new-password" minlength="8" maxlength="32" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" size="large" type="password" show-password autocomplete="new-password" minlength="8" maxlength="32" />
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-error" />
        <el-button type="primary" size="large" native-type="submit" class="submit-button" :loading="busy">
          保存并重新登录
        </el-button>
      </el-form>
    </section>

    <aside class="auth-note">
      <div class="note-index">01</div>
      <h2>密码规则</h2>
      <ul>
        <li>长度为 8～32 位</li>
        <li>同时包含字母和数字</li>
        <li>不要使用常见弱密码</li>
      </ul>
      <div class="note-index">02</div>
      <h2>安全提示</h2>
      <p>密码修改后，旧登录凭证会立即失效。不要把账号密码转发到公开群聊。</p>
    </aside>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  width: min(900px, calc(100% - 32px));
  min-height: calc(100vh - 80px);
  margin: 40px auto;
  grid-template-columns: minmax(0, 1.15fr) minmax(260px, 0.85fr);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-md);
}

.auth-card {
  padding: clamp(30px, 5vw, 58px);
}

.brand-row {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 54px;
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
  margin: 10px 0;
  font-family: var(--font-display);
  font-size: clamp(2rem, 4vw, 2.8rem);
  letter-spacing: -0.05em;
  line-height: 1.2;
}

.intro {
  margin: 0 0 28px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.form-error {
  margin-bottom: 16px;
}

.submit-button {
  width: 100%;
  margin-top: 6px;
}

.auth-note {
  padding: clamp(30px, 4vw, 48px);
  border-left: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  background: var(--pine-deep);
  color: #f5efe4;
}

.note-index {
  color: #dda27d;
  font-family: var(--font-mono);
  font-size: 10px;
}

.auth-note h2 {
  margin: 8px 0 12px;
  font-family: var(--font-display);
  font-size: 19px;
}

.auth-note ul {
  margin: 0 0 34px;
  padding: 0;
  list-style: none;
  color: rgba(245, 239, 228, 0.66);
  font-size: 13px;
  line-height: 2;
}

.auth-note p {
  margin: 0;
  color: rgba(245, 239, 228, 0.66);
  font-size: 13px;
  line-height: 1.8;
}

@media (max-width: 760px) {
  .auth-page {
    grid-template-columns: 1fr;
    margin: 18px auto;
  }

  .brand-row {
    margin-bottom: 36px;
  }

  .auth-note {
    border-top: 1px solid rgba(255, 255, 255, 0.12);
    border-left: 0;
    border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  }

  .auth-note ul {
    margin-bottom: 22px;
  }
}
</style>
