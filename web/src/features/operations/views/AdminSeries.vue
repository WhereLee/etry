<script setup>
import { onMounted, reactive, ref } from 'vue'
import {
  getAdminSeriesContent,
  listAdminSeriesContent,
  updateAdminSeriesStatus
} from '@/features/operations/api/admin'
import { updateSeriesSale } from '@/features/commerce/api/order'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const filters = reactive({ keyword: '', status: '', page: 1, size: 20 })
const pageData = ref({ records: [], total: 0, page: 1, size: 20 })
const loading = ref(true)
const error = ref('')
const notice = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const actionTarget = ref(null)
const actionStatus = ref('')
const actionBusy = ref(false)
const saleOpen = ref(false)
const saleBusy = ref(false)
const saleTarget = ref(null)
const saleForm = reactive({ saleStatus: 'OFF_SALE', priceYuan: '0.00' })

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    pageData.value = await listAdminSeriesContent({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      page: filters.page,
      size: filters.size
    })
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
  Object.assign(filters, { keyword: '', status: '', page: 1 })
  load()
}

function goPage(next) {
  const totalPages = Math.max(1, Math.ceil(pageData.value.total / pageData.value.size))
  const target = Math.min(Math.max(next, 1), totalPages)
  if (target === filters.page) return
  filters.page = target
  load()
}

function changeSize(size) {
  filters.size = size
  filters.page = 1
  load()
}

async function openDetail(series) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  error.value = ''
  try {
    detail.value = await getAdminSeriesContent(series.id)
  } catch (e) {
    error.value = e.message
  } finally {
    detailLoading.value = false
  }
}

function askStatus(series, status) {
  error.value = ''
  notice.value = ''
  actionTarget.value = series
  actionStatus.value = status
}

function setActionDialogOpen(open) {
  if (!open) actionTarget.value = null
}

async function applyStatus() {
  if (!actionTarget.value) return
  actionBusy.value = true
  try {
    await updateAdminSeriesStatus(actionTarget.value.id, actionStatus.value)
    notice.value = `已${actionStatus.value === 'ARCHIVED' ? '归档' : '下架'}系列“${actionTarget.value.title}”`
    actionTarget.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    actionBusy.value = false
  }
}

