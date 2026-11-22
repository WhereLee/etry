<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CATEGORIES,
  CATEGORY_LABELS,
  STAGE_LABELS,
  listMaterials,
  priceText,
  searchMaterials
} from '@/features/material/api/material'
import { isLoggedIn } from '@/store/session'
import { useCartStore } from '@/store/cart'

const router = useRouter()
const cartStore = useCartStore()

const items = ref([])
const total = ref(0)
const page = ref(1)
const size = 12
const category = ref('')
const keyword = ref('')
const searching = ref(false)
const loading = ref(true)
const error = ref('')
const addingId = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (searching.value && keyword.value.trim()) {
      items.value = await searchMaterials(keyword.value.trim())
      total.value = items.value.length
    } else {
      searching.value = false
      const data = await listMaterials({ category: category.value || undefined, page: page.value, size })
      items.value = data.items
      total.value = data.total
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function pickCategory(value) {
  category.value = value
  searching.value = false
  keyword.value = ''
  page.value = 1
  load()
}

function submitSearch() {
  if (!keyword.value.trim()) {
    searching.value = false
    page.value = 1
  } else {
    searching.value = true
  }
  load()
}

function changePage(value) {
  page.value = value
  load()
}

async function add(item) {
  if (!isLoggedIn()) {
    router.push({ name: 'login', query: { redirect: '/materials' } })
    return
  }
  addingId.value = item.id
  try {
    await cartStore.add(item.id)
    ElMessage.success('已加入购物车')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    addingId.value = null
  }
}
</script>

<template>
  <div>
    <header v-if="!isLoggedIn()" class="guest-bar">
      <RouterLink to="/materials" class="guest-brand">
        <span class="brand-seal">学</span>
        <strong>学习资料商城</strong>
      </RouterLink>
      <span class="spacer" />
      <RouterLink :to="{ name: 'login', query: { redirect: '/materials' } }">登录</RouterLink>
      <RouterLink :to="{ name: 'register', query: { redirect: '/materials' } }" class="guest-register">注册</RouterLink>
    </header>

    <main class="page material-page">
      <div class="eyebrow">MATERIAL STORE</div>
      <div class="page-head">
        <div>
          <h1>学习资料商城</h1>
          <p class="page-desc">电子版试卷、教辅、讲义与课本。购买后可在线预览与下载。</p>
        </div>
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="搜索资料标题或简介"
            clearable
            size="large"
            @keyup.enter="submitSearch"
            @clear="submitSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" size="large" @click="submitSearch">搜索</el-button>
        </div>
      </div>

      <div class="category-tabs">
        <button
          v-for="c in CATEGORIES"
          :key="c.value"
          type="button"
          class="category-tab"
          :class="{ active: !searching && category === c.value }"
          @click="pickCategory(c.value)"
        >
          {{ c.label }}
        </button>
        <span v-if="searching" class="search-hint">“{{ keyword }}” 的搜索结果（{{ total }} 条）</span>
      </div>

      <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" class="feedback" />

      <div v-if="loading" class="material-grid">
        <div v-for="i in 6" :key="i" class="material-card">
          <el-skeleton animated :rows="4" />
        </div>
      </div>

      <div v-else-if="!items.length" class="empty-panel">
        <el-empty :description="searching ? '没有找到相关资料' : '当前分类暂无在售资料'" />
      </div>

      <section v-else class="material-grid">
        <article v-for="item in items" :key="item.id" class="material-card">
          <RouterLink :to="{ name: 'materialDetail', params: { id: item.id } }" class="card-link">
            <div class="card-tags">
              <el-tag effect="plain" round size="small">{{ CATEGORY_LABELS[item.category] || item.category }}</el-tag>
              <el-tag v-if="item.stage && item.stage !== 'GENERAL'" effect="plain" round size="small" type="info">
                {{ STAGE_LABELS[item.stage] || item.stage }}
              </el-tag>
              <span v-if="item.subject" class="subject">{{ item.subject }}</span>
            </div>
            <h2>{{ item.title }}</h2>
            <p>{{ item.summary || '暂无简介' }}</p>
            <div class="card-meta">{{ item.pageCount }} 页</div>
          </RouterLink>
          <div class="card-foot">
            <strong class="price">{{ priceText(item.priceCents, item.currency) }}</strong>
            <el-button
              type="primary"
              :loading="addingId === item.id"
              @click="add(item)"
            >
              <el-icon><ShoppingCart /></el-icon>
              加入购物车
            </el-button>
          </div>
        </article>
      </section>

      <el-pagination
        v-if="!searching && total > size"
        class="pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </main>
  </div>
</template>

<style scoped>
.guest-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px max(24px, calc((100vw - 1260px) / 2));
  border-bottom: 1px solid var(--border);
  background: rgba(247, 244, 237, 0.93);
  backdrop-filter: blur(14px);
}

.guest-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-display);
  font-size: 17px;
}

.guest-bar .brand-seal {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 9px 4px 9px 4px;
  background: var(--pine);
  color: #fff8ea;
  font-family: var(--font-display);
  font-weight: 700;
}

.guest-bar .spacer {
  flex: 1;
}

.guest-bar a {
  color: var(--muted);
  font-size: 14px;
}

.guest-bar .guest-register {
  padding: 7px 16px;
  border-radius: 7px;
  background: var(--pine);
  color: #fff8ea;
  font-weight: 700;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
  margin: 10px 0 22px;
}

.page-head h1 {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 34px;
  letter-spacing: -0.045em;
}

.page-desc {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}

.search-box {
  display: flex;
  gap: 10px;
  width: min(420px, 100%);
}

.category-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}

.category-tab {
  padding: 8px 18px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: 13px;
  transition: 0.15s ease;
}

.category-tab:hover {
  border-color: var(--pine);
  color: var(--pine);
}

.category-tab.active {
  border-color: var(--pine);
  background: var(--pine);
  color: #fff8ea;
  font-weight: 700;
}

.search-hint {
  color: var(--muted);
  font-size: 13px;
}

.feedback {
  margin-bottom: 18px;
}

.material-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}

.material-card {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.75);
  transition: 0.18s ease;
}

.material-card:hover {
  border-color: var(--pine);
  box-shadow: 0 8px 24px rgba(31, 54, 44, 0.08);
}

.card-link {
  display: block;
  flex: 1;
  color: inherit;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.card-tags .subject {
  color: var(--muted);
  font-size: 12px;
}

.material-card h2 {
  margin: 0 0 8px;
  font-size: 17px;
  line-height: 1.45;
}

.material-card p {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65;
}

.card-meta {
  color: var(--muted);
  font-size: 12px;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.price {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 20px;
}

.pager {
  justify-content: center;
  margin-top: 26px;
}

.empty-panel {
  padding: 40px 0;
}
</style>