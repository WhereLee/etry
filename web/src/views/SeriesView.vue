<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getSeries, playVideo, saveProgress, saveProgressKeepalive } from '../api/learning'
import { reportPlayback } from '../api/playback'

const route = useRoute()
const series = ref(null)
const current = ref(null)
const playUrl = ref('')
const playUrlExpiresAt = ref(0)
const videoRef = ref(null)
const loading = ref(true)
const loadingPlay = ref(false)
const error = ref('')
const sessionId = typeof crypto !== 'undefined' && crypto.randomUUID
  ? crypto.randomUUID()
  : `${Date.now()}-${Math.random().toString(36).slice(2)}`

let retriedOnce = false
let leaving = false
let refreshTimer = null
let refreshing = false
let resumePosition = 0
let lastProgressAt = 0
let lastProgressPosition = -1

const videos = computed(() =>
  (series.value?.chapters || []).flatMap((chapter) => chapter.videos || [])
)
const videoCount = computed(() => videos.value.length)
const completedCount = computed(() => videos.value.filter((video) => video.completed).length)

function report(eventType, extra = {}) {
  const video = videoRef.value
  const currentVideo = current.value
  if (!currentVideo) return
  const position = extra.positionSeconds ?? (video ? Math.floor(video.currentTime || 0) : 0)
  const duration = extra.durationSeconds ?? (
    video && Number.isFinite(video.duration) ? Math.floor(video.duration) : (currentVideo.durationSeconds || 0)
  )
  reportPlayback({
    videoId: currentVideo.id,
    eventType,
    positionSeconds: Math.max(0, position),
    durationSeconds: Math.max(0, duration),
    message: extra.message || null,
    sessionId
  })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    series.value = await getSeries(route.params.id)
    if (videos.value.length) {
      const resume = videos.value.find((video) => !video.completed && video.positionSeconds > 0)
        || videos.value.find((video) => !video.completed)
        || videos.value[0]
      await select(resume, true)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function select(video, force = false) {
  if (!force && current.value?.id === video.id && playUrl.value) return
  if (current.value && videoRef.value) {
    persistProgress(false)
  }
  current.value = video
  resumePosition = video.completed ? 0 : Math.max(0, video.positionSeconds || 0)
  lastProgressAt = 0
  lastProgressPosition = resumePosition
  loadingPlay.value = true
  error.value = ''
  retriedOnce = false
  clearRefreshTimer()
  try {
    const data = await playVideo(video.id)
    playUrlExpiresAt.value = data.urlExpiresAt || 0
    const sameUrl = playUrl.value === data.playUrl
    if (sameUrl) {
      await nextTick()
      if (videoRef.value) {
        applyResumePosition()
        videoRef.value.play().catch(() => {})
      }
    } else {
      playUrl.value = data.playUrl
      await nextTick()
      videoRef.value?.load()
    }
    scheduleRefresh()
  } catch (e) {
    error.value = e.message
    report('ERROR', { message: e.message })
  } finally {
    loadingPlay.value = false
  }
}

function onLoadedMetadata() {
  report('LOADED_METADATA')
  applyResumePosition()
}

function applyResumePosition() {
  const video = videoRef.value
  if (!video || resumePosition <= 0 || !Number.isFinite(video.duration) || video.duration <= 0) {
    return
  }
  const target = Math.min(resumePosition, Math.max(0, video.duration - 1))
  if (target > 0) {
    try {
      video.currentTime = target
    } catch {
      // 元数据尚未就绪时由 loadedmetadata 再次恢复
    }
  }
  resumePosition = 0
}

function onTimeUpdate() {
  if (!current.value || !videoRef.value) return
  const now = Date.now()
  const position = Math.floor(videoRef.value.currentTime || 0)
  if (now - lastProgressAt >= 10000 && Math.abs(position - lastProgressPosition) >= 5) {
    persistProgress(false)
  }
}

function persistProgress(completed, keepalive = false) {
  const video = videoRef.value
  const currentVideo = current.value
  if (!video || !currentVideo || !Number.isFinite(video.duration) || video.duration <= 0) return
  const duration = Math.max(0, Math.floor(video.duration))
  let position = completed ? duration : Math.max(0, Math.floor(video.currentTime || 0))
  const finalCompleted = completed
    || (duration > 0 && position >= Math.max(duration - 3, duration * 0.95))
  if (finalCompleted) position = duration
  const payload = { positionSeconds: position, durationSeconds: duration, completed: finalCompleted }
  currentVideo.positionSeconds = position
  if (finalCompleted) currentVideo.completed = true
  lastProgressAt = Date.now()
  lastProgressPosition = position
  if (keepalive) {
    saveProgressKeepalive(currentVideo.id, payload)
  } else {
    saveProgress(currentVideo.id, payload).catch(() => {})
  }
}

function onPause() {
  if (!leaving) {
    report('PAUSE')
    persistProgress(false)
  }
}

function clearRefreshTimer() {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

function scheduleRefresh() {
  clearRefreshTimer()
  const expiresAt = Number(playUrlExpiresAt.value || 0)
  if (!expiresAt) return
  const remaining = expiresAt * 1000 - Date.now()
  const ahead = Math.min(60000, Math.max(5000, remaining * 0.1))
  const delay = remaining - ahead
  if (delay <= 0) {
    refreshPlaybackUrl('schedule')
    return
  }
  refreshTimer = setTimeout(() => {
    if (current.value && !leaving) refreshPlaybackUrl('schedule')
  }, delay)
}

async function refreshPlaybackUrl(reason = 'manual') {
  if (refreshing || !current.value) return
  persistProgress(false)
  refreshing = true
  const video = videoRef.value
  const saved = video?.currentTime || 0
  const wasPlaying = video ? !video.paused : false
  try {
    report('URL_REFRESH', { positionSeconds: saved, message: reason })
    const data = await playVideo(current.value.id)
    playUrlExpiresAt.value = data.urlExpiresAt || 0
    const sameUrl = playUrl.value === data.playUrl
    playUrl.value = data.playUrl
    await nextTick()
    const element = videoRef.value
    if (element) {
      if (!sameUrl) element.load()
      const restore = () => {
        try {
          element.currentTime = saved
        } catch {
          // URL 已刷新，位置恢复失败时仍可继续播放
        }
        if (wasPlaying) element.play().catch(() => {})
      }
      if (element.readyState >= 1) {
        restore()
      } else {
        element.addEventListener('loadedmetadata', restore, { once: true })
      }
    }
    scheduleRefresh()
  } catch (e) {
    error.value = e.message
    report('ERROR', { message: e.message })
  } finally {
    refreshing = false
  }
}

function onStalled() {
  report('STALLED')
  const expiresAt = Number(playUrlExpiresAt.value || 0)
  if (expiresAt && Date.now() >= expiresAt * 1000 - 120000) {
    refreshPlaybackUrl('stalled')
  }
}

function onSeeked() {
  report('SEEKED')
  persistProgress(false)
}

function onEnded() {
  report('ENDED')
  persistProgress(true)
}

async function onVideoError() {
  const message = videoRef.value?.error
    ? `video error code=${videoRef.value.error.code}`
    : 'video element error'
  report('ERROR', { message })
  if (!current.value) return
  if (retriedOnce) {
    error.value = '视频加载失败，可以点下面重试'
    return
  }
  retriedOnce = true
  await refreshPlaybackUrl('error')
}

async function retry() {
  if (!current.value) return
  error.value = ''
  retriedOnce = false
  await select(current.value, true)
  if (!error.value) {
    videoRef.value?.play().catch(() => {})
  }
}

function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '--:--'
  const m = Math.floor(seconds / 60)
  const s = String(seconds % 60).padStart(2, '0')
  return `${m}:${s}`
}

function videoProgressPercent(video) {
  if (video.completed) return 100
  if (!video.durationSeconds || video.durationSeconds <= 0) return 0
  return Math.min(99, Math.round((video.positionSeconds / video.durationSeconds) * 100))
}

function globalIndex(videoId) {
  const index = videos.value.findIndex((video) => video.id === videoId)
  return String(index + 1).padStart(2, '0')
}

onMounted(load)
onBeforeUnmount(() => {
  leaving = true
  clearRefreshTimer()
  persistProgress(false, true)
  report('HIDDEN')
})
</script>

<template>
  <main class="page series-page">
    <RouterLink to="/learning" class="back-link">
      <el-icon><ArrowLeft /></el-icon>
      返回学习空间
    </RouterLink>

    <div v-if="loading" class="loading-layout">
      <div class="panel skeleton-main"><el-skeleton animated :rows="8" /></div>
      <div class="panel skeleton-side"><el-skeleton animated :rows="10" /></div>
    </div>

    <el-alert v-else-if="!series" type="error" :closable="false" show-icon :title="error || '系列不存在'" />

    <template v-else>
      <header class="series-hero">
        <div class="hero-copy">
          <div class="eyebrow">RECORDED SERIES</div>
          <h1>{{ series.title }}</h1>
          <p>{{ series.summary || '暂无系列简介' }}</p>
          <div class="hero-tags">
            <el-tag effect="plain">{{ series.teacherName }}</el-tag>
            <el-tag type="info" effect="plain">{{ videoCount }} 节视频</el-tag>
            <el-tag type="success" effect="plain">已加入学习</el-tag>
          </div>
        </div>
        <div class="progress-card">
          <div class="progress-head">
            <span>学习进度</span>
            <strong>{{ completedCount }}<small>/ {{ videoCount }}</small></strong>
          </div>
          <el-progress
            :percentage="videoCount ? Math.round((completedCount / videoCount) * 100) : 0"
            :stroke-width="7"
            :show-text="false"
          />
          <span class="progress-note">{{ completedCount >= videoCount && videoCount ? '系列已完成' : '进度会自动保存' }}</span>
        </div>
      </header>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" class="player-error">
        <el-button v-if="current" type="primary" link @click="retry">重新加载视频</el-button>
      </el-alert>

      <div class="player-layout">
        <section class="main-column">
          <div class="video-panel">
            <div class="video-topline">
              <span class="live-dot"></span>
              <span>COS 直连播放</span>
              <span class="topline-separator"></span>
              <span>{{ current ? formatDuration(current.durationSeconds) : '--:--' }}</span>
            </div>
            <div class="video-shell">
              <video
                v-if="playUrl"
                ref="videoRef"
                :src="playUrl"
                controls
                playsinline
                preload="metadata"
                @loadedmetadata="onLoadedMetadata"
                @play="report('PLAY')"
                @pause="onPause"
                @seeking="report('SEEKING')"
                @seeked="onSeeked"
                @timeupdate="onTimeUpdate"
                @waiting="report('WAITING')"
                @stalled="onStalled"
                @ended="onEnded"
                @error="onVideoError"
              ></video>
              <div v-else class="video-placeholder">
                <el-icon><VideoCamera /></el-icon>
                <span>{{ loadingPlay ? '正在获取播放地址…' : '选择右侧视频开始学习' }}</span>
              </div>
              <div v-if="loadingPlay && playUrl" class="video-loading">
                <el-icon class="is-loading"><Refresh /></el-icon>
                正在切换视频
              </div>
            </div>
          </div>

          <div v-if="current" class="now-playing">
            <div class="now-copy">
              <div class="playing-label"><span></span> 正在播放</div>
              <h2>{{ current.title }}</h2>
              <p>进度与观看位置会自动保存，刷新后可以继续。</p>
            </div>
            <div class="now-meta">
              <span>当前视频</span>
              <strong>{{ formatDuration(current.durationSeconds) }}</strong>
            </div>
          </div>
        </section>

        <aside class="syllabus">
          <div class="syllabus-head">
            <div>
              <span class="syllabus-kicker">COURSE OUTLINE</span>
              <strong>课程目录</strong>
            </div>
            <span class="lesson-total">{{ videoCount }} 节</span>
          </div>

          <div class="syllabus-list">
            <div v-for="(chapter, chapterIndex) in series.chapters" :key="chapter.id" class="chapter-block">
              <div class="chapter-title">
                <span>{{ String(chapterIndex + 1).padStart(2, '0') }}</span>
                {{ chapter.title }}
              </div>
              <button
                v-for="video in chapter.videos"
                :key="video.id"
                type="button"
                class="video-row"
                :class="{ active: current?.id === video.id, completed: video.completed, 'in-progress': !video.completed && video.positionSeconds > 0 }"
                @click="select(video)"
              >
                <span class="video-index">{{ globalIndex(video.id) }}</span>
                <span class="video-copy">
                  <span class="video-title">{{ video.title }}</span>
                  <span class="video-subline">
                    <template v-if="video.completed">已完成</template>
                    <template v-else-if="video.positionSeconds > 0">已看 {{ videoProgressPercent(video) }}%</template>
                    <template v-else>{{ formatDuration(video.durationSeconds) }}</template>
                  </span>
                </span>
                <el-icon v-if="video.completed" class="done-icon"><CircleCheck /></el-icon>
                <el-icon v-else class="row-arrow"><ArrowRight /></el-icon>
              </button>
              <div v-if="!chapter.videos.length" class="empty-chapter">本章暂无视频</div>
            </div>
          </div>
        </aside>
      </div>
    </template>
  </main>
</template>

<style scoped>
.series-page {
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

.loading-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
}

.skeleton-main,
.skeleton-side {
  min-height: 420px;
  padding: 26px;
}

.series-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding-bottom: 28px;
  border-bottom: 1px solid var(--border-strong);
}

.hero-copy {
  min-width: 0;
}

.hero-copy h1 {
  margin: 9px 0 10px;
  font-family: var(--font-display);
  font-size: clamp(2.4rem, 5vw, 4.2rem);
  font-weight: 600;
  letter-spacing: -0.06em;
  line-height: 1.08;
}

.hero-copy p {
  max-width: 680px;
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.75;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 18px;
}

.progress-card {
  width: min(270px, 100%);
  flex: none;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.76);
}

