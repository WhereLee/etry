<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  askQuestion,
  favorite,
  getCourse,
  listMaterials,
  listQuestions,
  listReviews,
  myReview,
  submitReview,
  unfavorite
} from '../api/course'
import { isLoggedIn } from '../store/session'

const route = useRoute()
const router = useRouter()
const courseId = route.params.id

const course = ref(null)
const materials = ref([])
const reviews = ref([])
const reviewTotal = ref(0)
const mine = ref(null)
const questions = ref([])
const questionTotal = ref(0)

const loading = ref(true)
const error = ref('')
const notice = ref('')

const reviewForm = ref({ rating: 5, content: '' })
const questionText = ref('')

const LEVELS = { BEGINNER: '入门', INTERMEDIATE: '进阶', ADVANCED: '高级' }

const lessons = computed(() => {
  if (!course.value) return []
  return course.value.chapters.flatMap((ch) => ch.lessons)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    course.value = await getCourse(courseId)
    materials.value = await listMaterials(courseId).catch(() => [])
    const r = await listReviews(courseId, 1, 10).catch(() => ({ records: [], total: 0 }))
    reviews.value = r.records || []
    reviewTotal.value = r.total || 0
    const q = await listQuestions(courseId, 1, 10).catch(() => ({ records: [], total: 0 }))
    questions.value = q.records || []
    questionTotal.value = q.total || 0
    if (isLoggedIn()) {
      mine.value = await myReview(courseId).catch(() => null)
      if (mine.value) {
        reviewForm.value.rating = mine.value.rating
        reviewForm.value.content = mine.value.content || ''
      }
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  if (!isLoggedIn()) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    if (course.value.favorited) {
      await unfavorite(courseId)
      course.value.favorited = false
    } else {
      await favorite(courseId)
      course.value.favorited = true
    }
  } catch (e) {
    notice.value = e.message
  }
}

async function saveReview() {
  if (!isLoggedIn()) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  notice.value = ''
  try {
    await submitReview(courseId, reviewForm.value.rating, reviewForm.value.content)
    mine.value = { rating: reviewForm.value.rating, content: reviewForm.value.content }
    await load()
    notice.value = '评价已保存'
  } catch (e) {
    notice.value = e.message
  }
}

async function ask() {
  if (!isLoggedIn()) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!questionText.value.trim()) {
    notice.value = '先写点内容再提问'
    return
  }
  notice.value = ''
  try {
    await askQuestion(courseId, questionText.value.trim())
    questionText.value = ''
    const q = await listQuestions(courseId, 1, 10)
    questions.value = q.records || []
    questionTotal.value = q.total || 0
    notice.value = '提问已提交'
  } catch (e) {
    notice.value = e.message
  }
}

