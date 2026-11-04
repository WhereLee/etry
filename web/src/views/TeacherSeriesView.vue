<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTeacherSeries } from '../api/teacher'
import StatusTag from '../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const series = ref(null)
const loading = ref(true)
const error = ref('')

const videoCount = computed(() =>
  (series.value?.chapters || []).reduce((total, chapter) => total + (chapter.videos?.length || 0), 0)
)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    series.value = await getTeacherSeries(route.params.id)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '--:--'
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
</script>

<template>
  <main class="page page-narrow detail-page">
    <RouterLink to="/teacher/series" class="back-link">
      <el-icon><ArrowLeft /></el-icon>
      返回我的系列
    </RouterLink>

    <div v-if="loading" class="panel skeleton-panel"><el-skeleton animated :rows="8" /></div>
    <el-alert v-else-if="error || !series" type="error" :closable="false" show-icon :title="error || '系列不存在'" />

    <template v-else>
      <header class="detail-hero">
        <div>
          <div class="eyebrow">READ ONLY CONTENT</div>
          <h1>{{ series.title }}</h1>
          <p>{{ series.summary || '暂无系列简介' }}</p>
        </div>
        <div class="hero-actions">
          <StatusTag :status="series.status" />
          <el-button type="primary" @click="router.push({ name: 'teacherSeriesEdit', params: { id: series.id } })">
            <el-icon><EditPen /></el-icon>
            编辑系列
          </el-button>
        </div>
      </header>

      <div class="meta-strip">
        <div><span>授课教师</span><strong>{{ series.teacherName }}</strong></div>
        <div><span>课程结构</span><strong>{{ series.chapters.length }} 章 · {{ videoCount }} 节</strong></div>
        <div><span>发布状态</span><StatusTag :status="series.status" /></div>
      </div>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="内容结构调整后系列会自动回到草稿；已拥有权益的学员仍可访问，新学员暂不可见。"
        class="readonly-note"
      />

      <section class="chapters">
        <article v-for="(chapter, index) in series.chapters" :key="chapter.id" class="chapter panel">
          <div class="chapter-head">
            <span class="chapter-no">{{ String(index + 1).padStart(2, '0') }}</span>
            <div>
              <h2>{{ chapter.title }}</h2>
              <span>{{ chapter.videos.length }} 节视频</span>
            </div>
          </div>
          <div v-if="!chapter.videos.length" class="empty-chapter">本章暂无视频</div>
          <ul v-else class="video-list">
            <li v-for="(video, videoIndex) in chapter.videos" :key="video.id">
              <span class="video-no">{{ String(videoIndex + 1).padStart(2, '0') }}</span>
              <span class="video-title">{{ video.title }}</span>
              <span class="duration">{{ formatDuration(video.durationSeconds) }}</span>
            </li>
          </ul>
        </article>
      </section>
    </template>
  </main>
</template>

<style scoped>
.detail-page {
  padding-top: 28px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 26px;
  color: var(--muted);
  font-size: 13px;
}

.back-link:hover {
  color: var(--pine);
}

.skeleton-panel {
  padding: 30px;
}

.detail-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  padding-bottom: 26px;
  border-bottom: 1px solid var(--border-strong);
}

.detail-hero h1 {
  margin: 9px 0 9px;
  font-family: var(--font-display);
  font-size: clamp(2.3rem, 5vw, 3.8rem);
  font-weight: 600;
  letter-spacing: -0.055em;
  line-height: 1.1;
}

.detail-hero p {
  max-width: 650px;
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
}

.meta-strip {
  display: grid;
  margin: 22px 0;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.7);
}

.meta-strip > div {
  display: flex;
  min-height: 70px;
  align-items: flex-start;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  padding: 14px 18px;
}

.meta-strip > div + div {
  border-left: 1px solid var(--border);
}

.meta-strip span:first-child {
  color: var(--muted);
  font-size: 10px;
}

.meta-strip strong {
  font-size: 13px;
}

.readonly-note {
  margin-bottom: 22px;
}

.chapters {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chapter {
  overflow: hidden;
}

.chapter-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 17px 20px;
  border-bottom: 1px solid var(--border);
  background: #faf7f0;
}

.chapter-no {
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 11px;
}

.chapter-head h2 {
  margin: 0 0 3px;
  font-family: var(--font-display);
  font-size: 18px;
  letter-spacing: -0.02em;
}

.chapter-head span {
  color: var(--muted);
  font-size: 10px;
}

.video-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.video-list li {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 12px 20px;
  font-size: 13px;
}

.video-list li + li {
  border-top: 1px solid var(--border);
}

.video-no {
  width: 28px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
}

.video-title {
  flex: 1;
}

.duration {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.empty-chapter {
  padding: 22px 20px;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 720px) {
  .detail-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .meta-strip {
    grid-template-columns: 1fr;
  }

  .meta-strip > div + div {
    border-top: 1px solid var(--border);
    border-left: 0;
  }
}
</style>
