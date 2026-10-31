<script setup>
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { listCourses, listSubjects, listTags } from '../api/course'

const router = useRouter()

const subjects = ref([])
const tags = ref([])
const filters = ref({ subjectId: '', tagId: '', level: '', keyword: '' })
const page = ref(1)
const size = 12
const total = ref(0)
const records = ref([])
const loading = ref(false)
const error = ref('')

const LEVELS = [
  { value: 'BEGINNER', label: '入门' },
  { value: 'INTERMEDIATE', label: '进阶' },
  { value: 'ADVANCED', label: '高级' }
]

function levelLabel(value) {
  return LEVELS.find((l) => l.value === value)?.label || value
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listCourses({
      subjectId: filters.value.subjectId || undefined,
      tagId: filters.value.tagId || undefined,
      level: filters.value.level || undefined,
      keyword: filters.value.keyword || undefined,
      page: page.value,
      size
    })
    records.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  filters.value = { subjectId: '', tagId: '', level: '', keyword: '' }
  search()
}

function totalPages() {
  return Math.max(1, Math.ceil(total.value / size))
}

function go(delta) {
  const next = page.value + delta
  if (next < 1 || next > totalPages()) return
  page.value = next
  load()
}

onMounted(async () => {
  load()
  try {
    subjects.value = await listSubjects()
    tags.value = await listTags()
  } catch {
    // 筛选数据拿不到不影响看列表
  }
})

watch(() => filters.value.subjectId, search)
watch(() => filters.value.tagId, search)
watch(() => filters.value.level, search)
</script>

<template>
  <div class="page">
    <div class="row wrap" style="margin-bottom: 16px">
      <input
        v-model.trim="filters.keyword"
        placeholder="按标题搜索"
        style="max-width: 240px"
        @keyup.enter="search"
      />
      <select v-model="filters.subjectId" style="max-width: 150px">
        <option value="">全部学科</option>
        <option v-for="s in subjects" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <select v-model="filters.tagId" style="max-width: 150px">
        <option value="">全部标签</option>
        <option v-for="t in tags" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
      <select v-model="filters.level" style="max-width: 130px">
        <option value="">全部难度</option>
        <option v-for="l in LEVELS" :key="l.value" :value="l.value">{{ l.label }}</option>
      </select>
      <button class="primary" @click="search">搜索</button>
      <button @click="reset">重置</button>
    </div>

    <p class="error" v-if="error">{{ error }}</p>

    <div class="empty" v-if="!loading && records.length === 0">还没有符合条件的课程</div>

    <div class="grid-cards">
      <div class="card" v-for="c in records" :key="c.id" @click="router.push(`/courses/${c.id}`)">
        <div class="cover" :style="c.coverUrl ? { backgroundImage: `url(${c.coverUrl})` } : {}"></div>
        <div class="body">
          <div class="title">{{ c.title }}</div>
          <div class="muted small">{{ c.teacherName || '未知讲师' }}</div>
          <div class="muted small clamp">{{ c.summary || '暂无简介' }}</div>
          <div class="row small">
            <span class="tag" v-if="c.level">{{ levelLabel(c.level) }}</span>
            <span class="muted">{{ c.videoLessonCount }} 节可学</span>
          </div>
        </div>
      </div>
    </div>

    <div class="row" style="justify-content: center; margin-top: 24px" v-if="total > size">
      <button :disabled="page <= 1" @click="go(-1)">上一页</button>
      <span class="muted small">{{ page }} / {{ totalPages() }}</span>
      <button :disabled="page >= totalPages()" @click="go(1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.card {
  cursor: pointer;
  transition: border-color 0.15s;
}

.card:hover {
  border-color: var(--primary);
}

.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
