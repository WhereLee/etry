<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createCourse, listMyCourses } from '../api/teacher'
import { listSubjects, listTags } from '../api/course'

const router = useRouter()

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const loading = ref(true)
const error = ref('')

const subjects = ref([])
const tags = ref([])
const creating = ref(false)
const busy = ref(false)
const form = ref({ title: '', summary: '', subjectId: '', level: '', tagIds: [], sequential: false })

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
    const data = await listMyCourses({
      status: status.value || undefined,
      page: page.value,
      size
    })
    rows.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function openNew() {
  creating.value = true
  error.value = ''
  form.value = {
    title: '',
    summary: '',
    subjectId: subjects.value[0]?.id || '',
    level: '',
    tagIds: [],
    sequential: false
  }
}

async function save() {
  if (!form.value.title.trim()) {
    error.value = '标题必填'
    return
  }
  if (!form.value.subjectId) {
    error.value = '请选择学科'
    return
  }
  busy.value = true
  try {
    const id = await createCourse({
      title: form.value.title.trim(),
      summary: form.value.summary || null,
      subjectId: form.value.subjectId,
      level: form.value.level || null,
      tagIds: form.value.tagIds,
      sequential: form.value.sequential
    })
    creating.value = false
    router.push({ name: 'teacherCourseEdit', params: { id } })
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
    // 拿不到筛选数据不影响列出课程
  }
})
</script>

<template>
  <div class="page">
    <div class="row" style="margin-bottom: 16px">
      <h2 style="margin: 0">我的课程</h2>
      <div class="spacer"></div>
      <select v-model="status" style="max-width: 140px" @change="page = 1; load()">
        <option value="">全部状态</option>
        <option v-for="(label, key) in STATUS_LABEL" :key="key" :value="key">{{ label }}</option>
      </select>
      <button class="primary" @click="openNew">新建课程</button>
    </div>

    <p class="error" v-if="error">{{ error }}</p>

    <div class="panel" v-if="creating" style="margin-bottom: 16px">
      <h3 style="margin-top: 0">新建课程</h3>
      <div class="field">
        <label>标题</label>
        <input v-model="form.title" maxlength="100" />
      </div>
      <div class="field">
        <label>简介</label>
        <input v-model="form.summary" maxlength="200" placeholder="一句话说明这门课讲什么" />
      </div>
      <div class="row wrap">
        <div class="field" style="flex: 1; min-width: 160px">
          <label>学科</label>
          <select v-model="form.subjectId">
            <option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div class="field" style="flex: 1; min-width: 140px">
          <label>难度</label>
          <select v-model="form.level">
            <option value="">不标注</option>
            <option value="BEGINNER">入门</option>
            <option value="INTERMEDIATE">进阶</option>
            <option value="ADVANCED">高级</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label>标签（可多选）</label>
        <div class="row wrap">
          <label class="check" v-for="t in tags" :key="t.id">
            <input type="checkbox" :value="t.id" v-model="form.tagIds" />
            {{ t.name }}
          </label>
          <span class="muted small" v-if="tags.length === 0">还没有标签，请让管理员先创建</span>
        </div>
      </div>
      <label class="check">
        <input type="checkbox" v-model="form.sequential" />
        按章节顺序学习（上一节学完才能看下一节）
      </label>
      <div class="row" style="margin-top: 12px">
        <button class="primary" :disabled="busy" @click="save">创建并继续编辑</button>
        <button :disabled="busy" @click="creating = false">取消</button>
      </div>
    </div>

    <div class="empty" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="rows.length === 0">还没有课程，先建一门</div>

    <table class="list" v-else>
      <thead>
        <tr>
          <th>标题</th>
          <th style="width: 90px">状态</th>
          <th style="width: 90px">课时</th>
          <th style="width: 160px">更新时间</th>
          <th style="width: 80px"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in rows" :key="c.id">
          <td>
            <div>{{ c.title }}</div>
            <div class="muted small" v-if="c.rejectReason">驳回原因：{{ c.rejectReason }}</div>
          </td>
          <td>
            <span class="tag" v-if="c.status === 'PUBLISHED'">已上架</span>
            <span class="tag plain" v-else-if="c.status === 'PENDING'">待审核</span>
            <span class="tag plain" v-else-if="c.status === 'REJECTED'">已驳回</span>
            <span class="tag plain" v-else>{{ STATUS_LABEL[c.status] }}</span>
          </td>
          <td class="muted small">{{ c.videoLessonCount }} / {{ c.lessonCount }}</td>
          <td class="muted small">{{ new Date(c.updatedAt).toLocaleString() }}</td>
          <td>
            <button @click="router.push({ name: 'teacherCourseEdit', params: { id: c.id } })">编辑</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="row" style="justify-content: center; margin-top: 16px" v-if="total > size">
      <button :disabled="page <= 1" @click="page--; load()">上一页</button>
      <span class="muted small">{{ page }}</span>
      <button :disabled="page * size >= total" @click="page++; load()">下一页</button>
    </div>
  </div>
</template>

<style scoped>
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
</style>
