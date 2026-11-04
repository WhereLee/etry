<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  closeAdminOrder,
  fulfillAdminOrder,
  getAdminOrder,
  listAdminOrderExceptions,
  listAdminOrders,
  queryAdminOrder,
  refundAdminOrder
} from '../api/order'
import PageHeader from '../components/PageHeader.vue'
import StatusTag from '../components/StatusTag.vue'

const loading = ref(true)
const error = ref('')
const notice = ref('')
const pageData = ref({ records: [], total: 0, page: 1, size: 20 })
const filters = reactive({ keyword: '', status: '', page: 1, size: 20 })
const exceptionsOnly = ref(false)

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const actionBusy = ref('')
const refundOpen = ref(false)
const refundBusy = ref(false)
const refundForm = reactive({ reason: '', simulateFailure: false })

const statusOptions = [
  ['CREATED', '待支付'], ['PAYING', '支付中'], ['PAID', '已支付'], ['FULFILLED', '已完成'],
  ['PAY_FAILED', '支付失败'], ['CLOSED', '已关闭'], ['REFUNDING', '退款中'],
  ['REFUNDED', '已退款'], ['REFUND_FAILED', '退款失败']
]

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const query = {
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      page: filters.page,
      size: filters.size
    }
    pageData.value = exceptionsOnly.value
      ? await listAdminOrderExceptions({ page: filters.page, size: filters.size })
      : await listAdminOrders(query)
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
  exceptionsOnly.value = false
  load()
}

function toggleExceptions() {
  exceptionsOnly.value = !exceptionsOnly.value
  filters.page = 1
  load()
}

function goPage(page) {
  filters.page = page
  load()
}

function changeSize(size) {
  filters.size = size
  filters.page = 1
  load()
}

async function openDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAdminOrder(row.orderNo)
  } catch (e) {
    error.value = e.message
  } finally {
    detailLoading.value = false
  }
}

