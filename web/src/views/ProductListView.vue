<template>
  <div class="page">
    <div class="between" style="margin-bottom:18px">
      <h2 style="margin:0">商品</h2>
      <div class="row">
        <button
          v-for="item in categories"
          :key="item.value"
          class="ghost"
          :style="active === item.value ? 'border-color:var(--primary);color:var(--primary)' : ''"
          @click="changeCategory(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>

    <div v-if="loading" class="muted">加载中…</div>
    <div v-else-if="records.length === 0" class="panel muted">暂无商品</div>

    <div v-else class="grid">
      <div v-for="item in records" :key="item.id" class="card" @click="goDetail(item.id)">
        <div class="between">
          <strong>{{ item.name }}</strong>
          <span class="tag">{{ item.category }}</span>
        </div>
        <div class="muted" style="font-size:13px;min-height:38px">{{ item.description }}</div>
        <div class="between">
          <span class="price">{{ item.pricePoints }} 积分</span>
          <span :class="item.availableStock <= 5 ? 'danger' : 'muted'" style="font-size:13px">
            可售 {{ item.availableStock }}
          </span>
        </div>
      </div>
    </div>

    <div class="between" style="margin-top:22px">
      <span class="muted">共 {{ total }} 件</span>
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
import { useRouter } from 'vue-router'
import { fetchProducts } from '../api/product'

const router = useRouter()

const categories = [
  { label: '全部', value: '' },
  { label: '数码配件', value: '数码配件' },
  { label: '桌面配件', value: '桌面配件' },
  { label: '音频', value: '音频' }
]

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 12
const active = ref('')
const loading = ref(false)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchProducts({ page: page.value, size, category: active.value || undefined })
    records.value = data.records
    total.value = data.total
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function changeCategory(value) {
  active.value = value
  page.value = 1
  load()
}

function go(next) {
  page.value = next
  load()
}

function goDetail(id) {
  router.push({ name: 'product-detail', params: { id } })
}

onMounted(load)
</script>
