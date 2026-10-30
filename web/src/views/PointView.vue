<template>
  <div class="page" style="max-width:820px">
    <h2 style="margin-top:0">我的积分</h2>

    <div v-if="error" class="error-bar">{{ error }}</div>
    <div v-if="!summary && !error" class="muted">加载中…</div>

    <div v-if="summary" class="grid" style="grid-template-columns:repeat(auto-fill,minmax(180px,1fr))">
      <div class="panel">
        <div class="muted" style="font-size:13px">可用积分</div>
        <div class="price" style="font-size:26px">{{ summary.balance }}</div>
      </div>
      <div class="panel">
        <div class="muted" style="font-size:13px">冻结积分</div>
        <div class="price" style="font-size:26px;color:var(--warn)">{{ summary.frozen }}</div>
      </div>
    </div>

    <h3 style="margin:28px 0 12px">积分流水</h3>
    <div v-if="summary && summary.flows.length === 0" class="panel muted">暂无流水</div>
    <div v-else-if="summary" class="panel" style="padding:0">
      <table>
        <thead>
          <tr>
            <th>类型</th>
            <th>变动</th>
            <th>变动后余额</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="flow in summary.flows" :key="flow.id">
            <td>{{ typeText(flow.type) }}</td>
            <td :class="flow.amount > 0 ? 'ok' : 'danger'">
              {{ flow.amount > 0 ? '+' : '' }}{{ flow.amount }}
            </td>
            <td>{{ flow.balanceAfter }}</td>
            <td class="muted">{{ formatTime(flow.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="muted" style="font-size:12px;margin-top:16px">
      充值功能尚未开放，当前积分来自演示账号初始化。
    </p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchPoints } from '../api/point'

const summary = ref(null)
const error = ref('')

function typeText(type) {
  const map = {
    RECHARGE: '充值',
    FREEZE: '下单冻结',
    DEDUCT: '支付扣减',
    UNFREEZE: '取消解冻',
    REFUND: '退款'
  }
  return map[type] || type
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function load() {
  error.value = ''
  try {
    summary.value = await fetchPoints(30)
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>
