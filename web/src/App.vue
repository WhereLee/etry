<script setup>
import { computed } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { clearSession, hasRole, homePath, isLoggedIn, session } from './store/session'

const router = useRouter()
const route = useRoute()
const loggedIn = computed(() => isLoggedIn())
const isAdmin = computed(() => hasRole('ADMIN'))
const isTeacher = computed(() => hasRole('TEACHER'))
const isConsole = computed(() => isAdmin.value || isTeacher.value)
const showShell = computed(() => loggedIn.value && !['login', 'password', 'teacherInvite'].includes(route.name))
const displayName = computed(() => session.user?.nickname || session.user?.username || '用户')
const avatarText = computed(() => String(displayName.value).slice(0, 1).toUpperCase())
const roleLabel = computed(() => isAdmin.value ? '管理员' : isTeacher.value ? '教师' : '学员')
const brand = computed(() => isTeacher.value ? '教师工作台' : '学习空间')

const consoleNav = computed(() => {
  if (isAdmin.value) {
    return [
      { to: '/admin/users', label: '用户管理', icon: 'User' },
      { to: '/admin/teacher-invitations', label: '教师邀请', icon: 'Link' },
      { to: '/admin/series', label: '内容管理', icon: 'Collection' },
      { to: '/admin/orders', label: '订单管理', icon: 'Tickets' },
      { to: '/password', label: '修改密码', icon: 'Lock' }
    ]
  }
  return [
    { to: '/teacher/series', label: '我的系列', icon: 'Reading' },
    { to: '/password', label: '修改密码', icon: 'Lock' }
  ]
})

const studentNav = [
  { to: '/learning', label: '我的学习空间', icon: 'House' },
  { to: '/store', label: '课程商店', icon: 'Collection' },
  { to: '/orders', label: '我的订单', icon: 'Tickets' },
  { to: '/password', label: '修改密码', icon: 'Lock' }
]

function logout() {
  clearSession()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-config-provider :locale="zhCn">
  <div v-if="showShell">
    <div v-if="isConsole" class="console-layout">
      <aside class="console-rail">
        <RouterLink :to="homePath()" class="brand">
          <span class="brand-seal">学</span>
          <span class="brand-copy">
            <strong>学习空间</strong>
            <small>ETRY LEARNING</small>
          </span>
        </RouterLink>

        <div class="rail-role">{{ roleLabel }}工作台</div>
        <nav class="rail-nav" aria-label="主导航">
          <RouterLink v-for="item in consoleNav" :key="item.to" :to="item.to" class="rail-link">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>

        <div class="rail-foot">
          <div class="rail-account">
            <span class="avatar">{{ avatarText }}</span>
            <span class="account-copy">
              <strong>{{ displayName }}</strong>
              <small>{{ roleLabel }}</small>
            </span>
          </div>
          <el-button text class="logout-button" @click="logout">
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </el-button>
        </div>
      </aside>

      <div class="console-main">
        <header class="console-mobile-bar">
          <RouterLink :to="homePath()" class="mobile-brand">
            <span class="brand-seal">学</span>
            <span>{{ brand }}</span>
          </RouterLink>
          <nav class="mobile-links" aria-label="移动端导航">
            <RouterLink v-for="item in consoleNav" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
          </nav>
          <el-button circle text aria-label="退出登录" @click="logout">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </header>
        <RouterView />
      </div>
    </div>

    <div v-else class="student-layout">
      <header class="student-bar">
        <RouterLink :to="homePath()" class="brand">
          <span class="brand-seal">学</span>
          <span class="brand-copy">
            <strong>学习空间</strong>
            <small>ETRY LEARNING</small>
          </span>
        </RouterLink>
        <nav class="student-nav" aria-label="主导航">
          <RouterLink v-for="item in studentNav" :key="item.to" :to="item.to">
            <el-icon><component :is="item.icon" /></el-icon>
            {{ item.label }}
          </RouterLink>
        </nav>
        <span class="spacer"></span>
        <div class="student-account">
          <span class="avatar">{{ avatarText }}</span>
          <span class="account-copy">
            <strong>{{ displayName }}</strong>
            <small>学员</small>
          </span>
        </div>
        <el-button text class="logout-button" @click="logout">
          <el-icon><SwitchButton /></el-icon>
          退出
        </el-button>
      </header>
      <RouterView />
    </div>
  </div>
  <RouterView v-else />
  </el-config-provider>
</template>

<style scoped>
.console-layout {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 248px minmax(0, 1fr);
}

.console-rail {
  position: sticky;
  top: 0;
  display: flex;
  height: 100vh;
  flex-direction: column;
  padding: 26px 18px 20px;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  background: var(--pine-deep);
  color: #f5f1e7;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 8px;
}

.brand:hover {
  color: inherit;
}

.brand-seal {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 10px 4px 10px 4px;
  background: #f4ead7;
  color: var(--pine-deep);
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.brand-copy strong {
  font-family: var(--font-display);
  font-size: 18px;
  letter-spacing: 0.04em;
}

.brand-copy small {
  color: rgba(245, 241, 231, 0.54);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.15em;
}

.rail-role {
  margin: 34px 12px 10px;
  color: rgba(245, 241, 231, 0.42);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.13em;
}

.rail-nav {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.rail-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 13px;
  border-radius: 8px;
  color: rgba(245, 241, 231, 0.7);
  font-size: 14px;
  transition: 0.18s ease;
}

.rail-link:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #fff8ea;
}

.rail-link.router-link-active {
  background: #f1e8d7;
  color: var(--pine-deep);
  font-weight: 700;
}

.rail-link.router-link-active::before {
  position: absolute;
  left: -18px;
  width: 4px;
  height: 20px;
  border-radius: 0 4px 4px 0;
  background: var(--clay);
  content: '';
}

.rail-foot {
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
}

.rail-account,
.student-account {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: var(--clay-soft);
  color: var(--clay);
  font-family: var(--font-display);
  font-weight: 700;
}

.account-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.account-copy strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-copy small {
  color: rgba(245, 241, 231, 0.48);
  font-size: 11px;
}

.logout-button {
  margin-top: 8px;
  color: rgba(245, 241, 231, 0.66);
}

.logout-button:hover {
  color: #fff;
}

.console-main {
  min-width: 0;
}

.console-mobile-bar {
  display: none;
}

.student-layout {
  min-height: 100vh;
}

.student-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 0 max(24px, calc((100vw - 1260px) / 2));
  border-bottom: 1px solid var(--border);
  background: rgba(247, 244, 237, 0.93);
  backdrop-filter: blur(14px);
}