.progress-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 11px;
}

.progress-head > span {
  color: var(--muted);
  font-size: 12px;
}

.progress-head strong {
  font-family: var(--font-display);
  font-size: 25px;
  line-height: 1;
}

.progress-head small {
  margin-left: 4px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 400;
}

.progress-note {
  display: block;
  margin-top: 8px;
  color: var(--muted);
  font-size: 10px;
}

.player-error {
  margin-top: 20px;
}

.player-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 20px;
  align-items: start;
  margin-top: 24px;
}

.main-column {
  min-width: 0;
}

.video-panel {
  overflow: hidden;
  border: 1px solid #263a33;
  border-radius: var(--radius-md);
  background: #101a16;
  box-shadow: var(--shadow-md);
}

.video-topline {
  display: flex;
  height: 42px;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.54);
  font-family: var(--font-mono);
  font-size: 10px;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #74b598;
  box-shadow: 0 0 0 3px rgba(116, 181, 152, 0.12);
}

.topline-separator {
  width: 1px;
  height: 11px;
  margin: 0 3px;
  background: rgba(255, 255, 255, 0.14);
}

.video-shell {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: #080c0a;
}

.video-shell video {
  display: block;
  width: 100%;
  height: 100%;
  background: #000;
}

.video-placeholder {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  color: rgba(255, 255, 255, 0.42);
  font-size: 14px;
}

