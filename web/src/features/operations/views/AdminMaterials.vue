<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createAdminMaterial,
  listAdminMaterials,
  setMaterialFeatured,
  setMaterialSaleStatus,
  updateAdminMaterial,
  uploadMaterialFile
} from '@/features/operations/api/material'
import PageHeader from '@/components/PageHeader.vue'

const CATEGORIES = [
  { value: 'EXAM_PAPER', label: '试卷' },
  { value: 'TEACHING_AID', label: '教辅' },
  { value: 'LECTURE_NOTES', label: '讲义' },
  { value: 'TEXTBOOK', label: '课本' }
]
const STAGES = [
  { value: 'PRIMARY', label: '小学' },
  { value: 'JUNIOR', label: '初中' },
  { value: 'SENIOR', label: '高中' },
  { value: 'GENERAL', label: '通用' }
]

const filters = reactive({ keyword: '', category: '', saleStatus: '', page: 1, size: 10 })
const state = ref({ records: [], total: 0, page: 1, size: 10 })
const loading = ref(true)
const error = ref('')
const notice = ref('')
const dialogError = ref('')
const busy = ref('')

const editorOpen = ref(false)
const editorBusy = ref(false)
const editorTarget = ref(null)
const form = reactive({
  title: '', category: 'EXAM_PAPER', stage: 'SENIOR', subject: '',
  summary: '', priceYuan: 9.9, pageCount: 20, previewPages: 3
})

