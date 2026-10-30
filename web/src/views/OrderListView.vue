<template>
  <div class="page">
    <div class="between" style="margin-bottom:18px">
      <h2 style="margin:0">我的订单</h2>
      <div class="row">
        <button
          v-for="item in filters"
          :key="item.value"
          class="ghost"
          :style="active === item.value ? 'border-color:var(--primary);color:var(--primary)' : ''"
          @click="changeFilter(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>
    <div v-if="loading" class="muted">加载中…</div>
    <div v-else-if="records.length === 0" class="panel muted">还没有订单，去商品页看看</div>

    <div v-else class="panel" style="padding:0">
      <table>
        <thead>
          <tr>
            <th>订单号</th>
            <th>状态</th>
            <th>积分</th>
            <th>下单时间</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in records" :key="item.id">
            <td style="font-family:monospace">{{ item.orderNo }}</td>
            <td>
              <span :class="['tag', statusClass(item.status)]">{{ statusText(item.status) }}</span>
            </td>
            <td>{{ item.totalPoints }}</td>
            <td class="muted">{{ formatTime(item.createdAt) }}</td>
            <td><RouterLink :to="`/orders/${item.id}`">查看</RouterLink></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="between" style="margin-top:22px">
      <span class="muted">共 {{ total }} 单</span>
      <div class="row">
        <button class="ghost" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
        <span class="muted">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="ghost" :disabled="page >= totalPages" @click="go(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchOrders } from '../api/order'

const filters = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'PENDING_PAY' }
]

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const active = ref('')
const loading = ref(false)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

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
  loading.value = true
  error.value = ''
  try {
    const data = await fetchOrders({ page: page.value, size, status: active.value || undefined })
    records.value = data.records
    total.value = data.total
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function changeFilter(value) {
  active.value = value
  page.value = 1
  load()
}

function go(next) {
  page.value = next
  load()
}

onMounted(load)
</script>
