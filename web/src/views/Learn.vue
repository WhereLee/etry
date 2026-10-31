<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourse, playLesson } from '../api/course'
import { courseProgress, reportProgress } from '../api/learning'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id

const course = ref(null)
const progress = ref(null)
const current = ref(null)
const playUrl = ref('')
const error = ref('')
const loading = ref(true)
/** 整门刚学完：弹一次出口，关掉就不再弹 */
const finished = ref(false)
/** 讲师增删课时后的提醒。进度会按新总数重算，不说一句用户会以为进度丢了 */
const lessonNotice = ref('')

const videoRef = ref(null)
let lastReportAt = 0
/** 链接失效只自动重试一次，避免坏文件无限重试 */
let retriedOnce = false

const lessons = computed(() => {
  if (!course.value) return []
  return course.value.chapters.flatMap((ch) =>
    ch.lessons.map((l) => ({ ...l, chapterTitle: ch.title }))
  )
})

const percent = computed(() => progress.value?.percent ?? 0)
const remaining = computed(() => {
  const total = progress.value?.totalLessons ?? 0
  const done = progress.value?.completedLessons ?? 0
  return Math.max(0, total - done)
})

/** 后端 LessonProgressVO 的字段是 lessonId，不是 id */
function progressOf(lessonId) {
  return progress.value?.lessons?.find((l) => l.lessonId === lessonId)
}

/** 单节看到多少：用最远位置算，回看开头不会让这个数字倒退 */
function seenPercent(lesson) {
  const item = progressOf(lesson.id)
  if (!item || !lesson.durationSeconds) return 0
  return Math.min(100, Math.round(((item.furthestSeconds || 0) / lesson.durationSeconds) * 100))
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
  await loadPlayUrl(lesson)
}

async function loadPlayUrl(lesson) {
  try {
    const data = await playLesson(lesson.id)
    current.value = lesson
    playUrl.value = data.playUrl
    retriedOnce = false
    // 续播用 positionSeconds（最后停在哪），不用最远位置
    const saved = progressOf(lesson.id)?.positionSeconds || 0
    setTimeout(() => {
      const video = videoRef.value
      if (video && saved > 0 && saved < (lesson.durationSeconds || Infinity)) {
        video.currentTime = saved
      }
    }, 200)
  } catch (e) {
    // 顺序课会返回 40015「请先学完上一节」；课程下架会返回 40408
    error.value = e.message
  }
}

/** 预签名链接过期或网络抖动：自己换一次新链接，不打断用户 */
async function onVideoError() {
  if (!current.value) return
  if (retriedOnce) {
    error.value = '视频加载失败，可以点下面重试'
    return
  }
  retriedOnce = true
  await loadPlayUrl(current.value)
}

function retry() {
  if (!current.value) return
  error.value = ''
  retriedOnce = false
  loadPlayUrl(current.value)
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

async function onEnded() {
  reportNow()
  await refreshProgress()
  // 整门学完是一个不可逆的时刻，也是唯一一次用户愿意写评价的时候，必须给出口
  if (progress.value && progress.value.totalLessons > 0
      && progress.value.completedLessons >= progress.value.totalLessons) {
    finished.value = true
  }
}

function autoSelect() {
  if (!lessons.value.length) return
  // 从详情页点某一节进来时，优先播那一节
  const wanted = Number(route.query.lesson)
  if (wanted) {
    const target = lessons.value.find((l) => l.id === wanted && l.hasVideo)
    if (target) {
      select(target)
      return
    }
  }
  const saved = progress.value?.lessons || []
  const next =
    lessons.value.find((l) => l.hasVideo && !saved.find((s) => s.lessonId === l.id && s.completed)) ||
    lessons.value.find((l) => l.hasVideo)
  if (next) select(next)
}

onMounted(async () => {
  loading.value = true
  try {
    course.value = await getCourse(courseId)
    await refreshProgress()
    checkLessonCount()
    autoSelect()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

/**
 * 课时数变了就说一句。
 * 存在本地而不是服务端：这只是“给用户一个解释”的体验措施，
 * 不该为了它每次打开学习页都往数据库写一笔。
 */
function checkLessonCount() {
  const key = `etry.course.${courseId}.lessons`
  const prev = Number(localStorage.getItem(key) || 0)
  const now = lessons.value.filter((l) => l.hasVideo).length
  if (prev > 0 && now > 0 && prev !== now) {
    lessonNotice.value = now > prev
      ? `这门课新增了 ${now - prev} 节，进度已按新的总课时重算。`
      : `这门课减少了 ${prev - now} 节，进度已按新的总课时重算。`
  }
  localStorage.setItem(key, String(now))
}

onBeforeUnmount(() => {
  reportNow()
})
</script>

<template>
  <div class="page learn">
    <p class="error" v-if="error">{{ error }}</p>
    <p class="notice" v-if="lessonNotice">{{ lessonNotice }}</p>
    <div class="empty" v-if="loading">加载中…</div>

    <div class="layout" v-if="course">
      <aside class="side panel">
        <div class="side-head">
          <div class="side-title">{{ course.title }}</div>
          <div class="muted small">
            已完成 {{ progress?.completedLessons || 0 }} / {{ progress?.totalLessons || 0 }} 节
            <span v-if="remaining > 0">，还差 {{ remaining }} 节</span>
          </div>
          <div class="bar"><span :style="{ width: percent + '%' }"></span></div>
          <div class="muted small">{{ percent }}%（已学完课时 ÷ 总课时）</div>
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
            <span class="muted small" v-else-if="seenPercent(l) > 0">看过 {{ seenPercent(l) }}%</span>
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
            @error="onVideoError"
          ></video>
          <button v-if="error" class="retry-btn" @click="retry">重试播放</button>
        </div>
        <div class="empty panel" v-else>从左侧选一个课时开始</div>

        <div class="panel meta" v-if="current">
          <h3>{{ current.title }}</h3>
          <div class="muted small">{{ current.chapterTitle }}</div>
        </div>
      </main>
    </div>

    <div class="done-mask" v-if="finished">
      <div class="done-card panel">
        <h3>这门课学完了</h3>
        <p class="muted small">全部 {{ progress?.totalLessons }} 节都完成了。</p>
        <div class="row">
          <button class="primary" @click="router.push({ name: 'course', params: { id: courseId } })">去评价</button>
          <button @click="router.push({ name: 'courses' })">找别的课</button>
        </div>
        <button class="link-btn" @click="finished = false">先不评价</button>
      </div>
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

.retry-btn {
  margin-top: 8px;
}

.done-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}

.done-card {
  width: 340px;
  text-align: center;
}

.done-card h3 {
  margin: 0 0 6px;
}

.done-card .row {
  justify-content: center;
  margin: 14px 0 10px;
}

.link-btn {
  border: none;
  background: none;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