const uploadInput = ref(null)
const uploadTarget = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil(state.value.total / state.value.size)))
const rangeStart = computed(() => state.value.total ? (state.value.page - 1) * state.value.size + 1 : 0)
const rangeEnd = computed(() => Math.min(state.value.page * state.value.size, state.value.total))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    state.value = await listAdminMaterials({
      keyword: filters.keyword.trim() || undefined,
      category: filters.category || undefined,
      saleStatus: filters.saleStatus || undefined,
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
  Object.assign(filters, { keyword: '', category: '', saleStatus: '', page: 1 })
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

function categoryLabel(value) {
  return CATEGORIES.find((c) => c.value === value)?.label || value
}

function stageLabel(value) {
  return STAGES.find((s) => s.value === value)?.label || value || '—'
}

function yuan(cents) {
  return `¥${(cents / 100).toFixed(2)}`
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function openCreate() {
  error.value = ''
  notice.value = ''
  dialogError.value = ''
  editorTarget.value = null
  Object.assign(form, {
    title: '', category: 'EXAM_PAPER', stage: 'SENIOR', subject: '',
    summary: '', priceYuan: 9.9, pageCount: 20, previewPages: 3
  })
  editorOpen.value = true
}

function openEdit(row) {
  error.value = ''
  notice.value = ''
  dialogError.value = ''
  editorTarget.value = row
  Object.assign(form, {
    title: row.title,
    category: row.category,
    stage: row.stage || 'GENERAL',
    subject: row.subject || '',
    summary: row.summary || '',
    priceYuan: row.priceCents / 100,
    pageCount: row.pageCount,
    previewPages: row.previewPages
  })
  editorOpen.value = true
}

function closeEditor() {
  if (editorBusy.value) return
  editorOpen.value = false
  dialogError.value = ''
}

function beforeEditorClose(done) {
  if (editorBusy.value) return
  dialogError.value = ''
  done()
}

async function submitEditor() {
  dialogError.value = ''
  const title = form.title.trim()
  if (!title) {
    dialogError.value = '请输入标题'
    return
  }
  if (!(form.priceYuan > 0)) {
    dialogError.value = '价格必须大于 0'
    return
  }
  if (!Number.isInteger(form.pageCount) || form.pageCount < 1) {
    dialogError.value = '总页数至少为 1'
    return
  }
  if (!Number.isInteger(form.previewPages) || form.previewPages < 1 || form.previewPages > form.pageCount) {
    dialogError.value = '试读页数需在 1 到总页数之间'
    return
  }
  const body = {
    title,
    category: form.category,
    stage: form.stage,
    subject: form.subject.trim(),
    summary: form.summary.trim(),
    priceCents: Math.round(form.priceYuan * 100),
    pageCount: form.pageCount,
    previewPages: form.previewPages
  }
  editorBusy.value = true
  try {
    if (editorTarget.value) {
      await updateAdminMaterial(editorTarget.value.id, body)
      notice.value = `资料「${title}」已更新`
    } else {
      await createAdminMaterial(body)
      notice.value = `资料「${title}」已创建，上传 PDF 后方可上架`
    }
    editorOpen.value = false
    await load()
  } catch (e) {
    dialogError.value = e.message
  } finally {
    editorBusy.value = false
  }
}

async function toggleSale(row) {
  const next = row.saleStatus === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  error.value = ''
  notice.value = ''
  busy.value = `sale-${row.id}`
  try {
    await setMaterialSaleStatus(row.id, next)
    notice.value = `资料「${row.title}」已${next === 'ON_SALE' ? '上架' : '下架'}`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function toggleFeatured(row) {
  error.value = ''
  notice.value = ''
  busy.value = `featured-${row.id}`
  try {
    await setMaterialFeatured(row.id, !row.featured)
    notice.value = `资料「${row.title}」${row.featured ? '已取消推荐' : '已设为首页推荐'}`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

function pickFile(row) {
  uploadTarget.value = row
  uploadInput.value.value = ''
  uploadInput.value.click()
}

async function onFileChosen(event) {
  const file = event.target.files?.[0]
  const row = uploadTarget.value
  if (!file || !row) return
  error.value = ''
  notice.value = ''
  busy.value = `upload-${row.id}`
  try {
    await uploadMaterialFile(row.id, file)
    notice.value = `资料「${row.title}」的文件已替换为 ${file.name}`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
    uploadTarget.value = null
  }
}
</script>

<template>
  <main class="page admin-page">
    <PageHeader
      eyebrow="MATERIAL OPERATIONS"
      title="资料管理"
      description="维护电子资料目录：新建/编辑、上传 PDF、上下架与首页推荐位。上架前必须已挂文件且价格大于 0。"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建资料
        </el-button>
      </template>
    </PageHeader>

    <section class="toolbar">
      <div class="filters">
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="搜索标题"
          clearable
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.category" class="filter-select" placeholder="全部分类" @change="search">
          <el-option label="全部分类" value="" />
          <el-option v-for="item in CATEGORIES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.saleStatus" class="filter-select" placeholder="全部状态" @change="search">
          <el-option label="全部状态" value="" />
          <el-option label="在售" value="ON_SALE" />
          <el-option label="下架" value="OFF_SALE" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="clearFilters">重置</el-button>
      </div>
      <div class="toolbar-meta">
        <span>共 {{ state.total }} 份资料</span>
        <small>演示文件缺失时由系统自动生成示例 PDF</small>
      </div>
    </section>

    <el-alert v-if="notice" type="success" show-icon class="feedback" @close="notice = ''">
      <template #title>操作已完成</template>
      <span class="notice-text">{{ notice }}</span>
    </el-alert>
    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section class="table-panel">
      <el-table v-loading="loading" :data="state.records" row-key="id" stripe>
        <el-table-column label="资料" min-width="240">
          <template #default="{ row }">
            <div class="material-cell">
              <strong>{{ row.title }}</strong>
              <span>{{ stageLabel(row.stage) }} · {{ row.subject || '未标学科' }} · {{ row.pageCount }} 页 / 试读 {{ row.previewPages }} 页</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="90">
          <template #default="{ row }">
            <el-tag effect="light" round>{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="价格" width="100">
          <template #default="{ row }"><span class="price-text">{{ yuan(row.priceCents) }}</span></template>
        </el-table-column>
        <el-table-column label="文件" width="110">
          <template #default="{ row }">
            <el-tag :type="row.fileKey ? 'success' : 'info'" effect="plain" size="small">
              {{ row.fileKey ? '已挂文件' : '未挂文件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.saleStatus === 'ON_SALE'"
              :loading="busy === `sale-${row.id}`"
              :disabled="!!busy"
              @change="toggleSale(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="推荐位" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.featured"
              :loading="busy === `featured-${row.id}`"
              :disabled="!!busy"
              @change="toggleFeatured(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="145">
          <template #default="{ row }"><span class="date-text">{{ formatDate(row.updatedAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" :disabled="!!busy" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" :loading="busy === `upload-${row.id}`" :disabled="!!busy" @click="pickFile(row)">
                上传 PDF
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有符合条件的资料" />
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

    <input ref="uploadInput" type="file" accept="application/pdf,.pdf" class="hidden-input" @change="onFileChosen">

    <el-dialog
      v-model="editorOpen"
      :title="editorTarget ? `编辑「${editorTarget.title}」` : '新建资料'"
      width="620px"
      align-center
      :before-close="beforeEditorClose"
    >
      <div class="dialog-kicker">{{ editorTarget ? 'EDIT MATERIAL' : 'CREATE MATERIAL' }}</div>
      <p class="hint">新建资料默认下架且不挂文件；上传 PDF 并设置价格后才能上架。金额按元填写，保存时换算为分。</p>
      <el-alert v-if="dialogError" :title="dialogError" type="error" :closable="false" show-icon class="dialog-error" />
      <el-form :model="form" label-position="top" @submit.prevent="submitEditor">
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="120" placeholder="例如：高中数学函数专项突破试卷（含详解）" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="分类">
            <el-select v-model="form.category">
              <el-option v-for="item in CATEGORIES" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="学段">
            <el-select v-model="form.stage">
              <el-option v-for="item in STAGES" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="学科">
            <el-input v-model="form.subject" maxlength="30" placeholder="数学 / 英语 / 物理…" />
          </el-form-item>
          <el-form-item label="价格（元）">
            <el-input-number v-model="form.priceYuan" :min="0.01" :step="1" :precision="2" controls-position="right" />
          </el-form-item>
          <el-form-item label="总页数">
            <el-input-number v-model="form.pageCount" :min="1" :step="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="试读页数">
            <el-input-number v-model="form.previewPages" :min="1" :max="form.pageCount" :step="1" controls-position="right" />
          </el-form-item>
        </div>
        <el-form-item label="简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="目录页展示的卖点简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="editorBusy" @click="closeEditor">取消</el-button>
        <el-button type="primary" :loading="editorBusy" @click="submitEditor">
          {{ editorTarget ? '保存修改' : '创建资料' }}
        </el-button>
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
  width: 230px;
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

.material-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.material-cell strong {
  font-size: 13px;
}

.material-cell span {
  color: var(--muted);
  font-size: 11px;
}

.price-text {
  font-family: var(--font-mono);
  font-size: 13px;
}

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

.hidden-input {
  display: none;
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 14px;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-input-number) {
  width: 100%;
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

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>