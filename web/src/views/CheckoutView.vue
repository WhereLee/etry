<template>
  <div class="page" style="max-width:680px">
    <p class="muted" style="margin-top:0">
      <RouterLink :to="product ? `/products/${product.id}` : '/'">← 返回</RouterLink>
    </p>

    <h2 style="margin-top:0">确认下单</h2>

    <div v-if="error" class="error-bar">{{ error }}</div>
    <div v-if="!product && !error" class="muted">加载中…</div>

    <div v-if="product" class="panel">
      <table>
        <thead>
          <tr>
            <th>商品</th>
            <th>单价</th>
            <th>数量</th>
            <th>小计</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{{ product.name }}</td>
            <td>{{ product.pricePoints }}</td>
            <td>{{ quantity }}</td>
            <td>{{ totalPoints }}</td>
          </tr>
        </tbody>
      </table>

      <div class="field" style="margin-top:20px">
        <label>收货地址</label>
        <input v-model.trim="address" maxlength="200" placeholder="选填，最多 200 字" />
      </div>

      <div class="between" style="margin-top:18px">
        <span>
          需冻结 <strong class="price">{{ totalPoints }}</strong> 积分
        </span>
        <button :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>

      <p class="muted" style="font-size:12px;margin:12px 0 0">
        重复点击不会生成多张订单：本次提交使用固定的幂等键，重试会复用同一个键。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchProduct } from '../api/product'
import { createOrder } from '../api/order'

const route = useRoute()
const router = useRouter()

const product = ref(null)
const quantity = ref(1)
const address = ref('')
const submitting = ref(false)
const error = ref('')

/**
 * 幂等键在进入本页时生成一次，之后无论重试多少次都用同一个。
 * 只有在提交成功、或用户重新进入本页时才会换成新的键。
 */
const idempotencyKey = ref(createKey())

const totalPoints = computed(() => (product.value ? product.value.pricePoints * quantity.value : 0))

function createKey() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function load() {
  error.value = ''
  const id = route.query.productId
  quantity.value = Number(route.query.quantity) || 1
  if (!id) {
    error.value = '缺少商品信息，请从商品页重新进入'
    return
  }
  try {
    product.value = await fetchProduct(id)
  } catch (e) {
    error.value = e.message
  }
}

async function submit() {
  error.value = ''
  if (!product.value) return
  submitting.value = true
  try {
    const order = await createOrder(
      {
        items: [{ productId: product.value.id, quantity: quantity.value }],
        address: address.value || null
      },
      idempotencyKey.value
    )
    router.replace({ name: 'order-detail', params: { id: order.id } })
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
