<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createOrder, listCatalog } from '../api/order'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const items = ref([])
const loading = ref(true)
const error = ref('')
const buyingId = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listCatalog()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function priceText(cents, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format((cents || 0) / 100)
}

async function buy(item) {
  if (item.owned) {
    router.push({ name: 'learning' })
    return
  }
  buyingId.value = item.seriesId
  error.value = ''
  try {
    const requestId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
    const order = await createOrder(item.seriesId, requestId)
    router.push({ name: 'orderDetail', params: { orderNo: order.orderNo } })
  } catch (e) {
    error.value = e.message
    await load()
  } finally {
    buyingId.value = null
  }
}

function actionText(item) {
  if (item.owned) return '已拥有，进入学习'
  if (item.activeOrderNo) {
    return item.activeOrderStatus === 'PAYING' ? '继续支付' : '继续处理订单'
  }
  return '购买课程'
}
</script>

<template>
  <main class="page store-page">
    <PageHeader
      eyebrow="COURSE STORE"
      title="课程商店"
      description="按系列购买录播课程。支付成功后，课程会自动加入你的学习空间。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" class="feedback" />

    <div v-if="loading" class="store-grid">
      <div v-for="item in 3" :key="item" class="course-card skeleton-card">
        <el-skeleton animated :rows="5" />
      </div>
    </div>

    <div v-else-if="!items.length" class="empty-panel">
      <el-empty description="当前没有正在销售的课程" />
    </div>

    <section v-else class="store-grid">
      <article v-for="(item, index) in items" :key="item.seriesId" class="course-card" :class="{ owned: item.owned }">
        <div class="card-index">{{ String(index + 1).padStart(2, '0') }}</div>
        <div class="card-head">
          <el-tag v-if="item.owned" type="success" effect="plain" round>已拥有</el-tag>
          <el-tag v-else effect="plain" round>录播系列</el-tag>
          <span>{{ item.videoCount }} 节视频</span>
        </div>
        <h2>{{ item.title }}</h2>
        <p>{{ item.summary || '暂无课程简介' }}</p>
        <div class="course-meta">
          <span>{{ item.teacherName }}</span>
          <span>{{ item.chapterCount }} 章 · {{ item.videoCount }} 节</span>
        </div>
        <div class="price-row">
          <div>
            <span class="price-label">课程价格</span>
            <strong>{{ priceText(item.priceCents, item.currency) }}</strong>
          </div>
          <span class="price-note">一次购买 · 长期有效</span>
        </div>
        <el-button
          type="primary"
          size="large"
          class="buy-button"
          :loading="buyingId === item.seriesId"
          @click="buy(item)"
        >
          {{ actionText(item) }}
          <el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
      </article>
    </section>
  </main>
</template>

<style scoped>
.store-page {
  padding-top: 42px;
}

.feedback {
  margin-bottom: 18px;
}

.store-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.course-card {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  padding: 25px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.course-card::before {
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: var(--clay);
  content: '';
}

.course-card.owned::before {
  background: var(--pine);
}

.skeleton-card {
  min-height: 320px;
}

.card-index {
  position: absolute;
  top: 19px;
  right: 22px;
  color: #d9d3c7;
  font-family: var(--font-mono);
  font-size: 11px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 28px;
  color: var(--muted);
  font-size: 11px;
}

h2 {
  margin: 22px 0 9px;
  font-family: var(--font-display);
  font-size: 26px;
  letter-spacing: -0.04em;
}

p {
  min-height: 42px;
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.course-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  color: var(--muted);
  font-size: 11px;
}

.price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin: 22px 0 18px;
}

.price-row > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.price-label,
.price-note {
  color: var(--muted);
  font-size: 10px;
}

.price-row strong {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 28px;
  line-height: 1;
}

.buy-button {
  width: 100%;
}

.empty-panel {
  padding: 40px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

@media (max-width: 760px) {
  .store-page {
    padding-top: 28px;
  }

  .store-grid {
    grid-template-columns: 1fr;
  }
}
</style>
