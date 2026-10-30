<template>
  <div class="page" style="max-width:720px">
    <p class="muted" style="margin-top:0">
      <RouterLink to="/orders">← 返回订单列表</RouterLink>
    </p>

    <div v-if="error" class="error-bar">{{ error }}</div>
    <div v-if="!order && !error" class="muted">加载中…</div>

    <div v-if="order" class="panel">
      <div class="between">
        <h2 style="margin:0;font-family:monospace;font-size:18px">{{ order.orderNo }}</h2>
        <span :class="['tag', statusClass(order.status)]">{{ statusText(order.status) }}</span>
      </div>

      <div v-if="order.status === 'PENDING_PAY'" class="between" style="margin-top:12px">
        <span class="muted">支付剩余时间</span>
        <span :class="expired ? 'danger' : 'warn'">{{ countdown }}</span>
      </div>

      <table style="margin-top:16px">
        <thead>
          <tr>
            <th>商品</th>
            <th>单价</th>
            <th>数量</th>
            <th>小计</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in order.items" :key="item.productId">
            <td>{{ item.productName }}</td>
            <td>{{ item.unitPoints }}</td>
            <td>{{ item.quantity }}</td>
            <td>{{ item.subtotalPoints }}</td>
          </tr>
        </tbody>
      </table>

      <div class="between" style="margin-top:18px">
        <span class="muted">合计</span>
        <span class="price">{{ order.totalPoints }} 积分</span>
      </div>

      <div style="margin-top:22px;border-top:1px solid var(--line);padding-top:16px">
        <div class="between" style="padding:4px 0">
          <span class="muted">收货地址</span>
          <span>{{ order.address || '未填写' }}</span>
        </div>
        <div class="between" style="padding:4px 0">
          <span class="muted">下单时间</span>
          <span>{{ formatTime(order.createdAt) }}</span>
        </div>
        <div class="between" style="padding:4px 0">
          <span class="muted">支付截止</span>
          <span>{{ formatTime(order.expireAt) }}</span>
        </div>
      </div>

      <p class="muted" style="font-size:12px;margin:16px 0 0">
        支付能力将在下一阶段接入，当前订单停留在待支付状态。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchOrder } from '../api/order'

const route = useRoute()

const order = ref(null)
const error = ref('')
const now = ref(Date.now())
let timer = null

const expired = computed(() => {
  if (!order.value || !order.value.expireAt) return false
  return new Date(order.value.expireAt).getTime() - now.value <= 0
})

const countdown = computed(() => {
  if (!order.value || !order.value.expireAt) return '-'
  const diff = new Date(order.value.expireAt).getTime() - now.value
  if (diff <= 0) return '已超时'
  const minutes = Math.floor(diff / 60000)
  const seconds = Math.floor((diff % 60000) / 1000)
  return `${minutes} 分 ${String(seconds).padStart(2, '0')} 秒`
})

function statusText(status) {
  const map = {
    PENDING_PAY: '待支付',
    PAID: '已支付',
    CANCELLED: '已取消',
    SHIPPED: '已发货',
    COMPLETED: '已完成'
  }
  return map[status] || status
}

function statusClass(status) {
  if (status === 'PENDING_PAY') return 'warn'
  if (status === 'CANCELLED') return 'danger'
  if (status === 'PAID' || status === 'COMPLETED') return 'ok'
  return ''
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function load() {
  error.value = ''
  try {
    order.value = await fetchOrder(route.params.id)
  } catch (e) {
    error.value = e.message
  }
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
