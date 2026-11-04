<script setup>
import { computed, onMounted, ref } from 'vue'
import { listSeries } from '../api/learning'
import { session } from '../store/session'

const series = ref([])
const loading = ref(true)
const error = ref('')

const displayName = computed(() => session.user?.nickname || session.user?.username || '同学')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const totalVideos = computed(() => series.value.reduce((total, item) => total + (item.videoCount || 0), 0))
const totalCompleted = computed(() => series.value.reduce((total, item) => total + (item.completedVideoCount || 0), 0))

async function load() {
  loading.value = true
  error.value = ''
  try {
    series.value = await listSeries()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

function progressPercent(item) {
  if (!item.videoCount) return 0
  return Math.round((item.completedVideoCount / item.videoCount) * 100)
}

function actionText(item) {
  if (item.videoCount > 0 && item.completedVideoCount >= item.videoCount) return '回顾课程'
  return item.completedVideoCount > 0 ? '继续学习' : '开始学习'
}

function serial(index) {
  return String(index + 1).padStart(2, '0')
}
</script>

<template>
  <main class="page learning-page">
    <section class="welcome">
      <div class="welcome-copy">
        <div class="eyebrow">LEARNING LIBRARY</div>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p>课程按系列陈列。进入一个系列，从上次停下的位置继续。</p>
      </div>
      <div class="overview" aria-label="学习概览">
        <div class="overview-item">
          <strong>{{ series.length }}</strong>
          <span>系列课程</span>
        </div>
        <div class="overview-item">
          <strong>{{ totalVideos }}</strong>
          <span>课程视频</span>
        </div>
        <div class="overview-item accent">
          <strong>{{ totalCompleted }}</strong>
          <span>已完成</span>
        </div>
      </div>
    </section>

    <div class="shelf-head">
      <div>
        <span class="shelf-title">课程书架</span>
        <span class="shelf-hint">按分配内容展示</span>
      </div>
      <span class="shelf-count">{{ series.length }} 个系列</span>
    </div>

    <div v-if="loading" class="loading-panel panel">
      <el-skeleton animated :rows="5" />
    </div>

    <el-alert v-else-if="error" type="error" :closable="false" show-icon class="feedback-alert">
      <template #title>{{ error }}</template>
      <el-button type="primary" link @click="load">重新加载</el-button>
    </el-alert>

    <div v-else-if="!series.length" class="empty-panel panel">
      <el-empty description="当前账号还没有分配系列课程">
        <span class="empty-note">请联系管理员为你分配学习内容。</span>
      </el-empty>
    </div>

    <section v-else class="series-grid">
      <RouterLink
        v-for="(item, index) in series"
        :key="item.id"
        :to="{ name: 'series', params: { id: item.id } }"
        class="series-card"
        :style="{ '--card-index': serial(index) }"
      >
        <span class="card-index">{{ serial(index) }}</span>
        <div class="card-top">
          <el-tag effect="plain" round>录播系列</el-tag>
          <span class="card-count">{{ item.videoCount }} 节视频</span>
        </div>
        <h2>{{ item.title }}</h2>
        <p class="summary">{{ item.summary || '暂无课程简介' }}</p>
        <div class="card-meta">
          <span>{{ item.teacherName }}</span>
          <span>{{ item.chapterCount }} 章 · {{ item.videoCount }} 节</span>
        </div>
        <div class="progress-block">
          <div class="progress-copy">
            <span>学习进度</span>
            <span>{{ item.completedVideoCount }} / {{ item.videoCount }}</span>
          </div>
          <el-progress :percentage="progressPercent(item)" :stroke-width="6" :show-text="false" />
        </div>
        <div class="card-action">
          <span>{{ actionText(item) }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </RouterLink>
    </section>
  </main>
</template>

<style scoped>
.learning-page {
  padding-top: 48px;
}

.welcome {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding-bottom: 34px;
  border-bottom: 1px solid var(--border-strong);
}

.welcome-copy h1 {
  margin: 10px 0 12px;
  font-family: var(--font-display);
  font-size: clamp(2.5rem, 5vw, 4.4rem);
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 1.08;
}

.welcome-copy p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.overview {
  display: flex;
  flex: none;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.72);
}

.overview-item {
  display: flex;
  min-width: 92px;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
}

.overview-item + .overview-item {
  border-left: 1px solid var(--border);
}

.overview-item strong {
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1;
}

.overview-item span {
  color: var(--muted);
  font-size: 11px;
}

.overview-item.accent strong {
  color: var(--clay);
}

.shelf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 32px 0 18px;
}

.shelf-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
}

.shelf-hint {
  margin-left: 12px;
  color: var(--muted);
  font-size: 12px;
}

.shelf-count {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.loading-panel,
.empty-panel {
  padding: 30px;
}

.feedback-alert {
  margin-bottom: 20px;
}

.empty-note {
  color: var(--muted);
  font-size: 13px;
}

.series-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.series-card {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  padding: 24px 24px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.series-card::before {
  position: absolute;
  top: 0;
  left: 24px;
  width: 54px;
  height: 3px;
  background: var(--clay);
  content: '';
}

.series-card:hover {
  transform: translateY(-3px);
  border-color: var(--pine);
  box-shadow: var(--shadow-md);
}

.series-card:focus-visible {
  outline-offset: 4px;
}

.card-index {
  position: absolute;
  top: 17px;
  right: 22px;
  color: #d9d3c7;
  font-family: var(--font-mono);
  font-size: 12px;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 30px;
}

.card-count {
  color: var(--muted);
  font-size: 12px;
}

h2 {
  margin: 22px 0 9px;
  font-family: var(--font-display);
  font-size: 25px;
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
  padding-top: 15px;
  border-top: 1px solid var(--border);
  color: var(--muted);
  font-size: 12px;
}

.progress-block {
  margin-top: 18px;
}

.progress-copy {
  display: flex;
  justify-content: space-between;
  margin-bottom: 7px;
  color: var(--muted);
  font-size: 11px;
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

.card-action .el-icon {
  transition: transform 0.18s ease;
}

.series-card:hover .card-action .el-icon {
  transform: translateX(4px);
}

@media (max-width: 860px) {
  .welcome {
    align-items: flex-start;
    flex-direction: column;
  }

  .overview {
    width: 100%;
  }

  .overview-item {
    flex: 1;
  }
}

@media (max-width: 680px) {
  .series-grid {
    grid-template-columns: 1fr;
  }

  .overview-item {
    min-width: 0;
    padding: 14px;
  }

  .shelf-hint {
    display: none;
  }
}
</style>
