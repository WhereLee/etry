<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  completeTeacherVideoUpload,
  createTeacherChapter,
  createTeacherSeries,
  createTeacherVideo,
  deleteTeacherChapter,
  deleteTeacherVideo,
  getTeacherSeries,
  requestTeacherVideoUpload,
  updateTeacherChapter,
  updateTeacherSeries,
  updateTeacherSeriesStatus,
  updateTeacherVideo
} from '@/features/course/api/teacher'
import StatusTag from '@/components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const isNew = computed(() => !route.params.id)
const series = ref(null)
const loading = ref(false)
const busy = ref('')
const error = ref('')
const notice = ref('')
const form = reactive({ title: '', summary: '', sort: 0 })
const chapterForm = reactive({ title: '', sort: 0 })
const newVideos = reactive({})
const uploads = reactive({})

onMounted(load)

async function load() {
  if (!route.params.id) return
  loading.value = true
  error.value = ''
  try {
    series.value = await getTeacherSeries(route.params.id)
    Object.assign(form, {
      title: series.value.title,
      summary: series.value.summary || '',
      sort: series.value.sort || 0
    })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function reload() {
  if (!route.params.id) return
  series.value = await getTeacherSeries(route.params.id)
}

async function saveSeries() {
  resetFeedback()
  if (!form.title.trim()) {
    error.value = '请输入系列标题'
    return
  }
  busy.value = 'series'
  try {
    const body = { title: form.title.trim(), summary: form.summary.trim(), sort: Number(form.sort) || 0 }
    if (isNew.value) {
      const id = await createTeacherSeries(body)
      notice.value = '系列已创建，请继续添加章节和视频。'
      await router.replace({ name: 'teacherSeriesEdit', params: { id } })
      await load()
    } else {
      await updateTeacherSeries(route.params.id, body)
      notice.value = '系列信息已保存。'
      await reload()
    }
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function changeStatus(status) {
  resetFeedback()
  busy.value = `status-${status}`
  try {
    await updateTeacherSeriesStatus(route.params.id, status)
    notice.value = status === 'PUBLISHED' ? '系列已发布。' : status === 'ARCHIVED' ? '系列已归档。' : '系列已回到草稿。'
    await reload()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function addChapter() {
  resetFeedback()
  if (!chapterForm.title.trim()) {
    error.value = '请输入章节标题'
    return
  }
  busy.value = 'chapter-add'
  try {
    await createTeacherChapter(route.params.id, {
      title: chapterForm.title.trim(),
      sort: Number(chapterForm.sort) || 0
    })
    chapterForm.title = ''
    chapterForm.sort = 0
    notice.value = '章节已添加。'
    await reload()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function saveChapter(chapter) {
  resetFeedback()
  busy.value = `chapter-${chapter.id}`
  try {
    await updateTeacherChapter(route.params.id, chapter.id, {
      title: chapter.title.trim(),
      sort: Number(chapter.sort) || 0
    })
    notice.value = '章节已保存。'
    await reload()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function removeChapter(chapter) {
  try {
    await ElMessageBox.confirm(`确认删除章节“${chapter.title}”？`, '删除章节', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  resetFeedback()
  busy.value = `chapter-delete-${chapter.id}`
  try {
    await deleteTeacherChapter(route.params.id, chapter.id)
    notice.value = '章节已删除。'
    await reload()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

function videoDraft(chapterId) {
  if (!newVideos[chapterId]) {
    newVideos[chapterId] = { title: '', sort: 0, durationSeconds: 0 }
  }
  return newVideos[chapterId]
}

async function addVideo(chapter) {
  resetFeedback()
  const draft = videoDraft(chapter.id)
  if (!draft.title.trim()) {
    error.value = '请输入视频标题'
    return
  }
  busy.value = `video-add-${chapter.id}`
  try {
    await createTeacherVideo(route.params.id, chapter.id, {
      title: draft.title.trim(),
      sort: Number(draft.sort) || 0,
      durationSeconds: Number(draft.durationSeconds) || 0
    })
    Object.assign(draft, { title: '', sort: 0, durationSeconds: 0 })
    notice.value = '视频已添加。'
    await reload()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function saveVideo(chapter, video) {
  resetFeedback()
  busy.value = `video-${video.id}`
  try {
    await updateTeacherVideo(route.params.id, chapter.id, video.id, {
      title: video.title.trim(),
      sort: Number(video.sort) || 0,
      durationSeconds: Number(video.durationSeconds) || 0
    })
    notice.value = '视频信息已保存。'
    await reload()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

async function uploadVideoFile(chapter, video, event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  resetFeedback()
  if (!file.type.startsWith('video/')) {
    error.value = '请选择视频文件'
    return
  }
  const contentType = file.type || (file.name.toLowerCase().endsWith('.webm') ? 'video/webm' : 'video/mp4')
  uploads[video.id] = { progress: 0, label: '读取视频信息…' }
  try {
    const duration = await readVideoDuration(file)
    uploads[video.id].label = '申请上传地址…'
    const ticket = await requestTeacherVideoUpload(route.params.id, chapter.id, video.id, {
      filename: file.name,
      contentType,
      sizeBytes: file.size
    })
    uploads[video.id].label = '上传中…'
    await putFileToCos(ticket.uploadUrl, file, ticket.contentType, (progress) => {
      uploads[video.id].progress = progress
    })
    uploads[video.id].label = '确认上传…'
    await completeTeacherVideoUpload(route.params.id, chapter.id, video.id, {
      durationSeconds: Math.round(duration) || Number(video.durationSeconds) || 0
    })
    uploads[video.id] = { progress: 100, label: '已完成' }
    notice.value = '视频上传完成，已标记为就绪。'
    await reload()
  } catch (e) {
    uploads[video.id] = { progress: 0, label: '上传失败' }
    error.value = e.message
  }
}

function putFileToCos(url, file, contentType, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    xhr.setRequestHeader('Content-Type', contentType)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress(100)
        resolve()
      } else {
        reject(new Error(`COS 上传失败 HTTP ${xhr.status}`))
      }
    }
    xhr.onerror = () => reject(new Error('COS 上传失败，请检查存储桶 CORS 配置'))
    xhr.send(file)
  })
}

function readVideoDuration(file) {
  return new Promise((resolve) => {
    const video = document.createElement('video')
    const objectUrl = URL.createObjectURL(file)
    const finish = (duration) => {
      URL.revokeObjectURL(objectUrl)
      resolve(Number.isFinite(duration) ? duration : 0)
    }
    video.preload = 'metadata'
    video.onloadedmetadata = () => finish(video.duration)
    video.onerror = () => finish(0)
    video.src = objectUrl
  })
}

async function removeVideo(chapter, video) {
  try {
    await ElMessageBox.confirm(`确认删除视频“${video.title}”？`, '删除视频', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  resetFeedback()
  busy.value = `video-delete-${video.id}`
  try {
    await deleteTeacherVideo(route.params.id, chapter.id, video.id)
    notice.value = '视频已删除。'
    await reload()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = ''
  }
}

function resetFeedback() {
  error.value = ''
  notice.value = ''
}

function mediaLabel(status) {
  if (status === 'READY') return '已就绪'
  if (status === 'UPLOADING') return '待确认'
  return '未上传'
}
</script>

<template>
  <main class="page editor-page">
    <RouterLink to="/teacher/series" class="back-link">
      <el-icon><ArrowLeft /></el-icon>
      返回我的系列
    </RouterLink>

    <div v-if="loading" class="panel skeleton-panel"><el-skeleton animated :rows="8" /></div>
    <template v-else>
      <header class="editor-hero">
        <div>
          <div class="eyebrow">{{ isNew ? 'CREATE SERIES' : 'EDIT SERIES' }}</div>
          <h1>{{ isNew ? '新建系列' : form.title }}</h1>
          <p>先创建视频条目，再上传视频文件；上传确认前视频保持未就绪。</p>
        </div>
        <StatusTag v-if="series" :status="series.status" />
      </header>

      <el-alert v-if="notice" type="success" show-icon class="feedback" @close="notice = ''" :title="notice" />
      <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

      <section class="form-card">
        <div class="section-heading">
          <span>01</span>
          <div>
            <h2>系列信息</h2>
            <p>标题和简介会显示在学员的课程书架中。</p>
          </div>
        </div>
        <el-form :model="form" label-position="top" @submit.prevent="saveSeries">
          <div class="series-form">
            <el-form-item label="系列标题" class="grow">
              <el-input v-model="form.title" maxlength="100" placeholder="例如：物理必修一" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" controls-position="right" class="sort-input" />
            </el-form-item>
          </div>
          <el-form-item label="系列简介">
            <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="给学生看的系列简介" />
          </el-form-item>
          <div class="form-actions">
            <el-button type="primary" native-type="submit" :loading="busy === 'series'">保存系列信息</el-button>
          </div>
        </el-form>
      </section>

      <template v-if="!isNew && series">
        <section class="status-bar">
          <div>
            <span class="status-kicker">PUBLISHING</span>
            <strong>发布状态</strong>
            <p>发布前必须有章节、视频，且所有视频都已完成。结构修改后自动回到草稿，但已拥有权益的学员仍可访问。</p>
          </div>
          <div class="status-actions">
            <el-button v-if="series.status === 'DRAFT'" type="primary" :disabled="!!busy" @click="changeStatus('PUBLISHED')">发布系列</el-button>
            <el-button v-if="series.status === 'PUBLISHED'" :disabled="!!busy" @click="changeStatus('DRAFT')">下架为草稿</el-button>
            <el-button v-if="series.status !== 'ARCHIVED'" type="danger" plain :disabled="!!busy" @click="changeStatus('ARCHIVED')">归档</el-button>
            <el-button v-if="series.status === 'ARCHIVED'" :disabled="!!busy" @click="changeStatus('DRAFT')">恢复草稿</el-button>
          </div>
        </section>

        <section class="chapters">
          <div class="chapters-head">
            <div class="section-heading compact">
              <span>02</span>
              <div>
                <h2>章节与视频</h2>
                <p>{{ series.chapters.length }} 个章节</p>
              </div>
            </div>
          </div>

          <el-form class="add-chapter" @submit.prevent="addChapter">
            <el-input v-model="chapterForm.title" maxlength="100" placeholder="新章节标题" class="chapter-name-input" />
            <el-input-number v-model="chapterForm.sort" :min="0" controls-position="right" class="compact-number" />
            <el-button type="primary" plain native-type="submit" :loading="busy === 'chapter-add'">
              <el-icon><Plus /></el-icon>
              添加章节
            </el-button>
          </el-form>

          <article v-for="(chapter, chapterIndex) in series.chapters" :key="chapter.id" class="chapter-card">
            <div class="chapter-edit">
              <span class="chapter-number">CAP. {{ String(chapterIndex + 1).padStart(2, '0') }}</span>
              <el-input v-model="chapter.title" maxlength="100" class="chapter-title-input" />
              <el-input-number v-model="chapter.sort" :min="0" controls-position="right" class="compact-number" />
              <el-button :disabled="!!busy" @click="saveChapter(chapter)">保存章节</el-button>
              <el-button type="danger" text :disabled="!!busy" @click="removeChapter(chapter)">删除</el-button>
            </div>

            <div class="video-table">
              <div class="video-head">
                <span>视频标题</span>
                <span>排序</span>
                <span>时长（秒）</span>
                <span>媒体状态</span>
                <span>视频文件</span>
                <span>操作</span>
              </div>
              <div v-for="video in chapter.videos" :key="video.id" class="video-edit">
                <el-input v-model="video.title" maxlength="100" />
                <el-input-number v-model="video.sort" :min="0" controls-position="right" class="compact-number" />
                <el-input-number v-model="video.durationSeconds" :min="0" :max="86400" controls-position="right" class="compact-number" />
                <el-tag :type="video.mediaStatus === 'READY' ? 'success' : video.mediaStatus === 'UPLOADING' ? 'warning' : 'info'" effect="plain" size="small">
                  {{ mediaLabel(video.mediaStatus) }}
                </el-tag>
                <div class="upload-cell">
                  <label class="file-button" :class="{ busy: uploads[video.id] && uploads[video.id].progress < 100 }">
                    <el-icon><UploadFilled /></el-icon>
                    {{ video.mediaStatus === 'READY' ? '替换视频' : '上传视频' }}
                    <input type="file" accept="video/mp4,video/webm" @change="uploadVideoFile(chapter, video, $event)" />
                  </label>
                  <div v-if="uploads[video.id]" class="upload-progress">
                    <el-progress :percentage="uploads[video.id].progress" :stroke-width="4" :show-text="false" />
                    <small>{{ uploads[video.id].label }} {{ uploads[video.id].progress }}%</small>
                  </div>
                  <small v-else-if="video.mediaStatus === 'READY' && video.videoKey" class="object-key">已关联 COS 对象</small>
                </div>
                <div class="video-actions">
                  <el-button link type="primary" :disabled="!!busy" @click="saveVideo(chapter, video)">保存</el-button>
                  <el-button link type="danger" :disabled="!!busy" @click="removeVideo(chapter, video)">删除</el-button>
                </div>
              </div>
              <div v-if="!chapter.videos.length" class="empty-videos">本章暂无视频，可在下方添加视频条目。</div>
            </div>

            <div class="add-video">
              <span class="add-video-label">新增视频</span>
              <el-input v-model="videoDraft(chapter.id).title" maxlength="100" placeholder="新视频标题" class="video-name-input" />
              <el-input-number v-model="videoDraft(chapter.id).sort" :min="0" controls-position="right" class="compact-number" />
              <el-input-number v-model="videoDraft(chapter.id).durationSeconds" :min="0" :max="86400" controls-position="right" class="compact-number" />
              <el-button type="primary" plain :disabled="busy === `video-add-${chapter.id}`" @click="addVideo(chapter)">添加视频</el-button>
            </div>
          </article>
        </section>
      </template>
    </template>
  </main>
</template>

<style scoped>
.editor-page {
  max-width: 1260px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  color: var(--muted);
  font-size: 13px;
}

.back-link:hover {
  color: var(--pine);
}

.skeleton-panel {
  padding: 30px;
}

.editor-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border-strong);
}

.editor-hero h1 {
  margin: 9px 0 9px;
  font-family: var(--font-display);
  font-size: clamp(2.2rem, 4.5vw, 3.6rem);
  font-weight: 600;
  letter-spacing: -0.055em;
  line-height: 1.08;
}

.editor-hero p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.65;
}

.feedback {
  margin-bottom: 14px;
}

.form-card {
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
}

.section-heading > span {
  padding-top: 3px;
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 10px;
}

.section-heading h2 {
  margin: 0 0 4px;
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: -0.025em;
}

.section-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
}

.section-heading.compact {
  margin-bottom: 0;
}

.series-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 14px;
}

.sort-input {
  width: 100%;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 18px;
  padding: 20px 22px;
  border-radius: var(--radius-md);
  background: var(--pine-deep);
  color: #f7f1e5;
}

.status-kicker {
  display: block;
  margin-bottom: 4px;
  color: #dda27d;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
}

.status-bar strong {
  font-family: var(--font-display);
  font-size: 18px;
}

.status-bar p {
  margin: 5px 0 0;
  color: rgba(247, 241, 229, 0.58);
  font-size: 11px;
}

.status-actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 8px;
}

.chapters {
  margin-top: 28px;
}

.chapters-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.add-chapter {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-sm);
  background: rgba(255, 253, 248, 0.54);
}

.chapter-name-input {
  max-width: 380px;
}

.compact-number {
  width: 104px;
  flex: none;
}

.chapter-card {
  overflow: hidden;
  margin-bottom: 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.chapter-edit {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: #f8f5ee;
}

.chapter-number {
  flex: none;
  color: var(--clay);
  font-family: var(--font-mono);
  font-size: 9px;
}

.chapter-title-input {
  max-width: 360px;
}

.video-table {
  overflow-x: auto;
}

.video-head,
.video-edit {
  display: grid;
  min-width: 940px;
  grid-template-columns: minmax(220px, 1.45fr) 100px 110px 86px minmax(180px, 1fr) 96px;
  gap: 10px;
  align-items: center;
  padding: 0 16px;
}

.video-head {
  height: 36px;
  color: var(--muted);
  font-size: 10px;
}

.video-edit {
  min-height: 56px;
  border-top: 1px solid var(--border);
}

.video-edit :deep(.el-input-number) {
  width: 100%;
}

.upload-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.file-button {
  position: relative;
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 5px;
  padding: 7px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: #fffefa;
  color: var(--text-soft);
  font-size: 11px;
  cursor: pointer;
  transition: 0.16s ease;
}

.file-button:hover {
  border-color: var(--pine);
  color: var(--pine);
}

.file-button.busy {
  opacity: 0.55;
  pointer-events: none;
}

.file-button input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
}

.upload-progress {
  display: flex;
  align-items: center;
  gap: 7px;
}

.upload-progress :deep(.el-progress) {
  width: 70px;
}

.upload-progress small,
.object-key {
  overflow: hidden;
  color: var(--muted);
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.empty-videos {
  padding: 18px 16px;
  border-top: 1px solid var(--border);
  color: var(--muted);
  font-size: 11px;
}

.add-video {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  background: #fbf9f4;
}

.add-video-label {
  flex: none;
  color: var(--muted);
  font-size: 10px;
}

.video-name-input {
  max-width: 330px;
}

@media (max-width: 860px) {
  .editor-hero,
  .status-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .status-actions {
    width: 100%;
  }

  .chapter-edit {
    flex-wrap: wrap;
  }

  .chapter-title-input {
    max-width: none;
    flex: 1 1 240px;
  }

  .add-video {
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .form-card {
    padding: 18px;
  }

  .series-form {
    grid-template-columns: 1fr;
  }

  .add-chapter,
  .add-video {
    align-items: stretch;
    flex-direction: column;
  }

  .chapter-name-input,
  .video-name-input,
  .compact-number {
    width: 100%;
    max-width: none;
  }
}
</style>