function startLearn() {
  if (!isLoggedIn()) {
    router.push({ name: 'login', query: { redirect: `/courses/${courseId}/learn` } })
    return
  }
  router.push({ name: 'learn', params: { id: courseId } })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>

    <template v-if="course">
      <div class="hero panel">
        <div class="row" style="align-items: flex-start; gap: 20px">
          <div class="cover-box" :style="course.coverUrl ? { backgroundImage: `url(${course.coverUrl})` } : {}"></div>
          <div style="flex: 1">
            <h2 style="margin: 0 0 6px">{{ course.title }}</h2>
            <div class="row small muted wrap" style="gap: 8px; margin-bottom: 8px">
              <span>{{ course.teacherName || '未知讲师' }}</span>
              <span v-if="course.level">· {{ LEVELS[course.level] || course.level }}</span>
              <span>· {{ lessons.filter((l) => l.hasVideo).length }} 节可学</span>
              <span v-if="course.ratingCount">· {{ course.ratingAvg }} 分（{{ course.ratingCount }} 条评价）</span>
            </div>
            <div class="row wrap" style="gap: 6px; margin-bottom: 10px">
              <span class="tag" v-for="t in course.tags" :key="t.id">{{ t.name }}</span>
              <span class="tag plain" v-if="course.sequential">按顺序学习</span>
            </div>
            <p class="muted" style="margin: 0 0 12px">{{ course.summary || '暂无简介' }}</p>
            <div class="row">
              <button class="primary" @click="startLearn">开始学习</button>
              <button v-if="isLoggedIn()" @click="toggleFavorite">
                {{ course.favorited ? '取消收藏' : '收藏' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <p class="notice" v-if="notice">{{ notice }}</p>

      <div class="section panel">
        <h3>课程目录</h3>
        <div v-for="ch in course.chapters" :key="ch.id" class="chapter">
          <div class="chapter-title">{{ ch.title }}</div>
          <div class="lesson" v-for="l in ch.lessons" :key="l.id" @click="startLearn">
            <span>{{ l.title }}</span>
            <span class="muted small">
              {{ Math.round((l.durationSeconds || 0) / 60) }} 分钟
              <span v-if="!l.hasVideo"> · 待上传</span>
            </span>
          </div>
        </div>
      </div>

      <div class="section panel" v-if="materials.length">
        <h3>课件资料</h3>
        <div class="row small material" v-for="m in materials" :key="m.id">
          <a :href="m.downloadUrl" target="_blank" rel="noreferrer">{{ m.title }}</a>
          <span class="muted">{{ m.fileName }} · {{ Math.round(m.sizeBytes / 1024) }} KB</span>
        </div>
      </div>

      <div class="section panel">
        <h3>课程简介</h3>
        <p class="description">{{ course.description || '讲师还没有填写详细介绍' }}</p>
      </div>

      <div class="section panel">
        <h3>评价 <span class="muted small">共 {{ reviewTotal }} 条</span></h3>

        <div class="review-form" v-if="isLoggedIn()">
          <div class="row" style="margin-bottom: 8px">
            <span class="small muted">评分</span>
            <select v-model.number="reviewForm.rating" style="max-width: 100px">
              <option v-for="n in 5" :key="n" :value="n">{{ n }} 星</option>
            </select>
          </div>
          <textarea v-model="reviewForm.content" placeholder="说点什么（可以先只打分）"></textarea>
          <div class="row" style="margin-top: 8px">
            <button class="primary" @click="saveReview">{{ mine ? '更新评价' : '提交评价' }}</button>
            <span class="muted small">完成至少一个课时后才能评价</span>
          </div>
        </div>

        <div class="empty" v-if="reviews.length === 0">还没有人评价</div>
        <div class="review" v-for="r in reviews" :key="r.id">
          <div class="row small">
            <strong>{{ r.nickname || '匿名' }}</strong>
            <span class="muted">{{ r.rating }} 星</span>
          </div>
          <div class="small">{{ r.content }}</div>
        </div>
      </div>

      <div class="section panel">
        <h3>问答 <span class="muted small">共 {{ questionTotal }} 条</span></h3>

        <div v-if="isLoggedIn()" class="row ask-row">
          <input v-model="questionText" placeholder="有疑问？直接问" @keyup.enter="ask" />
          <button class="primary" @click="ask">提问</button>
        </div>

        <div class="empty" v-if="questions.length === 0">还没有人提问</div>
        <div class="question" v-for="q in questions" :key="q.id">
          <div class="row small">
            <strong>{{ q.nickname || '匿名' }}</strong>
            <span class="tag plain" v-if="q.resolved">已解决</span>
            <span class="muted">{{ q.answerCount }} 条回答</span>
          </div>
          <div class="small">{{ q.content }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cover-box {
  width: 260px;
  height: 160px;
  border-radius: 8px;
  background: #eef1f5 center/cover no-repeat;
  flex: none;
}

.section {
  margin-top: 16px;
}

.section h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.chapter + .chapter {
  margin-top: 14px;
}

.chapter-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.lesson {
  display: flex;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
}

.lesson:hover {
  background: #f5f7fa;
}

.material {
  justify-content: space-between;
  padding: 6px 0;
}

.description {
  white-space: pre-wrap;
  margin: 0;
}

.review,
.question {
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}

.review:last-child,
.question:last-child {
  border-bottom: none;
}

.review-form {
  margin-bottom: 16px;
}

.notice {
  color: var(--success);
  font-size: 13px;
}

/* 输入框是 width:100%，放在 flex 行里会把按钮挤扁，这里给它让路 */
.ask-row input {
  flex: 1;
  min-width: 0;
}

.ask-row button {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
