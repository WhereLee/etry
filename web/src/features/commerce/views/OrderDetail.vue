<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { closeOrder, getOrder, getRefundRequest, payOrder, submitRefundRequest } from '@/features/commerce/api/order'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const orderNo = computed(() => String(route.params.orderNo || ''))
const loading = ref(true)
const busy = ref('')
const error = ref('')
const detail = ref(null)
const refundRequest = ref(null)
const refundOpen = ref(false)
const refundBusy = ref(false)
const refundForm = reactive({ reason: '' })
let pollTimer = null
let pollCount = 0

const order = computed(() => detail.value?.order || null)
const isMaterial = computed(() => order.value?.productType === 'MATERIAL')
const shouldPoll = computed(() => order.value && ['PAYING', 'PAID', 'REFUNDING'].includes(order.value.status))
const canRequestRefund = computed(() => order.value
  && ['PAID', 'FULFILLED', 'REFUND_FAILED'].includes(order.value.status)
  && (!refundRequest.value || refundRequest.value.status === 'REJECTED'))

onMounted(load)
onBeforeUnmount(stopPolling)

async function load(silent = false) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    detail.value = await getOrder(orderNo.value)
    refundRequest.value = await getRefundRequest(orderNo.value).catch(() => null)
    if (shouldPoll.value) startPolling()
    else stopPolling()
  } catch (e) {
    error.value = e.message
  } finally {
    if (!silent) loading.value = false
  }
}

