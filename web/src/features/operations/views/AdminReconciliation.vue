<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  actOnBreak,
  executeReconciliation,
  listBreaks,
  listRuns,
  listStatements,
  uploadStatement
} from '@/features/operations/api/reconciliation'
import PageHeader from '@/components/PageHeader.vue'
import { download } from '@/shared/api/http'
import { hasPermission } from '@/store/session'

const canOperate = computed(() => hasPermission('recon:operate'))
const loading = ref(false)
const uploadBusy = ref(false)
const runBusy = ref(false)
const exportBusy = ref(false)
const error = ref('')
const notice = ref('')
const pageData = ref({ records: [], total: 0, page: 1, size: 20 })
const filters = reactive({ page: 1, size: 20 })
const uploadForm = reactive({ provider: 'ALIPAY', statementDate: '', file: null })
const selected = ref(null)
const runs = ref([])
const activeRun = ref(null)
const breaks = ref({ records: [], total: 0, page: 1, size: 20 })
const breakStatus = ref('')
const actionOpen = ref(false)
const actionBusy = ref(false)
const actionForm = reactive({ id: null, action: 'RESOLVE', resolutionCode: 'CONFIRMED', note: '' })

const uploadRef = ref(null)
const today = computed(() => new Date())

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    pageData.value = await listStatements({ page: filters.page, size: filters.size })
    if (selected.value) {
      const fresh = pageData.value.records.find(item => item.id === selected.value.id)
      if (fresh) selected.value = fresh
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function chooseFile(file) {
  uploadForm.file = file.raw
}

async function submitUpload() {
  if (!uploadForm.statementDate) {
    error.value = '请选择账单日期'
    return
  }
  if (!uploadForm.file) {
    error.value = '请选择 CSV 或 ZIP 账单文件'
    return
  }
  uploadBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await uploadStatement(uploadForm.provider, uploadForm.statementDate, uploadForm.file)
    notice.value = `账单已导入：${result.fileName}，${result.rowCount} 行`
    uploadForm.file = null
    uploadRef.value?.clearFiles()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    uploadBusy.value = false
  }
}

async function selectStatement(row) {
  selected.value = row
  activeRun.value = null
  breaks.value = { records: [], total: 0, page: 1, size: 20 }
  breakStatus.value = ''
  await loadRuns(row.id)
}

async function loadRuns(fileId) {
  try {
    runs.value = await listRuns(fileId)
    if (runs.value.length) {
      activeRun.value = runs.value[0]
      await loadBreaks()
    }
  } catch (e) {
    error.value = e.message
  }
}