async function runAction(action) {
  const orderNo = detail.value?.order?.orderNo
  if (!orderNo) return
  if (action === 'close') {
    try {
      await ElMessageBox.confirm('确认关闭这个未支付订单？', '关闭订单', {
        type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  actionBusy.value = action
  error.value = ''
  notice.value = ''
  try {
    if (action === 'query') detail.value = await queryAdminOrder(orderNo)
    if (action === 'fulfill') detail.value = await fulfillAdminOrder(orderNo)
    if (action === 'close') detail.value = await closeAdminOrder(orderNo)
    notice.value = { query: '订单状态已刷新', fulfill: '补履约已完成', close: '订单已关闭' }[action]
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    actionBusy.value = ''
  }
}

function openRefund() {
  Object.assign(refundForm, { reason: '', simulateFailure: false })
  refundOpen.value = true
}

async function submitRefund() {
  if (!refundForm.reason.trim()) {
    error.value = '请填写退款原因'
    return
  }
  refundBusy.value = true
  error.value = ''
  try {
    detail.value = await refundAdminOrder(detail.value.order.orderNo, {
      reason: refundForm.reason.trim(),
      simulateFailure: refundForm.simulateFailure
    })
    refundOpen.value = false
    notice.value = detail.value.refund?.status === 'SUCCEEDED' ? '退款成功，订单权益已回收' : '退款失败，请查看订单详情'
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    refundBusy.value = false
  }
}

function priceText(cents, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format((cents || 0) / 100)
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

function timelineType(type) {
  return type.includes('REFUND') ? 'danger' : type === 'FULFILLED' ? 'success' : 'primary'
}
</script>

<template>
  <main class="page admin-orders-page">
    <PageHeader eyebrow="ORDER OPERATIONS" title="订单管理" description="查看支付状态、处理异常订单、补履约和全额退款。">
      <template #actions>
        <el-button :type="exceptionsOnly ? 'warning' : 'default'" @click="toggleExceptions">
          {{ exceptionsOnly ? '查看全部订单' : '只看异常订单' }}
        </el-button>
      </template>
    </PageHeader>

    <section class="toolbar">
      <div class="filters">
        <el-input v-model="filters.keyword" class="keyword-input" placeholder="订单号、学员或课程" clearable @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.status" class="status-select" placeholder="全部状态" :disabled="exceptionsOnly" @change="search">
          <el-option label="全部状态" value="" />
          <el-option v-for="[value, label] in statusOptions" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="clearFilters">重置</el-button>
      </div>
      <span class="total-count">共 {{ pageData.total }} 个订单</span>
    </section>

    <el-alert v-if="notice" type="success" show-icon class="feedback" @close="notice = ''" :title="notice" />
    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section class="table-panel">
      <el-table v-loading="loading" :data="pageData.records" row-key="orderNo" stripe>
        <el-table-column label="订单 / 课程" min-width="240">
          <template #default="{ row }">
            <div class="order-cell">
              <strong>{{ row.seriesTitle }}</strong>
              <span>{{ row.orderNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="学员" min-width="130">
          <template #default="{ row }">
            <div class="user-cell"><strong>{{ row.nickname }}</strong><span>{{ row.username }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }"><strong class="amount">{{ priceText(row.priceCents, row.currency) }}</strong></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }"><span class="date-text">{{ formatDate(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
        <template #empty><el-empty :description="exceptionsOnly ? '当前没有异常订单' : '暂无订单'" /></template>
      </el-table>
      <footer class="pager">
        <span>第 {{ pageData.page }} 页，共 {{ pageData.total }} 条</span>
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

    <el-dialog v-model="detailOpen" title="订单详情" width="900px" align-center>
      <div v-if="detailLoading" class="dialog-loading"><el-skeleton animated :rows="9" /></div>
      <template v-else-if="detail">
        <div class="detail-title">
          <div>
            <div class="dialog-kicker">ORDER {{ detail.order.orderNo }}</div>
            <h2>{{ detail.order.seriesTitle }}</h2>
          </div>
          <StatusTag :status="detail.order.status" :label="detail.order.statusLabel" />
        </div>

        <el-descriptions :column="3" border class="order-descriptions">
          <el-descriptions-item label="学员">{{ detail.order.nickname }}（{{ detail.order.username }}）</el-descriptions-item>
          <el-descriptions-item label="金额">{{ priceText(detail.order.priceCents, detail.order.currency) }}</el-descriptions-item>
          <el-descriptions-item label="支付渠道">{{ detail.order.payChannel || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(detail.order.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDate(detail.order.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="履约时间">{{ formatDate(detail.order.fulfilledAt) }}</el-descriptions-item>
          <el-descriptions-item label="平台交易号" :span="3">{{ detail.order.providerTradeNo || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.order.lastError" label="最近错误" :span="3">{{ detail.order.lastError }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-button :loading="actionBusy === 'query'" @click="runAction('query')">主动查单</el-button>
          <el-button v-if="detail.order.status === 'PAID'" type="primary" :loading="actionBusy === 'fulfill'" @click="runAction('fulfill')">补履约</el-button>
          <el-button v-if="['CREATED', 'PAY_FAILED'].includes(detail.order.status)" :loading="actionBusy === 'close'" @click="runAction('close')">关闭订单</el-button>
          <el-button v-if="['PAID', 'FULFILLED', 'REFUND_FAILED'].includes(detail.order.status)" type="danger" plain @click="openRefund">发起退款</el-button>
        </div>

        <el-tabs class="detail-tabs">
          <el-tab-pane label="时间线">
            <el-timeline>
              <el-timeline-item
                v-for="item in detail.orderDetail?.timeline || []"
                :key="item.type + item.time"
                :type="timelineType(item.type)"
                :timestamp="formatDate(item.time)"
              >{{ item.title }}</el-timeline-item>
            </el-timeline>
          </el-tab-pane>
          <el-tab-pane label="支付尝试">
            <el-table :data="detail.attempts || []" size="small" empty-text="暂无支付尝试">
              <el-table-column prop="attemptNo" label="次数" width="60" />
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column prop="providerStatus" label="平台状态" width="100" />
              <el-table-column prop="providerTradeNo" label="平台交易号" min-width="230" />
              <el-table-column prop="mockScenario" label="Mock 场景" min-width="140" />
              <el-table-column label="创建时间" width="155"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="支付事件">
            <el-table :data="detail.events || []" size="small" empty-text="暂无支付事件">
              <el-table-column prop="eventType" label="事件" width="130" />
              <el-table-column prop="processStatus" label="处理" width="90" />
              <el-table-column label="金额" width="100"><template #default="{ row }">{{ priceText(row.amountCents, detail.order.currency) }}</template></el-table-column>
              <el-table-column prop="eventKey" label="事件键" min-width="220" />
              <el-table-column prop="errorMessage" label="错误" min-width="150" />
              <el-table-column label="时间" width="155"><template #default="{ row }">{{ formatDate(row.receivedAt) }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="退款">
            <el-empty v-if="!detail.refund" description="暂无退款记录" :image-size="64" />
            <el-descriptions v-else :column="2" border>
              <el-descriptions-item label="退款单号" :span="2">{{ detail.refund.refundNo }}</el-descriptions-item>
              <el-descriptions-item label="金额">{{ priceText(detail.refund.amountCents, detail.order.currency) }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ detail.refund.status }}</el-descriptions-item>
              <el-descriptions-item label="原因" :span="2">{{ detail.refund.reason }}</el-descriptions-item>
              <el-descriptions-item label="申请时间">{{ formatDate(detail.refund.requestedAt) }}</el-descriptions-item>
              <el-descriptions-item label="成功时间">{{ formatDate(detail.refund.succeededAt) }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.refund.errorMessage" label="错误" :span="2">{{ detail.refund.errorMessage }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-dialog>

    <el-dialog v-model="refundOpen" title="发起全额退款" width="480px" align-center>
      <div class="dialog-kicker">FULL REFUND</div>
      <p class="refund-hint">退款成功后只撤销订单来源权益，管理员分配的权益不受影响。</p>
      <el-form label-position="top">
        <el-form-item label="退款原因">
          <el-input v-model="refundForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="填写退款原因" />
        </el-form-item>
        <el-form-item v-if="detail" label="Mock 故障演练">
          <el-switch v-model="refundForm.simulateFailure" active-text="模拟退款失败" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="refundBusy" @click="refundOpen = false">取消</el-button>
        <el-button type="danger" :loading="refundBusy" @click="submitRefund">确认退款</el-button>
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
  width: 270px;
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

.table-panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.order-cell,
.user-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.order-cell strong,
.user-cell strong {
  font-size: 13px;
}

.order-cell span,
.user-cell span,
.date-text {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.amount {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 15px;
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

.dialog-loading {
  padding: 24px;
}

.detail-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.dialog-kicker {
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.detail-title h2 {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 26px;
  letter-spacing: -0.04em;
}

.order-descriptions {
  margin-bottom: 16px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.refund-hint {
  margin: 10px 0 18px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
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
    width: calc(100% - 130px);
  }
}
</style>
