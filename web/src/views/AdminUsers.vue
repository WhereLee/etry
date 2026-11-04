<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  assignUserSeries,
  changeUserStatus,
  createUser,
  getUserSeries,
  listAllSeries,
  listUsers,
  resetUserPassword
} from '../api/admin'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'
import { session } from '../store/session'
import { generateStrongPassword, passwordError } from '../utils/password'

const filters = reactive({ keyword: '', role: '', status: '', page: 1, size: 10 })
const state = ref({ records: [], total: 0, page: 1, size: 10 })
const loading = ref(true)
const error = ref('')
const notice = ref('')
const dialogError = ref('')
const busy = ref('')

const createOpen = ref(false)
const createBusy = ref(false)
const createForm = reactive({ username: '', nickname: '', initialPassword: '', role: 'STUDENT' })

const resetOpen = ref(false)
const resetBusy = ref(false)
const resetTarget = ref(null)
const resetForm = reactive({ password: '', confirm: '' })

const seriesOpen = ref(false)
const seriesLoading = ref(false)
const seriesSaving = ref(false)
const seriesTarget = ref(null)
const seriesOptions = ref([])
const selectedSeries = ref([])

const totalPages = computed(() => Math.max(1, Math.ceil(state.value.total / state.value.size)))
const rangeStart = computed(() => state.value.total ? (state.value.page - 1) * state.value.size + 1 : 0)
const rangeEnd = computed(() => Math.min(state.value.page * state.value.size, state.value.total))

