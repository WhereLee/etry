<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getCourse, playLesson } from '../api/course'
import { courseProgress, reportProgress } from '../api/learning'

const route = useRoute()
const courseId = route.params.id

const course = ref(null)
const progress = ref(null)
const current = ref(null)
const playUrl = ref('')
const error = ref('')
const loading = ref(true)

const videoRef = ref(null)
let lastReportAt = 0

const lessons = computed(() => {
  if (!course.value) return []
  return course.value.chapters.flatMap((ch) =>
    ch.lessons.map((l) => ({ ...l, chapterTitle: ch.title }))
  )
})

const percent = computed(() => progress.value?.percent ?? 0)

function progressOf(lessonId) {
  return progress.value?.lessons?.find((l) => l.id === lessonId)
}

async function refreshProgress() {
  try {
    progress.value = await courseProgress(courseId)
  } catch {
    // 进度拿不到不影响播放
  }
}

async function select(lesson) {
  if (!lesson.hasVideo) {
    error.value = '这个课时还没上传视频'
    return
  }
  error.value = ''
  reportNow()
  try {
    const data = await playLesson(lesson.id)
    current.value = lesson
    playUrl.value = data.playUrl
    const saved = progressOf(lesson.id)?.positionSeconds || 0
    setTimeout(() => {
      const video = videoRef.value
      if (video && saved > 0 && saved < (lesson.durationSeconds || Infinity)) {
        video.currentTime = saved
      }
    }, 200)
  } catch (e) {
    // 顺序课会返回 40015「请先学完上一节」
    error.value = e.message
  }
}

function reportNow() {
  const video = videoRef.value
  if (!video || !current.value) return
  const position = Math.floor(video.currentTime || 0)
  lastReportAt = Date.now()
  reportProgress(current.value.id, position).catch(() => {})
}

function onTimeUpdate() {
  if (Date.now() - lastReportAt < 10000) return
  reportNow()
}

function onEnded() {
  reportNow()
  refreshProgress()
}

function autoSelect() {
  if (!lessons.value.length) return
  const saved = progress.value?.lessons || []
  const next =
    lessons.value.find((l) => l.hasVideo && !saved.find((s) => s.id === l.id && s.completed)) ||
    lessons.value.find((l) => l.hasVideo)
  if (next) select(next)
}

onMounted(async () => {
  loading.value = true
  try {
    course.value = await getCourse(courseId)
    await refreshProgress()
    autoSelect()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  reportNow()
})
</script>

<template>
  <div class="page learn">
    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>

    <div class="layout" v-if="course">
      <aside class="side panel">
        <div class="side-head">
          <div class="side-title">{{ course.title }}</div>
          <div class="muted small">已完成 {{ progress?.completedLessons || 0 }} / {{ progress?.totalLessons || 0 }}</div>
          <div class="bar"><span :style="{ width: percent + '%' }"></span></div>
          <div class="muted small">{{ percent }}%</div>
          <div class="muted small" v-if="course.sequential">这门课要求按顺序学习</div>
        </div>

        <div v-for="ch in course.chapters" :key="ch.id">
          <div class="chapter">{{ ch.title }}</div>
          <div
            v-for="l in ch.lessons"
            :key="l.id"
            class="lesson"
            :class="{
              active: current && current.id === l.id,
              done: progressOf(l.id)?.completed,
              disabled: !l.hasVideo
            }"
            @click="select(l)"
          >
            <span class="title">{{ l.title }}</span>
            <span class="muted small" v-if="progressOf(l.id)?.completed">已学完</span>
            <span class="muted small" v-else-if="!l.hasVideo">待上传</span>
          </div>
        </div>
      </aside>

      <main class="main">
        <div class="player" v-if="playUrl">
          <video
            ref="videoRef"
            :src="playUrl"
            controls
            playsinline
            @timeupdate="onTimeUpdate"
            @pause="reportNow"
            @ended="onEnded"
          ></video>
        </div>
        <div class="empty panel" v-else>从左侧选一个课时开始</div>

        <div class="panel meta" v-if="current">
          <h3>{{ current.title }}</h3>
          <div class="muted small">{{ current.chapterTitle }}</div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: start;
}

.side {
  padding: 0;
  overflow: hidden;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
}

.side-head {
  padding: 14px;
  border-bottom: 1px solid var(--border);
}

.side-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.bar {
  height: 6px;
  background: #eef1f5;
  border-radius: 3px;
  overflow: hidden;
  margin: 6px 0;
}

.bar span {
  display: block;
  height: 100%;
  background: var(--primary);
}

.chapter {
  padding: 10px 14px 4px;
  font-size: 12px;
  color: var(--muted);
  background: #fafbfc;
}

.lesson {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
}

.lesson:hover {
  background: #f7f9fc;
}

.lesson.active {
  border-left-color: var(--primary);
  background: var(--primary-weak);
}

.lesson.done .title {
  color: var(--muted);
}

.lesson.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.lesson .title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player video {
  width: 100%;
  display: block;
  border-radius: var(--radius);
  background: #000;
}

.meta {
  margin-top: 16px;
}

.meta h3 {
  margin: 0 0 4px;
  font-size: 15px;
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