.video-placeholder .el-icon {
  font-size: 30px;
}

.video-loading {
  position: absolute;
  right: 14px;
  bottom: 52px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 7px;
  background: rgba(16, 26, 22, 0.88);
  color: rgba(255, 255, 255, 0.78);
  font-size: 11px;
}

.now-playing {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 14px;
  padding: 20px 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.now-copy {
  min-width: 0;
}

.playing-label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--clay);
  font-size: 11px;
  font-weight: 700;
}

.playing-label span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--clay);
}

.now-copy h2 {
  margin: 7px 0 5px;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 21px;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.now-copy p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.now-meta {
  display: flex;
  flex: none;
  align-items: flex-end;
  flex-direction: column;
  gap: 5px;
  color: var(--muted);
  font-size: 10px;
}

.now-meta strong {
  color: var(--text-soft);
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 500;
}

.syllabus {
  position: sticky;
  top: 24px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.syllabus-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 19px 15px;
  border-bottom: 1px solid var(--border);
  background: #f8f5ee;
}

.syllabus-head > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.syllabus-kicker {
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.syllabus-head strong {
  font-family: var(--font-display);
  font-size: 17px;
}

.lesson-total {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.syllabus-list {
  max-height: min(690px, calc(100vh - 190px));
  overflow-y: auto;
  overscroll-behavior: contain;
}

.chapter-block + .chapter-block {
  border-top: 1px solid var(--border);
}

.chapter-title {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 14px 18px 9px;
  background: #fbf9f4;
  color: var(--text-soft);
  font-size: 11px;
  font-weight: 700;
}

.chapter-title span {
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 9px;
}

.video-row {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 11px 16px 11px 18px;
  border: 0;
  border-left: 3px solid transparent;
  background: transparent;
  color: var(--text);
  text-align: left;
  cursor: pointer;
  transition: 0.16s ease;
}

.video-row:hover {
  background: #fbf8f1;
}

.video-row.active {
  border-left-color: var(--clay);
  background: var(--clay-soft);
}

.video-row.completed .video-title {
  color: var(--pine);
}

.video-index {
  display: grid;
  width: 27px;
  height: 27px;
  flex: none;
  place-items: center;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: #fffefa;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
}

.video-row.active .video-index {
  border-color: var(--clay);
  color: var(--clay);
}

.video-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.video-title {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-subline {
  color: var(--muted);
  font-size: 10px;
}

.row-arrow,
.done-icon {
  flex: none;
  color: #9ba39e;
}

.done-icon {
  color: var(--pine);
}

.empty-chapter {
  padding: 12px 18px 16px;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 960px) {
  .loading-layout,
  .player-layout {
    grid-template-columns: 1fr;
  }

  .syllabus {
    position: static;
  }

  .syllabus-list {
    max-height: 520px;
  }
}

@media (max-width: 680px) {
  .series-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .progress-card {
    width: 100%;
  }

  .now-playing {
    align-items: flex-start;
    flex-direction: column;
  }

  .now-meta {
    align-items: flex-start;
  }

  .video-topline {
    padding: 0 12px;
  }
}
</style>