.student-bar .brand {
  height: 70px;
  padding: 0;
}

.student-bar .brand-seal {
  border-color: var(--pine);
  background: var(--pine);
  color: #fff8ea;
}

.student-bar .brand-copy small {
  color: var(--muted);
}

.student-nav {
  display: flex;
  gap: 7px;
}

.student-nav a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 11px;
  border-radius: 7px;
  color: var(--muted);
  font-size: 13px;
  white-space: nowrap;
}

.student-nav a:hover {
  background: var(--pine-soft);
  color: var(--pine);
}

.student-nav a.router-link-active {
  background: var(--pine-soft);
  color: var(--pine);
  font-weight: 700;
}

.student-account {
  padding-left: 14px;
  border-left: 1px solid var(--border);
}

.student-account .account-copy small {
  color: var(--muted);
}

.student-bar .logout-button {
  margin-top: 0;
  color: var(--muted);
}

.student-bar .logout-button:hover {
  color: var(--text);
}

@media (max-width: 900px) {
  .console-layout {
    grid-template-columns: 1fr;
  }

  .console-rail {
    display: none;
  }

  .console-mobile-bar {
    position: sticky;
    top: 0;
    z-index: 20;
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 10px 16px;
    border-bottom: 1px solid var(--border);
    background: rgba(247, 244, 237, 0.95);
    backdrop-filter: blur(14px);
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: none;
    font-family: var(--font-display);
    font-weight: 700;
  }

  .mobile-brand .brand-seal {
    width: 30px;
    height: 30px;
    background: var(--pine);
    color: #fff8ea;
    font-size: 15px;
  }

  .mobile-links {
    display: flex;
    flex: 1;
    gap: 4px;
    overflow-x: auto;
  }

  .mobile-links a {
    flex: none;
    padding: 7px 9px;
    border-radius: 6px;
    color: var(--muted);
    font-size: 12px;
  }

  .mobile-links a.router-link-active {
    background: var(--pine-soft);
    color: var(--pine);
    font-weight: 700;
  }
}

@media (max-width: 1000px) {
  .student-bar {
    gap: 12px;
    padding: 0 18px;
  }

  .student-bar .brand-copy,
  .student-account .account-copy {
    display: none;
  }

  .student-nav .el-icon {
    display: none;
  }

  .student-nav a {
    padding: 7px 8px;
    font-size: 12px;
  }

  .student-account {
    padding-left: 8px;
  }
}

@media (max-width: 760px) {
  .student-bar {
    gap: 12px;
    padding: 0 14px;
  }

  .student-bar .brand-copy,
  .student-account .account-copy {
    display: none;
  }

  .student-bar .brand {
    height: 62px;
  }

  .student-nav {
    overflow-x: auto;
  }

  .student-nav a {
    padding: 7px 8px;
    white-space: nowrap;
  }

  .student-nav .el-icon {
    display: none;
  }

  .student-account {
    padding-left: 8px;
  }

  .logout-button {
    padding: 8px;
  }
}

@media (max-width: 480px) {
  .student-account {
    display: none;
  }

  .student-nav a {
    font-size: 11px;
  }
}
</style>