function mediaLabel(status) {
  if (status === 'READY') return '已就绪'
  if (status === 'UPLOADING') return '待确认'
  return '未上传'
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function formatSize(bytes) {
  if (!bytes) return '—'
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}

function priceText(cents, currency = 'CNY') {
  if (!cents) return '未定价'
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(cents / 100)
}

function openSale(series) {
  saleTarget.value = series
  saleForm.saleStatus = series.saleStatus || 'OFF_SALE'
  saleForm.priceYuan = ((series.priceCents || 0) / 100).toFixed(2)
  saleOpen.value = true
}

async function submitSale() {
  const yuan = Number(saleForm.priceYuan)
  if (!Number.isFinite(yuan) || yuan < 0) {
    error.value = '请输入有效价格'
    return
  }
  const priceCents = Math.round(yuan * 100)
  if (saleForm.saleStatus === 'ON_SALE' && priceCents <= 0) {
    error.value = '开启销售时价格必须大于 0'
    return
  }
  saleBusy.value = true
  error.value = ''
  try {
    await updateSeriesSale(saleTarget.value.id, saleForm.saleStatus, priceCents)
    notice.value = saleForm.saleStatus === 'ON_SALE'
      ? `系列“${saleTarget.value.title}”已开启销售`
      : `系列“${saleTarget.value.title}”已停止销售`
    saleOpen.value = false
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saleBusy.value = false
  }
}
</script>
<template>
  <main class="page admin-series-page">
    <PageHeader
      eyebrow="CONTENT GOVERNANCE"
      title="内容管理"
      description="查看系列、销售状态和分配情况。下架或归档会停止新销售，已获得权益的学员仍可访问。"
    />

    <section class="toolbar">
      <div class="filters">
        <el-input v-model="filters.keyword" class="keyword-input" placeholder="搜索系列或教师" clearable @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.status" class="status-select" placeholder="全部状态" @change="search">
          <el-option label="全部状态" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="clearFilters">重置</el-button>
      </div>
      <span class="total-count">共 {{ pageData.total }} 个系列</span>
    </section>

    <el-alert v-if="notice" type="success" show-icon class="feedback" @close="notice = ''" :title="notice" />
    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section class="list-panel">
      <el-table v-loading="loading" :data="pageData.records" row-key="id" stripe>
        <el-table-column label="系列" min-width="230">
          <template #default="{ row }">
            <div class="series-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ row.summary || '暂无简介' }}</span>
              <span class="series-meta">{{ row.chapterCount }} 章 · {{ row.videoCount }} 节 · {{ row.assignedStudentCount }} 人已分配</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="教师" min-width="110">
          <template #default="{ row }"><span class="teacher-name">{{ row.teacherName }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="销售" min-width="145">
          <template #default="{ row }">
            <div class="sale-cell">
              <StatusTag :status="row.saleStatus" />
              <span>{{ priceText(row.priceCents, row.currency) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="145">
          <template #default="{ row }"><span class="muted-value">{{ formatDate(row.updatedAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="openDetail(row)">查看</el-button>
              <el-button link type="primary" @click="openSale(row)">销售</el-button>
              <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="askStatus(row, 'DRAFT')">下架</el-button>
              <el-button v-if="row.status !== 'ARCHIVED'" link type="danger" @click="askStatus(row, 'ARCHIVED')">归档</el-button>
              <span v-if="row.status === 'ARCHIVED'" class="archived-mark">只读</span>
            </div>
          </template>
        </el-table-column>
        <template #empty><el-empty description="没有符合条件的系列" /></template>
      </el-table>

      <footer class="pager">
        <span>显示第 {{ pageData.page }} / {{ Math.max(1, Math.ceil(pageData.total / pageData.size)) }} 页</span>
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

    <el-dialog v-model="detailOpen" :title="detail?.title || '系列详情'" width="760px" align-center>
      <div class="dialog-kicker">SERIES DETAIL</div>
      <div v-if="detailLoading" class="dialog-loading"><el-skeleton animated :rows="7" /></div>
      <template v-else-if="detail">
        <p class="detail-summary">{{ detail.summary || '暂无简介' }}</p>
        <div class="detail-meta">
          <StatusTag :status="detail.status" />
          <span>{{ detail.teacherName }}</span>
          <span>更新于 {{ formatDate(detail.updatedAt) }}</span>
        </div>
        <el-empty v-if="!detail.chapters.length" description="该系列还没有章节" />
        <article v-for="(chapter, index) in detail.chapters" :key="chapter.id" class="detail-chapter">
          <div class="chapter-title">
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <h3>{{ chapter.title }}</h3>
            <small>{{ chapter.videos.length }} 节</small>
          </div>
          <div v-if="!chapter.videos.length" class="chapter-empty">本章暂无视频</div>
          <div v-for="video in chapter.videos" :key="video.id" class="detail-video">
            <span class="video-title">{{ video.title }}</span>
            <el-tag :type="video.mediaStatus === 'READY' ? 'success' : 'info'" effect="plain" size="small">{{ mediaLabel(video.mediaStatus) }}</el-tag>
            <span>{{ Math.floor(video.durationSeconds / 60) }}:{{ String(video.durationSeconds % 60).padStart(2, '0') }}</span>
            <span>{{ formatSize(video.fileSizeBytes) }}</span>
          </div>
        </article>
      </template>
    </el-dialog>

    <el-dialog v-model="saleOpen" title="销售设置" width="480px" align-center>
      <div class="dialog-kicker">SALE SETTINGS</div>
      <p class="sale-hint">{{ saleTarget?.title }} · 只有已发布系列可以开启销售。</p>
      <el-form label-position="top">
        <el-form-item label="销售状态">
          <el-radio-group v-model="saleForm.saleStatus">
            <el-radio-button value="OFF_SALE">停止销售</el-radio-button>
            <el-radio-button value="ON_SALE" :disabled="saleTarget?.status !== 'PUBLISHED'">开启销售</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="课程价格（元）">
          <el-input v-model="saleForm.priceYuan" type="number" min="0" step="0.01" placeholder="例如：2.99">
            <template #prepend>¥</template>
          </el-input>
          <span class="sale-hint">价格变更只影响之后创建的订单，历史订单保留价格快照。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saleBusy" @click="saleOpen = false">取消</el-button>
        <el-button type="primary" :loading="saleBusy" @click="submitSale">保存销售设置</el-button>
      </template>
    </el-dialog>

    <el-dialog :model-value="!!actionTarget" :title="`${actionStatus === 'ARCHIVED' ? '归档' : '下架'}系列`" width="430px" align-center @update:model-value="setActionDialogOpen">
      <div class="dialog-kicker">{{ actionStatus === 'ARCHIVED' ? 'ARCHIVE SERIES' : 'UNPUBLISH SERIES' }}</div>
      <p class="confirm-copy">确认对“{{ actionTarget?.title }}”执行{{ actionStatus === 'ARCHIVED' ? '归档' : '下架为草稿' }}？新销售将停止，已拥有权益的学员仍可访问。</p>
      <template #footer>
        <el-button :disabled="actionBusy" @click="actionTarget = null">取消</el-button>
        <el-button type="primary" :loading="actionBusy" @click="applyStatus">确认</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
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
}

.keyword-input {
  width: 260px;
}

.status-select {
  width: 132px;
}

.total-count {
  flex: none;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.feedback {
  margin-bottom: 14px;
}

.list-panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.series-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.series-cell strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.series-cell span {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.series-cell .series-meta {
  color: #8d6b41;
  font-family: var(--font-mono);
  font-size: 9px;
}

.teacher-name {
  font-size: 12px;
}

.sale-cell {
  display: flex;
  align-items: center;
  gap: 7px;
}

.sale-cell > span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.muted-value {
  color: var(--muted);
  font-size: 12px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.archived-mark {
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

.dialog-loading {
  padding: 18px 0;
}

.detail-summary {
  margin: 10px 0 14px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 9px;
  margin-bottom: 20px;
  color: var(--muted);
  font-size: 11px;
}

.detail-chapter {
  padding-top: 15px;
  border-top: 1px solid var(--border);
}

.chapter-title {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 7px;
}

.chapter-title > span {
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 9px;
}

.chapter-title h3 {
  flex: 1;
  margin: 0;
  font-family: var(--font-display);
  font-size: 16px;
}

.chapter-title small {
  color: var(--muted);
  font-size: 10px;
}

.detail-video {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 52px 62px;
  gap: 12px;
  align-items: center;
  padding: 9px 0 9px 18px;
  border-top: 1px dashed #e7e2d8;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.video-title {
  overflow: hidden;
  color: var(--text);
  font-family: var(--font-body);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-empty {
  padding: 12px 0 12px 18px;
  color: var(--muted);
  font-size: 11px;
}

.confirm-copy {
  margin: 12px 0 0;
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.7;
}

.sale-hint {
  display: block;
  margin: 8px 0 16px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.65;
}

@media (max-width: 760px) {
  .toolbar,
  .pager {
    align-items: stretch;
    flex-direction: column;
  }

  .filters {
    flex-wrap: wrap;
  }

  .keyword-input {
    width: 100%;
  }

  .status-select {
    width: calc(100% - 132px);
  }

  .detail-video {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .detail-video > span:nth-last-child(-n + 2) {
    display: none;
  }
}
</style>