onMounted(() => {
  load()
  loadSeriesOptions()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    state.value = await listUsers({
      keyword: filters.keyword.trim() || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined,
      page: filters.page,
      size: filters.size
    })
    filters.page = state.value.page
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadSeriesOptions() {
  try {
    seriesOptions.value = await listAllSeries()
  } catch {
    seriesOptions.value = []
  }
}

function search() {
  filters.page = 1
  load()
}

function clearFilters() {
  Object.assign(filters, { keyword: '', role: '', status: '', page: 1 })
  load()
}

function goPage(page) {
  const target = Math.min(Math.max(page, 1), totalPages.value)
  if (target === filters.page) return
  filters.page = target
  load()
}

function changeSize(size) {
  filters.size = size
  filters.page = 1
  load()
}

function resetFeedback() {
  error.value = ''
  notice.value = ''
  dialogError.value = ''
}

function roleLabel(role) {
  return { ADMIN: '管理员', TEACHER: '讲师', STUDENT: '学员' }[role] || role
}

function roleTagType(role) {
  return { ADMIN: 'danger', TEACHER: 'warning', STUDENT: 'primary' }[role] || 'info'
}

function isSelf(user) {
  return Number(session.user?.id) === Number(user.id)
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function generatePassword() {
  return generateStrongPassword()
}

function openCreate() {
  resetFeedback()
  Object.assign(createForm, { username: '', nickname: '', initialPassword: generatePassword(), role: 'STUDENT' })
  createOpen.value = true
}

function closeCreate() {
  if (createBusy.value) return
  createOpen.value = false
  dialogError.value = ''
}

function beforeCreateClose(done) {
  if (createBusy.value) return
  dialogError.value = ''
  done()
}

async function submitCreate() {
  resetFeedback()
  const username = createForm.username.trim()
  const nickname = createForm.nickname.trim()
  if (!/^[a-zA-Z0-9_]{4,20}$/.test(username)) {
    dialogError.value = '用户名需为 4-20 位字母、数字或下划线'
    return
  }
  if (!nickname) {
    dialogError.value = '请输入昵称'
    return
  }
  const passwordIssue = passwordError(createForm.initialPassword)
  if (passwordIssue) {
    dialogError.value = passwordIssue
    return
  }
  createBusy.value = true
  try {
    await createUser({ username, nickname, initialPassword: createForm.initialPassword, role: createForm.role })
    createOpen.value = false
    notice.value = `账号 ${username} 已创建。初始密码：${createForm.initialPassword}（仅显示一次，首次登录须改密）`
    filters.page = 1
    await load()
  } catch (e) {
    dialogError.value = e.message
  } finally {
    createBusy.value = false
  }
}

async function toggleStatus(user) {
  if (isSelf(user)) return
  const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const verb = next === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${verb}账号 ${user.username}？`,
      `${verb}账号`,
      { type: 'warning', confirmButtonText: `确认${verb}`, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  resetFeedback()
  busy.value = `status-${user.id}`
  try {
    await changeUserStatus(user.id, next)
    notice.value = `账号 ${user.username} 已${verb}${next === 'DISABLED' ? '，旧登录凭证立即失效' : ''}`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

function openReset(user) {
  if (isSelf(user)) return
  resetFeedback()
  resetTarget.value = user
  resetForm.password = generatePassword()
  resetForm.confirm = resetForm.password
  resetOpen.value = true
}

function closeReset() {
  if (resetBusy.value) return
  resetOpen.value = false
  resetTarget.value = null
  dialogError.value = ''
}

function beforeResetClose(done) {
  if (resetBusy.value) return
  dialogError.value = ''
  done()
}

async function submitReset() {
  resetFeedback()
  const passwordIssue = passwordError(resetForm.password)
  if (passwordIssue) {
    dialogError.value = passwordIssue
    return
  }
  if (resetForm.password !== resetForm.confirm) {
    dialogError.value = '两次输入的密码不一致'
    return
  }
  resetBusy.value = true
  try {
    await resetUserPassword(resetTarget.value.id, resetForm.password)
    const username = resetTarget.value.username
    resetOpen.value = false
    notice.value = `账号 ${username} 的密码已重置。新密码：${resetForm.password}（仅显示一次，下次登录须改密）`
    await load()
  } catch (e) {
    dialogError.value = e.message
  } finally {
    resetBusy.value = false
  }
}

async function openSeries(user) {
  if (user.role !== 'STUDENT') return
  resetFeedback()
  seriesTarget.value = user
  selectedSeries.value = []
  seriesLoading.value = true
  if (!seriesOptions.value.length) await loadSeriesOptions()
  try {
    selectedSeries.value = await getUserSeries(user.id)
  } catch (e) {
    dialogError.value = e.message
  } finally {
    seriesLoading.value = false
  }
  seriesOpen.value = true
}

function closeSeries() {
  if (seriesSaving.value) return
  seriesOpen.value = false
  seriesTarget.value = null
  selectedSeries.value = []
  dialogError.value = ''
}

function beforeSeriesClose(done) {
  if (seriesSaving.value) return
  dialogError.value = ''
  done()
}

function toggleSeries(id) {
  selectedSeries.value = selectedSeries.value.includes(id)
    ? selectedSeries.value.filter((item) => item !== id)
    : [...selectedSeries.value, id]
}

async function saveSeries() {
  resetFeedback()
  seriesSaving.value = true
  try {
    await assignUserSeries(seriesTarget.value.id, selectedSeries.value)
    notice.value = `已更新 ${seriesTarget.value.username} 的系列分配`
    seriesOpen.value = false
    await load()
  } catch (e) {
    dialogError.value = e.message
  } finally {
    seriesSaving.value = false
  }
}
</script>

<template>
  <main class="page admin-page">
    <PageHeader
      eyebrow="ACCOUNT ADMINISTRATION"
      title="账号管理"
      description="管理员创建学员账号、维护账号状态，并控制学习空间可见的系列。教师账号通过邀请激活。"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建学员
        </el-button>
      </template>
    </PageHeader>

    <section class="toolbar">
      <div class="filters">
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="搜索用户名或昵称"
          clearable
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.role" class="filter-select" placeholder="全部角色" @change="search">
          <el-option label="全部角色" value="" />
          <el-option label="学员" value="STUDENT" />
          <el-option label="讲师" value="TEACHER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-select v-model="filters.status" class="filter-select" placeholder="全部状态" @change="search">
          <el-option label="全部状态" value="" />
          <el-option label="正常" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="clearFilters">重置</el-button>
      </div>
      <div class="toolbar-meta">
        <span>共 {{ state.total }} 个账号</span>
        <small>教师账号请通过“教师邀请”创建</small>
      </div>
    </section>

    <el-alert v-if="notice" type="success" show-icon class="feedback" @close="notice = ''">
      <template #title>操作已完成</template>
      <span class="notice-text">{{ notice }}</span>
    </el-alert>
    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section class="table-panel">
      <el-table v-loading="loading" :data="state.records" row-key="id" stripe>
        <el-table-column label="账号" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="34" class="user-avatar">{{ String(row.nickname || row.username).slice(0, 1) }}</el-avatar>
              <div>
                <strong>{{ row.nickname }}</strong>
                <span>{{ row.username }}<template v-if="isSelf(row)"> · 当前账号</template></span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="105">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" effect="light" round>{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="150">
          <template #default="{ row }">
            <div class="status-cell">
              <StatusTag :status="row.status" />
              <el-tag v-if="row.mustChangePassword" type="warning" effect="plain" size="small">待改密</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="系列" width="90">
          <template #default="{ row }">
            <span class="series-count">{{ row.role === 'STUDENT' ? `${row.seriesCount || 0} 个` : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="145">
          <template #default="{ row }"><span class="date-text">{{ formatDate(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="285" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button v-if="row.role === 'STUDENT'" link type="primary" :disabled="!!busy || row.status !== 'ACTIVE'" @click="openSeries(row)">分配系列</el-button>
              <el-button link type="primary" :disabled="isSelf(row) || !!busy" @click="openReset(row)">重置密码</el-button>
              <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" :disabled="isSelf(row) || !!busy" @click="toggleStatus(row)">
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的账号" />
        </template>
      </el-table>

      <footer class="pager">
        <span>显示 {{ rangeStart }}–{{ rangeEnd }}，共 {{ state.total }} 条</span>
        <el-pagination
          background
          layout="sizes, prev, pager, next"
          :total="state.total"
          :current-page="filters.page"
          :page-size="filters.size"
          :page-sizes="[10, 20, 50]"
          @current-change="goPage"
          @size-change="changeSize"
        />
      </footer>
    </section>

    <el-dialog v-model="createOpen" title="新建学员" width="560px" align-center :before-close="beforeCreateClose">
      <div class="dialog-kicker">CREATE STUDENT</div>
      <p class="hint">账号由管理员创建，不开放自助注册。初始密码只用于首次登录。</p>
      <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" show-icon class="dialog-error" />
      <el-form :model="createForm" label-position="top" @submit.prevent="submitCreate">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" autocomplete="off" placeholder="4-20 位字母、数字或下划线" autofocus />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" autocomplete="off" maxlength="20" placeholder="用于页面展示" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.initialPassword" autocomplete="new-password" minlength="8" maxlength="32">
            <template #append><el-button @click="createForm.initialPassword = generatePassword()">重新生成</el-button></template>
          </el-input>
          <span class="field-hint">用户首次登录必须修改密码。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="createBusy" @click="closeCreate">取消</el-button>
        <el-button type="primary" :loading="createBusy" @click="submitCreate">创建学员</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetOpen" :title="`重置 ${resetTarget?.username || ''} 的密码`" width="540px" align-center :before-close="beforeResetClose">
      <div class="dialog-kicker">RESET PASSWORD</div>
      <p class="hint">重置后已有登录凭证立即失效，用户下次登录必须先修改密码。</p>
      <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" show-icon class="dialog-error" />
      <el-form :model="resetForm" label-position="top" @submit.prevent="submitReset">
        <el-form-item label="新密码">
          <el-input v-model="resetForm.password" autocomplete="new-password" minlength="8" maxlength="32">
            <template #append><el-button @click="resetForm.password = generatePassword(); resetForm.confirm = resetForm.password">重新生成</el-button></template>
          </el-input>
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="resetForm.confirm" autocomplete="new-password" minlength="8" maxlength="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="resetBusy" @click="closeReset">取消</el-button>
        <el-button type="primary" :loading="resetBusy" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="seriesOpen" :title="`为 ${seriesTarget?.nickname || ''} 分配系列`" width="620px" align-center :before-close="beforeSeriesClose">
      <div class="dialog-kicker">SERIES ACCESS</div>
      <p class="hint">学员学习空间只展示这里勾选的系列，保存后立即生效。</p>
      <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" show-icon class="dialog-error" />
      <div v-loading="seriesLoading" class="series-dialog-body">
        <el-empty v-if="!seriesLoading && !seriesOptions.length" description="当前没有可分配的系列" />
        <el-checkbox-group v-else v-model="selectedSeries" class="series-options">
          <el-checkbox v-for="item in seriesOptions" :key="item.id" :value="item.id" border class="series-option">
            <span>{{ item.title }}</span>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button :disabled="seriesSaving" @click="closeSeries">取消</el-button>
        <el-button type="primary" :loading="seriesSaving" :disabled="seriesLoading" @click="saveSeries">保存分配</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.74);
}

.filters {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.keyword-input {
  width: 250px;
}

.filter-select {
  width: 126px;
}

.toolbar-meta {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
  color: var(--muted);
  font-size: 12px;
}

.toolbar-meta small {
  font-size: 10px;
}

.feedback {
  margin-bottom: 14px;
}

.notice-text {
  line-height: 1.65;
  word-break: break-all;
}

.table-panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.user-cell,
.status-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  flex: none;
  background: var(--pine-soft);
  color: var(--pine);
  font-family: var(--font-display);
  font-weight: 700;
}

.user-cell > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.user-cell strong {
  font-size: 13px;
}

.user-cell span {
  color: var(--muted);
  font-size: 11px;
}

.series-count,
.date-text {
  color: var(--muted);
  font-size: 12px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 16px;
  border-top: 1px solid var(--border);
}

.pager > span {
  color: var(--muted);
  font-size: 11px;
}

.dialog-kicker {
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.hint {
  margin: 8px 0 20px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.65;
}

.dialog-error {
  margin-bottom: 16px;
}

.field-hint {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  font-size: 11px;
}

.series-dialog-body {
  min-height: 120px;
}

.series-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.series-option {
  width: 100%;
  height: auto;
  margin: 0 !important;
  padding: 12px 14px;
}

.series-option :deep(.el-checkbox__label) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 940px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-meta {
    align-items: flex-start;
  }
}

@media (max-width: 720px) {
  .filters {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .keyword-input {
    width: 100%;
  }

  .filter-select {
    width: calc(50% - 5px);
  }

  .pager {
    align-items: flex-start;
    flex-direction: column;
  }

  .series-options {
    grid-template-columns: 1fr;
  }
}
</style>
