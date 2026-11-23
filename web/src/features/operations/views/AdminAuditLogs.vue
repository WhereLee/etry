<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listAuditLogs } from '@/features/operations/api/audit'
import PageHeader from '@/components/PageHeader.vue'

const ACTION_LABELS = {
  USER_CREATE: '创建账号',
  USER_STATUS_CHANGE: '账号启停',
  USER_PASSWORD_RESET: '重置密码',
  USER_SERIES_ASSIGN: '分配系列',
  USER_ROLES_ASSIGN: '调整角色',
  MATERIAL_CREATE: '新建资料',
  MATERIAL_UPDATE: '编辑资料',
  MATERIAL_SALE_STATUS: '资料上下架',
  MATERIAL_FEATURED: '推荐位调整',
  MATERIAL_UPLOAD: '上传文件',
  ORDER_QUERY: '渠道查单',
  ORDER_FULFILL: '补履约',
  ORDER_CLOSE: '关单',
  ORDER_REFUND: '退款',
  ORDER_DUPLICATE_REFUND: '重复退款',
  RECON_STATEMENT_UPLOAD: '上传对账单',
  RECON_RUN: '执行对账',
  RECON_BREAK_ACTION: '差异处理',
  ADMIN_SERIES_STATUS: '系列状态',
  ADMIN_SERIES_SALE: '系列售卖',
  TEACHER_INVITE_CREATE: '教师邀请',
  TEACHER_INVITE_REVOKE: '撤销邀请'
}

const filters = reactive({ operatorId: '', action: '', targetType: '', success: '', page: 1, size: 20 })
const state = ref({ records: [], total: 0, page: 1, size: 20 })
const loading = ref(true)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(state.value.total / state.value.size)))
const rangeStart = computed(() => state.value.total ? (state.value.page - 1) * state.value.size + 1 : 0)
const rangeEnd = computed(() => Math.min(state.value.page * state.value.size, state.value.total))
const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label: `${label}（${value}）` }))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const operatorId = filters.operatorId.trim()
    state.value = await listAuditLogs({
      operatorId: /^\d+$/.test(operatorId) ? operatorId : undefined,
      action: filters.action.trim() || undefined,
      targetType: filters.targetType.trim() || undefined,
      success: filters.success === '' ? undefined : filters.success === 'true',
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

function search() {
  filters.page = 1
  load()
}

function clearFilters() {
  Object.assign(filters, { operatorId: '', action: '', targetType: '', success: '', page: 1 })
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

function actionLabel(action) {
  return ACTION_LABELS[action] || action
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <main class="page admin-page">
    <PageHeader
      eyebrow="AUDIT TRAIL"
      title="审计日志"
      description="记录后台写操作的操作人、目标、结果与耗时，失败尝试同样留痕，可按操作人、动作与结果筛选。"
    />

    <section class="toolbar">
      <div class="filters">
        <el-input v-model="filters.operatorId" class="operator-input" placeholder="操作人ID" clearable @keyup.enter="search" @clear="search" />
        <el-select
          v-model="filters.action"
          class="action-select"
          placeholder="全部动作"
          filterable
          clearable
          allow-create
          default-first-option
          @change="search"
        >
          <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="filters.targetType" class="target-input" placeholder="目标类型 如 ORDER" clearable @keyup.enter="search" @clear="search" />
        <el-select v-model="filters.success" class="success-select" placeholder="全部结果" @change="search">
          <el-option label="全部结果" value="" />
          <el-option label="成功" value="true" />
          <el-option label="失败" value="false" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="clearFilters">重置</el-button>
      </div>
      <div class="toolbar-meta">
        <span>共 {{ state.total }} 条记录</span>
      </div>
    </section>

    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section class="table-panel">
      <el-table v-loading="loading" :data="state.records" row-key="id" stripe>
        <el-table-column label="ID" prop="id" width="80" />
        <el-table-column label="操作人" width="90">
          <template #default="{ row }"><span class="mono-text">#{{ row.operatorId }}</span></template>
        </el-table-column>
        <el-table-column label="动作" min-width="180">
          <template #default="{ row }">
            <div class="action-cell">
              <strong>{{ actionLabel(row.action) }}</strong>
              <span class="mono-text">{{ row.action }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="目标" min-width="130">
          <template #default="{ row }">
            <span class="mono-text">{{ row.targetType }}<template v-if="row.targetId != null">#{{ row.targetId }}</template></span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" effect="light" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }"><span class="mono-text">{{ row.durationMs }} ms</span></template>
        </el-table-column>
        <el-table-column label="错误信息" min-width="180">
          <template #default="{ row }">
            <el-tooltip v-if="row.errorMessage" :content="row.errorMessage" placement="top">
              <span class="error-text">{{ row.errorMessage }}</span>
            </el-tooltip>
            <span v-else class="mono-text">—</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }"><span class="mono-text">{{ formatDate(row.createdAt) }}</span></template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的审计记录" />
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

.operator-input {
  width: 110px;
}

.action-select {
  width: 230px;
}

.target-input {
  width: 160px;
}

.success-select {
  width: 116px;
}

.toolbar-meta {
  flex: none;
  color: var(--muted);
  font-size: 12px;
}

.feedback {
  margin-bottom: 14px;
}

.table-panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.action-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.action-cell strong {
  font-size: 13px;
}

.mono-text {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.error-text {
  display: inline-block;
  overflow: hidden;
  max-width: 240px;
  color: var(--danger, #c45656);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
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

@media (max-width: 940px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .filters {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .operator-input,
  .action-select,
  .target-input,
  .success-select {
    width: calc(50% - 5px);
  }

  .pager {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>