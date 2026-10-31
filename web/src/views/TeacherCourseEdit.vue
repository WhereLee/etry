<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  addChapter,
  addLesson,
  addMaterial,
  deleteChapter,
  deleteCourse,
  deleteLesson,
  deleteMaterial,
  getMyCourse,
  listMyMaterials,
  materialTicket,
  offlineCourse,
  submitCourse,
  updateCourse,
  updateLesson,
  uploadCover,
  videoTicket,
  withdrawCourse
} from '../api/teacher'
import { listSubjects, listTags } from '../api/course'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id

const course = ref(null)
const materials = ref([])
const subjects = ref([])
const tags = ref([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const busy = ref(false)

const form = ref({ title: '', summary: '', description: '', subjectId: '', level: '', tagIds: [], sequential: false })
const chapterTitle = ref('')
const lessonDraft = ref({ chapterId: '', title: '' })
let coverFile = null

const canEdit = computed(() => course.value && ['DRAFT', 'REJECTED', 'OFFLINE'].includes(course.value.status))
const isPending = computed(() => course.value?.status === 'PENDING')
const isPublished = computed(() => course.value?.status === 'PUBLISHED')

const STATUS_LABEL = {
  DRAFT: '草稿',
  PENDING: '待审核',
  PUBLISHED: '已上架',
  REJECTED: '已驳回',
  OFFLINE: '已下架'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getMyCourse(courseId)
    course.value = data
    form.value = {
      title: data.title,
      summary: data.summary || '',
      description: data.description || '',
      subjectId: data.subjectId,
      level: data.level || '',
      tagIds: (data.tags || []).map((t) => t.id),
      sequential: !!data.sequential
    }
    materials.value = await listMyMaterials(courseId).catch(() => [])
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function flash(msg) {
  notice.value = msg
  setTimeout(() => {
    if (notice.value === msg) notice.value = ''
  }, 2500)
}

async function saveInfo() {
  busy.value = true
  try {
    await updateCourse(courseId, {
      title: form.value.title.trim(),
      summary: form.value.summary || null,
      description: form.value.description || null,
      subjectId: form.value.subjectId,
      level: form.value.level || null,
      tagIds: form.value.tagIds,
      sequential: form.value.sequential,
      coverKey: course.value.coverUrl ? undefined : null
    })
    await load()
    flash('已保存')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function onCoverChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  busy.value = true
  try {
    const key = await uploadCover(file)
    await updateCourse(courseId, {
      title: form.value.title.trim(),
      summary: form.value.summary || null,
      description: form.value.description || null,
      subjectId: form.value.subjectId,
      level: form.value.level || null,
      tagIds: form.value.tagIds,
      sequential: form.value.sequential,
      coverKey: key
    })
    await load()
    flash('封面已更新')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
    event.target.value = ''
  }
}

async function createChapter() {
  if (!chapterTitle.value.trim()) return
  try {
    await addChapter(courseId, { title: chapterTitle.value.trim(), sort: (course.value.chapters?.length || 0) + 1 })
    chapterTitle.value = ''
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function removeChapter(id) {
  if (!confirm('删除章节会连同下面的课时一起删掉，确定吗？')) return
  try {
    await deleteChapter(id)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function createLesson(chapter) {
  lessonDraft.value = { chapterId: chapter.id, title: '' }
}

async function confirmLesson(chapter) {
  const title = lessonDraft.value.title.trim()
  if (!title) return
  try {
    await addLesson(chapter.id, { title, sort: (chapter.lessons?.length || 0) + 1 })
    lessonDraft.value = { chapterId: '', title: '' }
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function uploadVideo(chapter, lesson, event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.mp4')) {
    error.value = '只支持 mp4'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const ticket = await videoTicket({
      fileName: file.name,
      contentType: 'video/mp4',
      sizeBytes: file.size
    })
    const res = await fetch(ticket.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'video/mp4' },
      body: file
    })
    if (!res.ok) throw new Error('上传失败，可能是签名过期，重试一次')
    await updateLesson(lesson.id, { title: lesson.title, sort: lesson.sort, videoKey: ticket.key })
    await load()
    flash('视频已上传，时长由服务端从文件读取')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
    event.target.value = ''
  }
}

async function removeLesson(id) {
  if (!confirm('删除这个课时？')) return
  try {
    await deleteLesson(id)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function onMaterialChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  busy.value = true
  try {
    const ticket = await materialTicket({
      fileName: file.name,
      contentType: file.type || 'application/pdf',
      sizeBytes: file.size
    })
    const res = await fetch(ticket.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': file.type || 'application/pdf' },
      body: file
    })
    if (!res.ok) throw new Error('上传失败，可能是签名过期')
    await addMaterial(courseId, {
      title: file.name,
      fileName: file.name,
      fileKey: ticket.key,
      sort: materials.value.length + 1
    })
    await load()
    flash('课件已添加')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
    event.target.value = ''
  }
}

async function removeMaterial(id) {
  if (!confirm('删除这份资料？对象存储里的文件也会一起删掉')) return
  try {
    await deleteMaterial(id)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function doSubmit() {
  busy.value = true
  try {
    await submitCourse(courseId)
    await load()
    flash('已提交审核')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function doWithdraw() {
  busy.value = true
  try {
    await withdrawCourse(courseId)
    await load()
    flash('已撤回，可以继续修改')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function doOffline() {
  if (!confirm('下架后学员看不到这门课，内容可以继续修改')) return
  busy.value = true
  try {
    await offlineCourse(courseId)
    await load()
    flash('已下架')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function doDelete() {
  if (!confirm('删除后不可恢复，确定吗？')) return
  busy.value = true
  try {
    await deleteCourse(courseId)
    router.push({ name: 'teacherCourses' })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  load()
  try {
    subjects.value = await listSubjects()
    tags.value = await listTags()
  } catch {
    // 忽略
  }
})
</script>

<template>
  <div class="page">
    <div class="row" style="margin-bottom: 12px">
      <a href="#" @click.prevent="router.push({ name: 'teacherCourses' })">← 返回我的课程</a>
      <div class="spacer"></div>
      <span class="tag" v-if="course">{{ STATUS_LABEL[course.status] }}</span>
    </div>

    <p class="error" v-if="error">{{ error }}</p>
    <p class="notice" v-if="notice">{{ notice }}</p>
    <div class="empty" v-if="loading">加载中…</div>

    <template v-if="course">
      <div class="panel" v-if="course.rejectReason">
        <strong class="muted small">驳回原因</strong>
        <div>{{ course.rejectReason }}</div>
      </div>

      <div class="row wrap toolbar">
        <button class="primary" v-if="canEdit" :disabled="busy" @click="doSubmit">提交审核</button>
        <button v-if="isPending" :disabled="busy" @click="doWithdraw">撤回审核</button>
        <button v-if="isPublished" :disabled="busy" @click="doOffline">下架</button>
        <button v-if="canEdit && !course.publishedAt" :disabled="busy" @click="doDelete">删除课程</button>
        <span class="muted small">上架后要改内容，先下架再改，改完重新提交</span>
      </div>

      <div class="panel">
        <h3 style="margin-top: 0">基本信息</h3>
        <div class="field">
          <label>标题</label>
          <input v-model="form.title" :disabled="!canEdit" maxlength="100" />
        </div>
        <div class="field">
          <label>简介</label>
          <input v-model="form.summary" :disabled="!canEdit" maxlength="200" />
        </div>
        <div class="field">
          <label>详情</label>
          <textarea v-model="form.description" :disabled="!canEdit" rows="5"></textarea>
        </div>
        <div class="row wrap">
          <div class="field" style="flex: 1; min-width: 160px">
            <label>学科</label>
            <select v-model="form.subjectId" :disabled="!canEdit">
              <option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="field" style="flex: 1; min-width: 140px">
            <label>难度</label>
            <select v-model="form.level" :disabled="!canEdit">
              <option value="">不标注</option>
              <option value="BEGINNER">入门</option>
              <option value="INTERMEDIATE">进阶</option>
              <option value="ADVANCED">高级</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>标签</label>
          <div class="row wrap">
            <label class="check" v-for="t in tags" :key="t.id">
              <input type="checkbox" :value="t.id" v-model="form.tagIds" :disabled="!canEdit" />
              {{ t.name }}
            </label>
          </div>
        </div>
        <label class="check">
          <input type="checkbox" v-model="form.sequential" :disabled="!canEdit" />
          按章节顺序学习
        </label>
        <div class="row" style="margin-top: 12px">
          <button class="primary" :disabled="!canEdit || busy" @click="saveInfo">保存基本信息</button>
        </div>
      </div>

      <div class="panel">
        <h3 style="margin-top: 0">封面</h3>
        <div class="row">
          <div class="cover-preview" :style="course.coverUrl ? { backgroundImage: `url(${course.coverUrl})` } : {}"></div>
          <div>
            <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="!canEdit || busy" @change="onCoverChange" />
            <div class="muted small">2MB 以内，jpg / png / webp</div>
          </div>
        </div>
      </div>

      <div class="panel">
        <h3 style="margin-top: 0">章节课时</h3>

        <div class="add-row" v-if="canEdit">
          <input v-model="chapterTitle" placeholder="新章节标题" @keyup.enter="createChapter" />
          <button :disabled="busy" @click="createChapter">添加章节</button>
        </div>

        <div class="chapter" v-for="ch in course.chapters" :key="ch.id">
          <div class="chapter-head">
            <strong>{{ ch.title }}</strong>
            <div class="spacer"></div>
            <button v-if="canEdit" class="small-btn" :disabled="busy" @click="createLesson(ch)">加课时</button>
            <button v-if="canEdit" class="small-btn" :disabled="busy" @click="removeChapter(ch.id)">删章节</button>
          </div>

          <div class="lesson" v-for="l in ch.lessons" :key="l.id">
            <div>
              <div>{{ l.title }}</div>
              <div class="muted small">
                {{ l.hasVideo ? `时长 ${Math.round((l.durationSeconds || 0) / 60)} 分钟` : '还没有视频' }}
              </div>
            </div>
            <div class="spacer"></div>
            <template v-if="canEdit">
              <label class="upload-btn" :class="{ disabled: busy }">
                上传视频
                <input type="file" accept="video/mp4" hidden :disabled="busy" @change="uploadVideo(ch, l, $event)" />
              </label>
              <button class="small-btn" :disabled="busy" @click="removeLesson(l.id)">删</button>
            </template>
          </div>

          <div class="add-row" v-if="lessonDraft.chapterId === ch.id">
            <input v-model="lessonDraft.title" placeholder="课时标题" @keyup.enter="confirmLesson(ch)" />
            <button :disabled="busy" @click="confirmLesson(ch)">确定</button>
          </div>
        </div>

        <div class="empty" v-if="!course.chapters?.length">还没有章节</div>
      </div>

      <div class="panel">
        <h3 style="margin-top: 0">课件资料</h3>
        <div class="row" style="margin-bottom: 10px" v-if="canEdit">
          <label class="upload-btn" :class="{ disabled: busy }">
            上传资料
            <input
              type="file"
              accept=".pdf,.doc,.docx,.ppt,.pptx,.zip"
              hidden
              :disabled="busy"
              @change="onMaterialChange"
            />
          </label>
          <span class="muted small">PDF / Word / PPT / ZIP，单个 50MB</span>
        </div>
        <div class="material" v-for="m in materials" :key="m.id">
          <a :href="m.downloadUrl" target="_blank" rel="noreferrer">{{ m.title }}</a>
          <div class="spacer"></div>
          <span class="muted small">{{ Math.round(m.sizeBytes / 1024) }} KB</span>
          <button v-if="canEdit" class="small-btn" :disabled="busy" @click="removeMaterial(m.id)">删除</button>
        </div>
        <div class="empty" v-if="!materials.length">还没有资料</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}

.panel + .panel {
  margin-top: 12px;
}

.cover-preview {
  width: 200px;
  height: 120px;
  border-radius: 8px;
  background: #eef1f5 center/cover no-repeat;
  flex: none;
}

.add-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.chapter {
  border-top: 1px solid var(--border);
  padding-top: 10px;
  margin-top: 10px;
}

.chapter-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.lesson {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0 6px 12px;
}

.small-btn {
  padding: 2px 10px;
  font-size: 12px;
}

.upload-btn {
  padding: 4px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: 12px;
  cursor: pointer;
  background: var(--panel);
}

.upload-btn:hover {
  border-color: #c9ced6;
}

.upload-btn.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.material {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-right: 14px;
  font-size: 13px;
}

.check input {
  width: auto;
}

.notice {
  color: var(--success);
  font-size: 13px;
}
</style>