async function runReconciliation() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(
      '本次会生成新的对账版本，只做差异识别，不会自动修改订单或补发权益。',
      '执行资金对账', { type: 'warning', confirmButtonText: '开始对账', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  runBusy.value = true
  error.value = ''
  try {
    activeRun.value = await executeReconciliation(selected.value.id)
    notice.value = `对账 #${activeRun.value.runVersion} 完成：匹配 ${activeRun.value.matchedCount} 行，差异 ${activeRun.value.breakCount} 条`
    await loadRuns(selected.value.id)
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    runBusy.value = false
  }
}

async function chooseRun(run) {
  activeRun.value = run
  breakStatus.value = ''
  breaks.value.page = 1
  await loadBreaks()
}

async function loadBreaks() {
  if (!activeRun.value) return
  try {
    breaks.value = await listBreaks(activeRun.value.id, {
      status: breakStatus.value || undefined,
      page: breaks.value.page,
      size: breaks.value.size
    })
  } catch (e) {
    error.value = e.message
  }
}

async function exportBreaks() {
  if (!activeRun.value) return
  error.value = ''
  notice.value = ''
  exportBusy.value = true
  try {
    await download(`/admin/reconciliation/runs/${activeRun.value.id}/breaks/export`,
      `recon-breaks-${activeRun.value.id}.xlsx`)
    notice.value = '对账差异报表已开始下载（最多 10000 行）'
  } catch (e) {
    error.value = e.message
  } finally {
    exportBusy.value = false
  }
}

function openAction(row, action) {
  Object.assign(actionForm, {
    id: row.id,
    action,
    resolutionCode: action === 'RESOLVE' ? 'CONFIRMED' : '',
    note: action === 'IGNORE' ? '重复差异，已人工确认无需处理' : ''
  })
  actionOpen.value = true
}

async function submitAction() {
  if (actionForm.action !== 'CLAIM' && !actionForm.note.trim()) {
    error.value = '请填写处理说明'
    return
  }
  actionBusy.value = true
  error.value = ''
  try {
    await actOnBreak(actionForm.id, {
      action: actionForm.action,
      resolutionCode: actionForm.resolutionCode || undefined,
      note: actionForm.note.trim() || undefined
    })
    actionOpen.value = false
    notice.value = '差异状态已更新'
    await loadBreaks()
    await loadRuns(selected.value.id)
  } catch (e) {
    error.value = e.message
  } finally {
    actionBusy.value = false
  }
}

function goPage(page) {
  filters.page = page
  load()
}

function breakGoPage(page) {
  breaks.value.page = page
  loadBreaks()
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

function priceText(cents) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format((cents || 0) / 100)
}

function severityType(severity) {
  return severity === 'CRITICAL' ? 'danger' : severity === 'HIGH' ? 'warning' : 'info'
}

function statusType(status) {
  return status === 'RESOLVED' ? 'success' : status === 'IGNORED' ? 'info' : status === 'IN_REVIEW' ? 'warning' : 'danger'
}
</script>

<template>
  <main class="page admin-recon-page">
    <PageHeader
      eyebrow="CASH CONTROL"
      title="资金对账"
      description="导入渠道账单，按支付尝试匹配本地资金流水。执行对账只生成差异，不会自动修改订单。"
    />

    <el-alert v-if="notice" type="success" show-icon class="feedback" :title="notice" @close="notice = ''" />
    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section class="panel upload-panel">
      <div class="panel-heading">
        <div>
          <div class="section-kicker">STATEMENT INGEST</div>
          <h2>导入渠道交易账单</h2>
        </div>
        <el-tag type="info" effect="plain">ALIPAY · CSV / ZIP</el-tag>
      </div>
      <p class="hint">
        先上传账单，再执行对账。文件按 SHA-256 去重，原始内容压缩保存在数据库，重复上传不会重复入账。
        本期不做自动补单和自动退款，所有差异进入工作台。
      </p>
      <div class="upload-grid">
        <el-form label-position="top">
          <el-form-item label="渠道">
            <el-select v-model="uploadForm.provider" style="width: 100%">
              <el-option label="支付宝" value="ALIPAY" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-form label-position="top">
          <el-form-item label="账单日期">
            <el-date-picker
              v-model="uploadForm.statementDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择交易日期"
              style="width: 100%"
              :disabled-date="date => date > today"
            />
          </el-form-item>
        </el-form>
        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".csv,.txt,.zip"
          :on-change="chooseFile"
          :on-exceed="() => error = '一次只能上传一个账单文件'"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖入账单文件，或<em>点击选择</em></div>
          <template #tip><div class="el-upload__tip">最大 20MB，支持 UTF-8 / GB18030 CSV 或 ZIP</div></template>
        </el-upload>
        <el-button v-if="canOperate" type="primary" :loading="uploadBusy" @click="submitUpload">导入账单</el-button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading">
        <div>
          <div class="section-kicker">STATEMENT FILES</div>
          <h2>账单批次</h2>
        </div>
        <el-button text :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="pageData.records" size="small" @row-click="selectStatement">
        <el-table-column prop="statementDate" label="账期" width="120" />
        <el-table-column prop="fileName" label="文件" min-width="220" />
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column label="成功金额" width="140"><template #default="{ row }">{{ priceText(row.totalAmountCents) }}</template></el-table-column>
        <el-table-column prop="status" label="解析" width="100" />
        <el-table-column prop="latestRunStatus" label="最近对账" width="110">
          <template #default="{ row }">{{ row.latestRunStatus || '未执行' }}</template>
        </el-table-column>
        <el-table-column label="导入时间" width="160"><template #default="{ row }">{{ formatDate(row.importedAt) }}</template></el-table-column>
      </el-table>
      <el-pagination
        v-if="pageData.total"
        class="pager"
        layout="total, prev, pager, next"
        :total="pageData.total"
        :current-page="pageData.page"
        :page-size="pageData.size"
        @current-change="goPage"
      />
    </section>

    <section v-if="selected" class="panel">
      <div class="panel-heading">
        <div>
          <div class="section-kicker">RECONCILIATION RUNS</div>
          <h2>{{ selected.fileName }}</h2>
          <p class="hint">{{ selected.provider }} · {{ selected.statementDate }} · 账单行数 {{ selected.rowCount }}</p>
        </div>
        <el-button v-if="canOperate" type="warning" :loading="runBusy" @click="runReconciliation">执行对账</el-button>
      </div>
      <el-empty v-if="!runs.length" description="还没有对账运行" :image-size="70" />
      <template v-else>
        <div class="run-list">
          <button
            v-for="run in runs"
            :key="run.id"
            class="run-pill"
            :class="{ active: activeRun?.id === run.id }"
            type="button"
            @click="chooseRun(run)"
          >
            <strong>#{{ run.runVersion }}</strong>
            <span>{{ run.status }}</span>
            <small>匹配 {{ run.matchedCount }} / 差异 {{ run.breakCount }}</small>
          </button>
        </div>
        <div v-if="activeRun" class="run-summary">
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="运行状态">{{ activeRun.status }}</el-descriptions-item>
            <el-descriptions-item label="匹配行">{{ activeRun.matchedCount }}</el-descriptions-item>
            <el-descriptions-item label="未解决差异">{{ activeRun.unresolvedBreakCount }}</el-descriptions-item>
            <el-descriptions-item label="未匹配金额">{{ priceText(activeRun.unmatchedAmountCents) }}</el-descriptions-item>
          </el-descriptions>
          <div class="break-toolbar">
            <span>差异工作台</span>
            <div class="break-toolbar-actions">
              <el-select v-model="breakStatus" size="small" clearable placeholder="全部状态" @change="loadBreaks">
                <el-option label="待处理" value="OPEN" />
                <el-option label="处理中" value="IN_REVIEW" />
                <el-option label="已解决" value="RESOLVED" />
                <el-option label="已忽略" value="IGNORED" />
              </el-select>
              <el-button size="small" :loading="exportBusy" @click="exportBreaks">
                <el-icon><Download /></el-icon>
                导出差异
              </el-button>
            </div>
          </div>
          <el-table :data="breaks.records" size="small" empty-text="暂无差异">
            <el-table-column prop="breakType" label="差异类型" min-width="160" />
            <el-table-column prop="severity" label="等级" width="100"><template #default="{ row }"><el-tag :type="severityType(row.severity)">{{ row.severity }}</el-tag></template></el-table-column>
            <el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column prop="orderNo" label="订单" min-width="190" />
            <el-table-column prop="merchantTradeNo" label="商户单号" min-width="190" />
            <el-table-column prop="providerTradeNo" label="渠道交易号" min-width="200" />
            <el-table-column label="差额" width="110"><template #default="{ row }">{{ priceText(row.deltaCents) }}</template></el-table-column>
            <el-table-column prop="detail" label="说明" min-width="260" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canOperate && row.status === 'OPEN'" text size="small" @click="openAction(row, 'CLAIM')">认领</el-button>
                <el-button v-if="canOperate && ['OPEN', 'IN_REVIEW'].includes(row.status)" text type="primary" size="small" @click="openAction(row, 'RESOLVE')">解决</el-button>
                <el-button v-if="canOperate && ['OPEN', 'IN_REVIEW'].includes(row.status)" text type="info" size="small" @click="openAction(row, 'IGNORE')">忽略</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="breaks.total" class="pager" layout="total, prev, pager, next" :total="breaks.total" :current-page="breaks.page" :page-size="breaks.size" @current-change="breakGoPage" />
        </div>
      </template>
    </section>

    <el-dialog v-model="actionOpen" :title="actionForm.action === 'CLAIM' ? '认领差异' : actionForm.action === 'IGNORE' ? '忽略差异' : '解决差异'" width="480px" align-center>
      <el-form label-position="top">
        <el-form-item v-if="actionForm.action === 'RESOLVE'" label="处理代码">
          <el-select v-model="actionForm.resolutionCode" style="width: 100%">
            <el-option label="已人工确认" value="CONFIRMED" />
            <el-option label="已补建支付记录" value="PAYMENT_REPAIRED" />
            <el-option label="已发起退款" value="REFUND_CREATED" />
            <el-option label="数据质量问题" value="DATA_QUALITY" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理说明" required>
          <el-input v-model="actionForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="记录证据、原因和处理结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="actionBusy" @click="actionOpen = false">取消</el-button>
        <el-button type="primary" :loading="actionBusy" @click="submitAction">确认</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.upload-panel { margin-bottom: 20px; }
.panel { padding: 22px; border: 1px solid var(--border); border-radius: var(--radius-md); background: rgba(255,253,248,.78); margin-bottom: 20px; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 14px; }
.panel-heading h2 { margin: 4px 0 0; font-family: var(--font-display); font-size: 22px; }
.section-kicker { color: var(--muted); font-family: var(--font-mono); font-size: 10px; letter-spacing: .16em; }
.hint { color: var(--muted); font-size: 12px; line-height: 1.7; }
.upload-grid { display: grid; grid-template-columns: 170px 190px minmax(240px, 1fr) auto; gap: 16px; align-items: end; }
.pager { margin-top: 16px; justify-content: flex-end; }
.run-list { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.run-pill { display: grid; text-align: left; gap: 2px; min-width: 150px; padding: 10px 14px; border: 1px solid var(--border); border-radius: 10px; background: transparent; cursor: pointer; }
.run-pill.active { border-color: var(--accent); background: rgba(123,92,45,.08); }
.run-pill strong { font-family: var(--font-mono); }
.run-pill span { font-size: 12px; }
.run-pill small { color: var(--muted); }
.run-summary { margin-top: 8px; }
.break-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 10px; font-weight: 600; }
.break-toolbar-actions { display: flex; align-items: center; gap: 8px; }
.break-toolbar-actions .el-select { width: 130px; }
.feedback { margin-bottom: 16px; }
@media (max-width: 900px) { .upload-grid { grid-template-columns: 1fr; } .panel { padding: 16px; } }
</style>
