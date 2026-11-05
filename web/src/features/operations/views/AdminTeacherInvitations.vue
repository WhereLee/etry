<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  createTeacherInvitation,
  listTeacherInvitations,
  revokeTeacherInvitation
} from '@/features/identity/api/invitation'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const form = reactive({ username: '', nickname: '' })
const createBusy = ref(false)
const loading = ref(true)
const error = ref('')
const notice = ref('')
const created = ref(null)
const busyAction = ref('')
const pendingRevoke = ref(null)
const status = ref('')
const pageData = ref({ records: [], total: 0, page: 1, size: 20 })

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    pageData.value = await listTeacherInvitations({
      status: status.value || undefined,
      page: pageData.value.page,
      size: pageData.value.size
    })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function create() {
  error.value = ''
  notice.value = ''
  const username = form.username.trim()
  const nickname = form.nickname.trim()
  if (!/^[a-zA-Z0-9_]{4,20}$/.test(username)) {
    error.value = '用户名需为 4-20 位字母、数字或下划线'
    return
  }
  if (!nickname) {
    error.value = '请输入教师昵称'
    return
  }
  createBusy.value = true
  try {
    created.value = await createTeacherInvitation({ username, nickname })
    notice.value = '教师邀请已生成，请立即复制链接并通过内部渠道发送。'
    form.username = ''
    form.nickname = ''
    pageData.value.page = 1
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    createBusy.value = false
  }
}

async function copyLink() {
  if (!created.value?.inviteUrl) return
  const text = created.value.inviteUrl
  try {
    await navigator.clipboard.writeText(text)
    notice.value = '邀请链接已复制。'
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const copied = document.execCommand('copy')
    textarea.remove()
    notice.value = copied ? '邀请链接已复制。' : '自动复制失败，请手动选择链接复制。'
  }
}

function askRevoke(invitation) {
  pendingRevoke.value = invitation
}

function setRevokeOpen(open) {
  if (!open) pendingRevoke.value = null
}

async function revoke() {
  const invitation = pendingRevoke.value
  if (!invitation) return
  error.value = ''
  notice.value = ''
  busyAction.value = `revoke-${invitation.id}`
  try {
    await revokeTeacherInvitation(invitation.id)
    pendingRevoke.value = null
    notice.value = `已撤销 ${invitation.username} 的教师邀请。`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busyAction.value = ''
  }
}

function changeStatus() {
  pageData.value.page = 1
  load()
}

function goPage(next) {
  const totalPages = Math.max(1, Math.ceil(pageData.value.total / pageData.value.size))
  const target = Math.min(Math.max(next, 1), totalPages)
  if (target === pageData.value.page) return
  pageData.value.page = target
  load()
}

function changeSize(size) {
  pageData.value.size = size
  pageData.value.page = 1
  load()
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}
</script>

