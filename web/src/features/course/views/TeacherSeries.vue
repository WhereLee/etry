<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listTeacherSeries } from '@/features/course/api/teacher'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const router = useRouter()
const series = ref([])
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    series.value = await listTeacherSeries()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}
</script>

<template>
  <main class="page">
    <PageHeader
      eyebrow="TEACHER WORKSPACE"
      title="我的系列"
      description="维护课程结构、视频与发布状态。结构调整后会自动回到草稿，已有权益学员仍可访问。"
    >
      <template #actions>
        <span class="series-count">{{ series.length }} 个系列</span>
        <el-button type="primary" @click="router.push({ name: 'teacherSeriesNew' })">
          <el-icon><Plus /></el-icon>
          新建系列
        </el-button>
      </template>
    </PageHeader>

    <div v-if="loading" class="loading-grid">
      <div v-for="item in 2" :key="item" class="panel skeleton-card">
        <el-skeleton animated :rows="4" />
      </div>
    </div>

    <el-alert v-else-if="error" type="error" :closable="false" show-icon>
      <template #title>{{ error }}</template>
      <el-button type="primary" link @click="load">重新加载</el-button>
    </el-alert>

    <div v-else-if="!series.length" class="panel empty-panel">
      <el-empty description="当前账号还没有负责的系列">
        <el-button type="primary" @click="router.push({ name: 'teacherSeriesNew' })">创建第一个系列</el-button>
      </el-empty>
    </div>

    <section v-else class="series-grid">
      <RouterLink
        v-for="item in series"
        :key="item.id"
        :to="{ name: 'teacherSeriesDetail', params: { id: item.id } }"
        class="series-card"
        :class="`is-${item.status.toLowerCase()}`"
      >
        <div class="card-head">
          <StatusTag :status="item.status" />
          <span class="video-count">{{ item.videoCount }} 节视频</span>
        </div>
        <h2>{{ item.title }}</h2>
        <p class="summary">{{ item.summary || '暂无系列简介' }}</p>
        <div class="card-meta">
          <span>{{ item.chapterCount }} 章 · {{ item.videoCount }} 节</span>
          <span>{{ formatDate(item.updatedAt) }}</span>
        </div>
        <div class="card-action">
          <span>查看内容</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.series-count {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.loading-grid,
.series-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.skeleton-card {
  padding: 24px;
}

.empty-panel {
  padding: 26px;
}

.series-card {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  padding: 23px 24px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: 0.2s ease;
}

.series-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--gold);
  content: '';
}

.series-card.is-published::before {
  background: var(--pine);
}

.series-card.is-archived::before {
  background: #8d938e;
}

.series-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.video-count {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

h2 {
  margin: 20px 0 8px;
  font-family: var(--font-display);
  font-size: 24px;
  letter-spacing: -0.035em;
}

.summary {
  min-height: 44px;
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  color: var(--muted);
  font-size: 12px;
}

.card-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18px;
  color: var(--pine);
  font-size: 13px;
  font-weight: 700;
}

.series-card:hover .card-action .el-icon {
  transform: translateX(4px);
}

.card-action .el-icon {
  transition: transform 0.18s ease;
}

@media (max-width: 760px) {
  .series-grid,
  .loading-grid {
    grid-template-columns: 1fr;
  }
}
</style>
