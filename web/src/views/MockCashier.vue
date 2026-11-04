<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMockCashier, simulateMockPayment } from '../api/order'

const route = useRoute()
const router = useRouter()
const orderNo = computed(() => String(route.params.orderNo || ''))
const loading = ref(true)
const busy = ref('')
const error = ref('')
const result = ref(null)
const cashier = ref(null)

const scenarios = {
  SUCCESS: { title: '模拟支付成功', desc: '产生标准支付成功事件，订单会进入权益发放。', type: 'success' },
  FAILED: { title: '模拟支付失败', desc: '模拟用户支付失败，订单可以重新发起支付。', type: 'danger' },
  LOST_CALLBACK: { title: '回调丢失', desc: '平台已扣款但不发送通知，等待主动查单补偿。', type: 'warning' },
  DELAYED: { title: '延迟通知', desc: '支付成功事件延迟 5 秒送达。', type: 'warning' },
  DUPLICATE: { title: '重复通知', desc: '同一支付事件发送两次，验证权益不会重复生成。', type: 'warning' },
  AMOUNT_MISMATCH: { title: '金额不一致', desc: '通知金额与订单金额不同，订单不得发货。', type: 'danger' },
  FULFILLMENT_FAIL_ONCE: { title: '履约失败一次', desc: '支付成功但首次发放权益失败，等待补偿任务恢复。', type: 'danger' }
}

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    cashier.value = await getMockCashier(orderNo.value)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function priceText(cents, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format((cents || 0) / 100)
}

async function run(scenario) {
  busy.value = scenario
  error.value = ''
  result.value = null
  try {
    result.value = await simulateMockPayment(orderNo.value, scenario)
    if (scenario === 'SUCCESS' || scenario === 'DUPLICATE') {
      setTimeout(() => router.push({ name: 'orderDetail', params: { orderNo: orderNo.value } }), 800)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <main class="page mock-page">
    <div class="mock-banner">
      <span>MOCK PAYMENT</span>
      <strong>本地模拟收银台</strong>
      <small>该页面不会产生真实扣款</small>
    </div>

    <div v-if="loading" class="panel loading-panel"><el-skeleton animated :rows="6" /></div>
    <el-alert v-else-if="error && !cashier" type="error" show-icon :closable="false" :title="error" />

    <template v-else-if="cashier">
      <section class="cashier-card">
        <div class="cashier-head">
          <div class="eyebrow">PAYMENT CONFIRMATION</div>
          <h1>确认订单</h1>
          <p>请选择模拟场景。Mock 事件仍会经过签名、幂等、订单确认和权益履约流程。</p>
        </div>

        <div class="order-summary">
          <div class="summary-row"><span>课程</span><strong>{{ cashier.seriesTitle }}</strong></div>
          <div class="summary-row"><span>订单号</span><code>{{ cashier.orderNo }}</code></div>
          <div class="summary-row"><span>订单状态</span><el-tag effect="plain">{{ cashier.status }}</el-tag></div>
          <div class="amount-row"><span>应付金额</span><strong>{{ priceText(cashier.priceCents, cashier.currency) }}</strong></div>
        </div>

        <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" class="feedback" />
        <el-alert v-if="result" type="success" show-icon :closable="false" class="feedback" :title="result.message">
          <template #default>当前订单状态：{{ result.orderStatus }}</template>
        </el-alert>

        <div class="primary-actions">
          <el-button type="primary" size="large" :loading="busy === 'SUCCESS'" :disabled="!!busy" @click="run('SUCCESS')">模拟支付成功</el-button>
          <el-button type="danger" plain size="large" :loading="busy === 'FAILED'" :disabled="!!busy" @click="run('FAILED')">模拟支付失败</el-button>
        </div>

        <el-collapse class="fault-panel">
          <el-collapse-item title="开发场景 / 故障演练" name="faults">
            <div class="scenario-grid">
              <button
                v-for="(meta, key) in scenarios"
                v-show="key !== 'SUCCESS' && key !== 'FAILED'"
                :key="key"
                type="button"
                class="scenario-button"
                :disabled="!!busy"
                @click="run(key)"
              >
                <span class="scenario-title">{{ meta.title }}<el-icon v-if="busy === key" class="is-loading"><Refresh /></el-icon></span>
                <small>{{ meta.desc }}</small>
              </button>
            </div>
          </el-collapse-item>
        </el-collapse>

        <el-button text class="back-order" @click="router.push({ name: 'orderDetail', params: { orderNo } })">返回订单详情</el-button>
      </section>
    </template>
  </main>
</template>

<style scoped>
.mock-page {
  max-width: 760px;
  padding-top: 30px;
}

.mock-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 13px;
  border: 1px solid #e4c693;
  border-radius: var(--radius-sm);
  background: #fff8e8;
  color: #7d612a;
}

.mock-banner span {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.mock-banner strong {
  font-size: 12px;
}

.mock-banner small {
  margin-left: auto;
  font-size: 10px;
}

.loading-panel {
  padding: 30px;
}

.cashier-card {
  padding: clamp(24px, 5vw, 42px);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-md);
}

.cashier-head h1 {
  margin: 9px 0 8px;
  font-family: var(--font-display);
  font-size: clamp(2rem, 5vw, 3rem);
  letter-spacing: -0.05em;
}

.cashier-head p {
  margin: 0 0 24px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.order-summary {
  margin-bottom: 20px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: #faf7f0;
}

.summary-row,
.amount-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 8px 0;
  color: var(--muted);
  font-size: 12px;
}

.summary-row strong {
  color: var(--text);
  font-size: 13px;
}

.summary-row code {
  color: var(--text-soft);
  font-family: var(--font-mono);
  font-size: 10px;
}

.amount-row {
  margin-top: 8px;
  padding-top: 15px;
  border-top: 1px solid var(--border);
}

.amount-row strong {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 27px;
}

.feedback {
  margin-bottom: 14px;
}

.primary-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.fault-panel {
  margin-top: 20px;
  border-top: 1px solid var(--border);
}

.scenario-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.scenario-button {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: #fffefa;
  color: var(--text);
  text-align: left;
  cursor: pointer;
}

.scenario-button:hover {
  border-color: var(--clay);
}

.scenario-button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.scenario-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
}

.scenario-button small {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.55;
}

.back-order {
  margin-top: 16px;
}

@media (max-width: 600px) {
  .mock-banner small {
    display: none;
  }

  .primary-actions,
  .scenario-grid {
    grid-template-columns: 1fr;
  }
}
</style>