<template>
  <main class="page invitation-page">
    <PageHeader
      eyebrow="TEACHER ONBOARDING"
      title="教师邀请"
      description="管理员生成一次性邀请链接，教师设置密码后激活账号。链接只在生成时展示一次。"
    />

    <el-alert v-if="notice" type="success" show-icon class="feedback" @close="notice = ''" :title="notice" />
    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section class="create-panel">
      <div class="section-copy">
        <span class="section-index">01</span>
        <h2>生成邀请</h2>
        <p>用户名和昵称会绑定到邀请，教师不能通过链接改变角色。默认有效期 72 小时。</p>
      </div>
      <el-form :model="form" label-position="top" class="create-form" @submit.prevent="create">
        <el-form-item label="用户名">
          <el-input v-model.trim="form.username" autocomplete="off" placeholder="4-20 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="教师昵称">
          <el-input v-model.trim="form.nickname" autocomplete="off" maxlength="20" placeholder="例如：物理老师" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="createBusy" class="create-button">生成邀请链接</el-button>
      </el-form>

      <div v-if="created" class="created-box">
        <div class="created-copy">
          <span>NEW INVITATION</span>
          <strong>{{ created.nickname }}（{{ created.username }}）</strong>
          <small>链接只显示这一次，有效期至 {{ formatDate(created.expiresAt) }}。</small>
        </div>
        <el-input :model-value="created.inviteUrl" readonly class="invite-link" />
        <el-button type="primary" @click="copyLink">
          <el-icon><CopyDocument /></el-icon>
          复制链接
        </el-button>
      </div>
    </section>

    <section class="list-panel">
      <div class="list-head">
        <div>
          <span class="section-index">02</span>
          <div>
            <h2>邀请记录</h2>
            <p>已生成的明文链接不可再次查看，只能撤销后重新生成。</p>
          </div>
        </div>
        <el-select v-model="status" class="status-filter" placeholder="全部状态" @change="changeStatus">
          <el-option label="全部状态" value="" />
          <el-option label="待使用" value="PENDING" />
          <el-option label="已使用" value="USED" />
          <el-option label="已撤销" value="REVOKED" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="pageData.records" row-key="id" stripe>
        <el-table-column label="教师" min-width="180">
          <template #default="{ row }">
            <div class="teacher-cell">
              <strong>{{ row.nickname }}</strong>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.expired ? 'EXPIRED' : row.status" /></template>
        </el-table-column>
        <el-table-column label="创建人" min-width="120" prop="createdByName" />
        <el-table-column label="创建时间" min-width="145">
          <template #default="{ row }"><span class="date-text">{{ formatDate(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="145">
          <template #default="{ row }"><span class="date-text">{{ formatDate(row.expiresAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING' && !row.expired" link type="danger" :disabled="!!busyAction" @click="askRevoke(row)">撤销</el-button>
            <span v-else class="muted-text">—</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无邀请记录" /></template>
      </el-table>

      <footer class="pager">
        <span>共 {{ pageData.total }} 条，第 {{ pageData.page }} 页</span>
        <el-pagination
          background
          layout="sizes, prev, pager, next"
          :total="pageData.total"
          :current-page="pageData.page"
          :page-size="pageData.size"
          :page-sizes="[10, 20, 50]"
          @current-change="goPage"
          @size-change="changeSize"
        />
      </footer>
    </section>

    <el-dialog :model-value="!!pendingRevoke" title="撤销教师邀请" width="430px" align-center @update:model-value="setRevokeOpen">
      <div class="dialog-kicker">REVOKE INVITATION</div>
      <p class="confirm-copy">确认撤销 {{ pendingRevoke?.nickname }}（{{ pendingRevoke?.username }}）的邀请？撤销后链接立即失效。</p>
      <template #footer>
        <el-button :disabled="!!busyAction" @click="pendingRevoke = null">取消</el-button>
        <el-button type="danger" :loading="!!busyAction" @click="revoke">确认撤销</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.feedback {
  margin-bottom: 14px;
}

.create-panel {
  display: grid;
  grid-template-columns: minmax(220px, 0.72fr) minmax(0, 1.28fr);
  gap: 28px;
  margin-bottom: 18px;
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.section-copy {
  padding-right: 24px;
  border-right: 1px solid var(--border);
}

.section-index {
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 10px;
}

.section-copy h2,
.list-head h2 {
  margin: 8px 0 7px;
  font-family: var(--font-display);
  font-size: 21px;
  letter-spacing: -0.025em;
}

.section-copy p,
.list-head p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.create-form {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  align-items: end;
  gap: 10px;
}

.create-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.create-button {
  margin-bottom: 1px;
}

.created-box {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: minmax(180px, 0.7fr) minmax(0, 1.3fr) auto;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid #c8dfd4;
  border-radius: var(--radius-sm);
  background: #f5faf7;
}

.created-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.created-copy > span {
  color: var(--pine);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.created-copy strong {
  font-size: 13px;
}

.created-copy small {
  color: var(--muted);
  font-size: 10px;
}

.invite-link :deep(.el-input__inner) {
  font-family: var(--font-mono);
  font-size: 11px;
}

.list-panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border);
}

.list-head > div {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.list-head h2 {
  margin-top: 2px;
}

.status-filter {
  width: 132px;
}

.teacher-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.teacher-cell strong {
  font-size: 13px;
}

.teacher-cell span,
.date-text,
.muted-text {
  color: var(--muted);
  font-size: 11px;
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

.confirm-copy {
  margin: 12px 0 0;
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .create-panel {
    grid-template-columns: 1fr;
  }

  .section-copy {
    padding: 0 0 18px;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }

  .created-box {
    grid-template-columns: 1fr auto;
  }

  .created-copy {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .create-form {
    grid-template-columns: 1fr;
  }

  .create-button {
    width: 100%;
  }

  .created-box,
  .list-head,
  .pager {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .status-filter {
    width: 100%;
  }
}
</style>
