<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myQuestions, withdrawQuestion } from '../api/course'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await myQuestions(page.value, size)
    rows.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

/** 点进去直接落到那条问题，不用自己在问答区翻 */
function open(row) {
  router.push({ name: 'course', params: { id: row.courseId }, query: { question: row.id } })
}

async function withdraw(row) {
  try {
    await withdrawQuestion(row.id)
    await load()
  } catch (e) {
    error.value = e.message
  }
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
</script>

<template>
  <div class="page">
    <h2>我的提问</h2>
    <p class="muted small">有人回答时会收到站内信。问题解决了就回课程里标记一下，标记后不再接受新回答。</p>
    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="rows.length === 0">还没提过问题</div>

    <table class="list" v-else>
      <thead>
        <tr>
          <th style="width: 44%">问题</th>
          <th>课程</th>
          <th>回答</th>
          <th>状态</th>
          <th>时间</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id">
          <td><a href="#" @click.prevent="open(r)">{{ r.content }}</a></td>
          <td class="muted small">{{ r.courseTitle }}</td>
          <td class="muted small">{{ r.answerCount }} 条</td>
          <td>
            <span class="tag" v-if="r.resolved">已解决</span>
            <span class="tag plain" v-else-if="r.answerCount > 0">有回答</span>
            <span class="tag plain" v-else>等回答</span>
          </td>
          <td class="muted small">{{ new Date(r.createdAt).toLocaleString() }}</td>
          <td><button class="link-btn" @click="withdraw(r)">撤回</button></td>
        </tr>
      </tbody>
    </table>

    <div class="row" style="justify-content: center; margin-top: 24px" v-if="total > size">
      <button :disabled="page <= 1" @click="go(-1)">上一页</button>
      <span class="muted small">{{ page }} / {{ totalPages() }}</span>
      <button :disabled="page >= totalPages()" @click="go(1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.link-btn {
  border: none;
  background: none;
  color: var(--muted);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}

.link-btn:hover {
  color: var(--danger, #c62828);
}
</style>
