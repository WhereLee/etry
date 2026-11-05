<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOrders } from '@/features/commerce/api/order'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const pageData = ref({ records: [], total: 0, page: 1, size: 10 })
const filters = reactive({ page: 1, size: 10 })

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    pageData.value = await listOrders(filters)
    filters.page = pageData.value.page
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
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

function priceText(cents, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format((cents || 0) / 100)
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}
</script>

<template>
  <main class="page orders-page">
    <PageHeader eyebrow="MY ORDERS" title="我的订单" description="查看课程购买记录、支付状态和退款结果。">
      <template #actions>
        <el-button @click="router.push({ name: 'courseStore' })">前往课程商店</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" class="feedback" />

    <section class="order-panel">
      <el-table v-loading="loading" :data="pageData.records" row-key="orderNo" stripe>
        <el-table-column label="订单" min-width="210">
          <template #default="{ row }">
            <div class="order-cell">
              <strong>{{ row.seriesTitle }}</strong>
              <span>{{ row.orderNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }"><strong class="amount">{{ priceText(row.priceCents, row.currency) }}</strong></template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag :status="row.status" :label="row.statusLabel" /></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150">
          <template #default="{ row }"><span class="date-text">{{ formatDate(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'orderDetail', params: { orderNo: row.orderNo } })">查看订单</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无订单" /></template>
      </el-table>
      <footer class="pager">
        <span>共 {{ pageData.total }} 条订单</span>
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
  </main>
</template>

<style scoped>
.feedback {
  margin-bottom: 16px;
}

.order-panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.order-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.order-cell strong {
  font-size: 13px;
}

.order-cell span,
.date-text {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.amount {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 16px;
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

@media (max-width: 720px) {
  .pager {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
