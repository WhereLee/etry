<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CATEGORY_LABELS,
  STAGE_LABELS,
  getMaterial,
  priceText
} from '@/features/material/api/material'
import { downloadMaterial, fetchLibrary, openPreview } from '@/features/material/api/library'
import { isLoggedIn } from '@/store/session'
import { useCartStore } from '@/store/cart'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const material = ref(null)
const loading = ref(true)
const error = ref('')
const adding = ref(false)
const owned = ref(false)
const downloading = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  owned.value = false
  try {
    material.value = await getMaterial(route.params.id)
    if (isLoggedIn()) {
      try {
        const library = await fetchLibrary()
        owned.value = library.some((item) => item.id === material.value.id)
      } catch {
        owned.value = false
      }
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function preview() {
  openPreview(material.value.id)
}

async function download() {
  downloading.value = true
  try {
    await downloadMaterial(material.value.id, material.value.title)
    ElMessage.success('已开始下载')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    downloading.value = false
  }
}

async function add() {
  if (!isLoggedIn()) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  adding.value = true
  try {
    await cartStore.add(material.value.id)
    ElMessage.success('已加入购物车')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    adding.value = false
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
      <RouterLink :to="{ name: 'login', query: { redirect: route.fullPath } }">登录</RouterLink>
      <RouterLink :to="{ name: 'register', query: { redirect: route.fullPath } }" class="guest-register">注册</RouterLink>
    </header>

    <main class="page detail-page">
      <el-button text class="back-link" @click="router.push('/materials')">
        <el-icon><ArrowLeft /></el-icon>
        返回商城
      </el-button>

      <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" class="feedback" />
      <el-skeleton v-if="loading" animated :rows="8" />

      <section v-else-if="material" class="detail-card">
        <div class="detail-tags">
          <el-tag effect="plain" round>{{ CATEGORY_LABELS[material.category] || material.category }}</el-tag>
          <el-tag v-if="material.stage && material.stage !== 'GENERAL'" effect="plain" round type="info">
            {{ STAGE_LABELS[material.stage] || material.stage }}
          </el-tag>
          <span v-if="material.subject" class="subject">{{ material.subject }}</span>
        </div>
        <h1>{{ material.title }}</h1>
        <p class="summary">{{ material.summary || '暂无简介' }}</p>

        <dl class="detail-meta">
          <div>
            <dt>页数</dt>
            <dd>{{ material.pageCount }} 页</dd>
          </div>
          <div>
            <dt>试读</dt>
            <dd>免费试读前 {{ material.previewPages }} 页</dd>
          </div>
          <div>
            <dt>格式</dt>
            <dd>PDF 电子版</dd>
          </div>
        </dl>

        <div class="buy-row">
          <div class="price-block">
            <span class="price-label">资料价格</span>
            <strong>{{ priceText(material.priceCents, material.currency) }}</strong>
            <small>一次购买 · 长期有效</small>
          </div>
          <div class="action-row">
            <el-button size="large" @click="preview">
              <el-icon><View /></el-icon>
              免费试读
            </el-button>
            <el-button v-if="owned" type="success" size="large" :loading="downloading" @click="download">
              <el-icon><Download /></el-icon>
              下载资料
            </el-button>
            <el-button v-else type="primary" size="large" :loading="adding" @click="add">
              <el-icon><ShoppingCart /></el-icon>
              加入购物车
            </el-button>
          </div>
        </div>
        <p v-if="owned" class="owned-note">已购买 · 下载文件将附带你的专属水印</p>
      </section>
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

.back-link {
  margin-bottom: 14px;
  color: var(--muted);
}

.feedback {
  margin-bottom: 18px;
}

.detail-card {
  max-width: 760px;
  padding: 30px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.75);
}

.detail-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.detail-tags .subject {
  color: var(--muted);
  font-size: 13px;
}

.detail-card h1 {
  margin: 0 0 12px;
  font-family: var(--font-display);
  font-size: 28px;
  letter-spacing: -0.03em;
  line-height: 1.35;
}

.summary {
  margin: 0 0 22px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.8;
}

.detail-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 0 0 24px;
  padding: 16px 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

.detail-meta dt {
  color: var(--muted);
  font-size: 12px;
}

.detail-meta dd {
  margin: 4px 0 0;
  font-size: 14px;
  font-weight: 700;
}

.buy-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
}

.price-block {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.price-label {
  color: var(--muted);
  font-size: 12px;
}

.price-block strong {
  color: var(--clay);
  font-family: var(--font-display);
  font-size: 30px;
}

.price-block small {
  color: var(--muted);
  font-size: 12px;
}

.action-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.owned-note {
  margin: 14px 0 0;
  color: var(--pine);
  font-size: 12px;
}
</style>