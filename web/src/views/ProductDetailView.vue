<template>
  <div class="page" style="max-width:720px">
    <p class="muted" style="margin-top:0">
      <RouterLink to="/">← 返回商品列表</RouterLink>
    </p>

    <div v-if="error" class="error-bar">{{ error }}</div>
    <div v-if="!product && !error" class="muted">加载中…</div>

    <div v-if="product" class="panel">
      <div class="between">
        <h2 style="margin:0">{{ product.name }}</h2>
        <span class="tag">{{ product.category }}</span>
      </div>
      <p class="muted" style="margin:10px 0 18px">{{ product.description }}</p>

      <div class="between" style="margin-bottom:18px">
        <span class="price">{{ product.pricePoints }} 积分</span>
        <span :class="product.availableStock <= 5 ? 'danger' : 'muted'">
          可售 {{ product.availableStock }} 件
        </span>
      </div>

      <div class="row" style="margin-bottom:20px">
        <span class="muted">数量</span>
        <button class="ghost" :disabled="quantity <= 1" @click="quantity--">-</button>
        <input v-model.number="quantity" type="number" min="1" :max="maxQuantity" style="width:80px;text-align:center" />
        <button class="ghost" :disabled="quantity >= maxQuantity" @click="quantity++">+</button>
        <span class="muted" style="font-size:13px">最多 {{ maxQuantity }} 件</span>
      </div>

      <div class="between">
        <span>合计 <strong class="price">{{ totalPoints }}</strong> 积分</span>
        <button :disabled="product.availableStock <= 0" @click="goCheckout">
          {{ product.availableStock <= 0 ? '已售罄' : '立即下单' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchProduct } from '../api/product'

const route = useRoute()
const router = useRouter()

const product = ref(null)
const quantity = ref(1)
const error = ref('')

const maxQuantity = computed(() => {
  if (!product.value) return 1
  return Math.min(99, Math.max(1, product.value.availableStock))
})

const totalPoints = computed(() => (product.value ? product.value.pricePoints * quantity.value : 0))

async function load() {
  error.value = ''
  try {
    product.value = await fetchProduct(route.params.id)
  } catch (e) {
    error.value = e.message
  }
}

function goCheckout() {
  const qty = Math.min(Math.max(1, quantity.value), maxQuantity.value)
  router.push({
    name: 'checkout',
    query: { productId: product.value.id, quantity: qty }
  })
}

onMounted(load)
</script>