function startPolling() {
  if (pollTimer) return
  pollCount = 0
  pollTimer = setInterval(async () => {
    pollCount += 1
    if (pollCount > 40) {
      stopPolling()
      return
    }
    try {
      detail.value = await getOrder(orderNo.value)
      refundRequest.value = await getRefundRequest(orderNo.value).catch(() => null)
      if (!shouldPoll.value) stopPolling()
    } catch {
      stopPolling()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pay() {
  busy.value = 'pay'
  error.value = ''
  try {
    const response = await payOrder(orderNo.value)
    const cashierUrl = response.cashierUrl || ''
    if (/^https?:\/\//i.test(cashierUrl)) {
      // 支付宝收银台是外链，整页跳转；Mock 收银台仍是站内路由
      window.location.assign(cashierUrl)
    } else {
      router.push(cashierUrl)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function close() {
  try {
    await ElMessageBox.confirm('确认关闭这个未支付订单？', '关闭订单', {
      type: 'warning',
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  busy.value = 'close'
  try {
    detail.value = { order: await closeOrder(orderNo.value), timeline: detail.value?.timeline || [] }
    await load(true)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

function priceText(cents, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format((cents || 0) / 100)
}

function openRefund() {
  refundForm.reason = ''
  error.value = ''
  refundOpen.value = true
}

async function submitRefund() {
  const reason = refundForm.reason.trim()
  if (!reason) {
    error.value = '请填写退款理由'
    return
  }
  refundBusy.value = true
  error.value = ''
  try {
    refundRequest.value = await submitRefundRequest(orderNo.value, reason)
    refundOpen.value = false
    await load(true)
  } catch (e) {
    error.value = e.message
  } finally {
    refundBusy.value = false
  }
}

function refundStatusLabel(status) {
  return { PENDING: '处理中', APPROVED: '已同意', REJECTED: '已拒绝' }[status] || status
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '—'
}

function timelineType(type) {
  return type.includes('REFUND') ? 'danger' : type === 'FULFILLED' ? 'success' : 'primary'
}
</script>

<template>
  <main class="page order-detail-page">
    <RouterLink to="/orders" class="back-link">
      <el-icon><ArrowLeft /></el-icon>
      返回我的订单
    </RouterLink>

    <div v-if="loading" class="panel loading-panel"><el-skeleton animated :rows="8" /></div>
    <el-alert v-else-if="error && !order" type="error" show-icon :closable="false" :title="error" />

    <template v-else-if="order">
      <header class="detail-hero">
        <div>
          <div class="eyebrow">ORDER DETAIL</div>
          <h1>{{ order.productTitle || order.seriesTitle }}</h1>
          <code>{{ order.orderNo }}</code>
        </div>
        <StatusTag :status="order.status" :label="order.statusLabel" />
      </header>

      <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" class="feedback" />

      <section v-if="detail.items?.length" class="items-card">
        <div class="items-head">
          <strong>订单明细</strong>
          <span>共 {{ detail.items.length }} 件 · 下单时价格快照</span>
        </div>
        <div v-for="item in detail.items" :key="item.materialId" class="item-row">
          <RouterLink :to="{ name: 'materialDetail', params: { id: item.materialId } }" class="item-title">
            {{ item.title }}
          </RouterLink>
          <strong class="item-price">{{ priceText(item.priceCents, order.currency) }}</strong>
        </div>
      </section>

      <div class="detail-grid">
        <section class="summary-card">
          <div class="summary-row"><span>{{ isMaterial ? '订单金额' : '课程金额' }}</span><strong class="price">{{ priceText(order.priceCents, order.currency) }}</strong></div>
          <div class="summary-row"><span>创建时间</span><span>{{ formatDate(order.createdAt) }}</span></div>
          <div class="summary-row"><span>支付截止</span><span>{{ formatDate(order.expireAt) }}</span></div>
          <div class="summary-row"><span>支付时间</span><span>{{ formatDate(order.paidAt) }}</span></div>
          <div class="summary-row"><span>权益发放</span><span>{{ formatDate(order.fulfilledAt) }}</span></div>

          <div class="action-box">
            <template v-if="['CREATED', 'PAY_FAILED', 'PAYING'].includes(order.status)">
              <el-button type="primary" size="large" :loading="busy === 'pay'" @click="pay">
                {{ order.status === 'PAYING' ? '继续支付' : '前往支付' }}
              </el-button>
              <el-button v-if="order.status !== 'PAYING'" :loading="busy === 'close'" @click="close">关闭订单</el-button>
            </template>
            <template v-else-if="order.status === 'FULFILLED'">
              <template v-if="isMaterial">
                <el-button type="primary" size="large" @click="router.push({ name: 'learning' })">前往我的学习空间</el-button>
                <el-button @click="router.push({ name: 'materialStore' })">返回资料商城</el-button>
              </template>
              <template v-else>
                <el-button type="primary" size="large" @click="router.push({ name: 'series', params: { id: order.seriesId } })">开始学习</el-button>
                <el-button @click="router.push({ name: 'courseStore' })">返回课程商店</el-button>
              </template>
            </template>
            <template v-else-if="order.status === 'PAID'">
              <el-alert type="warning" :closable="false" show-icon :title="isMaterial ? '支付已确认，系统正在发放资料权益。' : '支付已确认，系统正在发放课程权益。'" />
              <el-button @click="load()">立即查询</el-button>
            </template>
            <template v-else-if="order.status === 'CLOSED'">
              <el-button type="primary" size="large" @click="router.push(isMaterial ? { name: 'materialStore' } : { name: 'courseStore' })">重新购买</el-button>
            </template>
            <template v-else-if="['REFUNDED', 'REFUNDING', 'REFUND_FAILED'].includes(order.status)">
              <el-alert type="info" :closable="false" show-icon :title="order.statusLabel">
                <template #default>退款相关问题请联系管理员处理。</template>
              </el-alert>
            </template>
            <el-button v-if="canRequestRefund" type="danger" plain @click="openRefund">申请整单退款</el-button>
          </div>

          <el-alert
            v-if="refundRequest"
            class="refund-state"
            :type="refundRequest.status === 'APPROVED' ? 'success' : refundRequest.status === 'REJECTED' ? 'warning' : 'info'"
            :closable="false"
            show-icon
            :title="`退款申请：${refundStatusLabel(refundRequest.status)}`"
          >
            <template #default>
              <span>理由：{{ refundRequest.reason }}</span>
              <span v-if="refundRequest.status === 'REJECTED' && refundRequest.handleNote">
                · 处理备注：{{ refundRequest.handleNote }}（可再次申请）
              </span>
            </template>
          </el-alert>
        </section>

        <section class="timeline-card">
          <div class="timeline-head">
            <strong>订单时间线</strong>
            <span>状态变更记录</span>
          </div>
          <el-timeline v-if="detail.timeline?.length">
            <el-timeline-item
              v-for="item in detail.timeline"
              :key="item.type + item.time"
              :type="timelineType(item.type)"
              :timestamp="formatDate(item.time)"
            >
              {{ item.title }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无时间线" :image-size="72" />
        </section>
      </div>
    </template>

    <el-dialog v-model="refundOpen" title="申请整单退款" width="520px" align-center>
      <p class="refund-hint">提交后由客服审核，同意后按原支付渠道整单退款，已发放的资料权益将被回收。</p>
      <el-input
        v-model="refundForm.reason"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="请说明退款理由，例如：买错了 / 内容与描述不符"
      />
      <template #footer>
        <el-button :disabled="refundBusy" @click="refundOpen = false">取消</el-button>
        <el-button type="danger" :loading="refundBusy" @click="submitRefund">提交申请</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.order-detail-page {
  max-width: 980px;
  padding-top: 28px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  color: var(--muted);
  font-size: 13px;
}

.loading-panel {
  padding: 30px;
}

.detail-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border-strong);
}

.detail-hero h1 {
  margin: 9px 0 8px;
  font-family: var(--font-display);
  font-size: clamp(2rem, 5vw, 3.4rem);
  letter-spacing: -0.055em;
  line-height: 1.1;
}

.detail-hero code {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.feedback {
  margin-top: 16px;
}

.items-card {
  margin-top: 22px;
  padding: 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.items-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}

.items-head strong {
  font-family: var(--font-display);
  font-size: 18px;
}

.items-head span {
  color: var(--muted);
  font-size: 10px;
}

.item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}

.item-row:last-child {
  border-bottom: none;
}

.item-title {
  overflow: hidden;
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-title:hover {
  color: var(--pine);
}

.item-price {
  flex: none;
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 15px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(280px, 0.92fr);
  gap: 18px;
  margin-top: 22px;
}

.summary-card,
.timeline-card {
  padding: 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  color: var(--muted);
  font-size: 12px;
}

.summary-row strong {
  color: var(--text);
  font-size: 13px;
}

.summary-row .price {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 25px;
}

.action-box {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.refund-state {
  margin-top: 14px;
}

.refund-hint {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.timeline-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20px;
}

.timeline-head strong {
  font-family: var(--font-display);
  font-size: 18px;
}

.timeline-head span {
  color: var(--muted);
  font-size: 10px;
}

.timeline-card :deep(.el-timeline) {
  padding-left: 4px;
}

.timeline-card :deep(.el-timeline-item__timestamp) {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
}

@media (max-width: 760px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
