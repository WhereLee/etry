<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { changePassword } from '../api/auth'
import { setSession, session } from '../store/session'

const router = useRouter()
const route = useRoute()

const forced = computed(() => route.query.forced === '1' || session.mustChangePassword)
const form = ref({ oldPassword: '', newPassword: '', confirm: '' })
const error = ref('')
const done = ref(false)
const busy = ref(false)

async function submit() {
  error.value = ''
  if (!form.value.oldPassword || !form.value.newPassword) {
    error.value = '原密码和新密码都要填'
    return
  }
  if (form.value.newPassword.length < 6) {
    error.value = '新密码至少 6 位'
    return
  }
  if (form.value.newPassword !== form.value.confirm) {
    error.value = '两次输入的新密码不一致'
    return
  }
  busy.value = true
  try {
    const data = await changePassword(form.value.oldPassword, form.value.newPassword)
    // 后端用新的 token_version 重签了一个，直接换掉就行。
    // 退回登录页会让用户刚做完的事白做，这里要的是往前推，不是重新开始。
    if (data?.token) {
      setSession({
        token: data.token,
        userId: session.userId,
        username: session.username,
        nickname: session.nickname,
        roles: session.roles,
        mustChangePassword: false
      })
    }
    done.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

/** 改完密码接着往下走：优先回到本来要去的地方 */
function goNext() {
  const redirect = route.query.redirect
  router.push(redirect || '/')
}
</script>

<template>
  <div class="page">
    <div class="panel form-panel">
      <h2>修改密码</h2>

      <div v-if="done">
        <p>密码已修改，可以直接继续使用。</p>
        <p class="muted small">其他设备上的登录已失效，需要用新密码重新登录。</p>
        <button class="primary" @click="goNext">继续</button>
      </div>

      <div v-else>
        <p class="muted small" v-if="forced">
          这是首次登录（或管理员重置后），必须先把密码改掉才能继续使用。
        </p>

        <div class="field">
          <label>原密码</label>
          <input v-model="form.oldPassword" type="password" autocomplete="current-password" />
        </div>
        <div class="field">
          <label>新密码</label>
          <input v-model="form.newPassword" type="password" autocomplete="new-password" />
        </div>
        <div class="field">
          <label>确认新密码</label>
          <input v-model="form.confirm" type="password" autocomplete="new-password" @keyup.enter="submit" />
        </div>

        <p class="error" v-if="error">{{ error }}</p>

        <button class="primary" :disabled="busy" @click="submit">
          {{ busy ? '提交中…' : '确认修改' }}
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
  margin: 0 0 12px;
}
</style>
