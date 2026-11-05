<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/features/identity/api/auth'
import { setSession } from '@/store/session'

const router = useRouter()
const route = useRoute()
const form = ref({ username: '', password: '' })
const busy = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!form.value.username.trim() || !form.value.password) {
    error.value = '请输入账号和密码'
    return
  }
  busy.value = true
  try {
    const data = await login(form.value.username.trim(), form.value.password)
    setSession(data)
    if (data.mustChangePassword) {
      router.push({ name: 'password', query: { forced: '1' } })
      return
    }
    if (data.user?.role === 'ADMIN') {
      router.push({ name: 'adminUsers' })
      return
    }
    if (data.user?.role === 'TEACHER') {
      router.push({ name: 'teacherSeries' })
      return
    }
    router.push(route.query.redirect || '/learning')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="story-grid" aria-hidden="true"></div>
      <div class="story-top">
        <span class="brand-seal">学</span>
        <span class="story-brand">ETRY · 内部课程平台</span>
      </div>

      <div class="story-content">
        <div class="eyebrow">RECORDED LEARNING</div>
        <h1>把课程放回<br /><em>学习本身。</em></h1>
        <p>面向机构内部的录播课程空间。按系列组织内容，保留学习进度，随时从上次的位置继续。</p>
      </div>

      <div class="story-notes">
        <div class="note">
          <span>01</span>
          <div>
            <strong>系列化课程</strong>
            <small>章节与视频按教学顺序展开</small>
          </div>
        </div>
        <div class="note">
          <span>02</span>
          <div>
            <strong>断点续播</strong>
            <small>学习位置自动保存</small>
          </div>
        </div>
        <div class="note">
          <span>03</span>
          <div>
            <strong>内部可控</strong>
            <small>账号、内容与发布状态统一管理</small>
          </div>
        </div>
      </div>

      <div class="story-foot">ETRY LEARNING · 2026</div>
    </section>

    <section class="login-side">
      <div class="login-card">
        <div class="eyebrow">ACCOUNT ACCESS</div>
        <h2>登录学习空间</h2>
        <p class="intro">使用管理员创建的账号，或通过教师邀请完成激活。</p>

        <el-form :model="form" label-position="top" class="login-form" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input
              v-model.trim="form.username"
              size="large"
              autocomplete="username"
              autofocus
              placeholder="请输入账号"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="login-error" />
          <el-button type="primary" size="large" native-type="submit" class="login-button" :loading="busy">
            进入学习空间
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </el-form>

        <div class="demo-box">
          <div class="demo-title">账号说明</div>
          <p>账号由管理员创建，教师通过一次性邀请激活。请向管理员获取登录凭据，初始密码首次登录后会被要求修改。</p>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(0, 1.08fr) minmax(420px, 0.92fr);
  background: var(--paper);
}

.login-story {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  padding: clamp(28px, 4vw, 56px);
  background: var(--pine-deep);
  color: #f7f1e5;
}

.story-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 54px 54px;
  mask-image: linear-gradient(to bottom right, #000 20%, transparent 82%);
  pointer-events: none;
}

.story-story {
  position: relative;
}

.story-top,
.story-content,
.story-notes,
.story-foot {
  position: relative;
  z-index: 1;
}

.story-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-seal {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 11px 4px 11px 4px;
  background: #f4ead7;
  color: var(--pine-deep);
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 700;
}

.story-brand {
  color: rgba(247, 241, 229, 0.68);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.12em;
}

.story-content {
  max-width: 630px;
  margin-top: auto;
  padding: 80px 0 54px;
}

.story-content .eyebrow {
  color: #e3a785;
}

.story-content h1 {
  margin: 18px 0 22px;
  font-family: var(--font-display);
  font-size: clamp(3rem, 6vw, 5.6rem);
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 1.02;
}

.story-content h1 em {
  color: #e4ad81;
  font-style: normal;
}

.story-content p {
  max-width: 520px;
  margin: 0;
  color: rgba(247, 241, 229, 0.68);
  font-size: 15px;
  line-height: 1.85;
}

.story-notes {
  display: grid;
  max-width: 660px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid rgba(255, 255, 255, 0.16);
}

.note {
  display: flex;
  gap: 10px;
  padding: 20px 18px 0 0;
}

.note + .note {
  padding-left: 18px;
  border-left: 1px solid rgba(255, 255, 255, 0.12);
}

.note > span {
  color: #e3a785;
  font-family: var(--font-mono);
  font-size: 10px;
}

.note div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.note strong {
  font-size: 13px;
}

.note small {
  color: rgba(247, 241, 229, 0.5);
  font-size: 11px;
  line-height: 1.5;
}

.story-foot {
  margin-top: 36px;
  color: rgba(247, 241, 229, 0.32);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.login-side {
  display: grid;
  place-items: center;
  padding: 42px clamp(24px, 5vw, 72px);
}

.login-card {
  width: min(420px, 100%);
}

.login-card h2 {
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

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-error {
  margin-bottom: 16px;
}

.login-button {
  width: 100%;
  margin-top: 4px;
}

.demo-box {
  margin-top: 30px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.65);
}

.demo-title {
  margin-bottom: 10px;
  color: var(--text-soft);
  font-size: 12px;
  font-weight: 700;
}

.demo-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 5px 0;
  color: var(--muted);
  font-size: 12px;
}

.demo-row code,
.demo-box p code {
  color: var(--pine);
  font-family: var(--font-mono);
  font-size: 11px;
}

.demo-box p {
  margin: 10px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--border);
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-story {
    min-height: auto;
    padding: 26px 24px 34px;
  }

  .story-content {
    padding: 52px 0 38px;
  }

  .story-content h1 {
    font-size: clamp(2.7rem, 12vw, 4.2rem);
  }

  .story-notes,
  .story-foot {
    display: none;
  }

  .login-side {
    align-items: start;
    padding: 38px 24px 54px;
  }
}
</style>
