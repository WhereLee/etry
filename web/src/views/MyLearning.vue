<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myCourses } from '../api/learning'

const router = useRouter()
const rows = ref([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    rows.value = await myCourses(50)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

function open(row) {
  if (row.status !== 'PUBLISHED') return
  router.push({ name: 'learn', params: { id: row.courseId } })
}
</script>

<template>
  <div class="page">
    <h2>我的学习</h2>
    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="rows.length === 0">还没有学过任何课程</div>

    <table class="list" v-else>
      <thead>
        <tr>
          <th style="width: 40%">课程</th>
          <th>进度</th>
          <th>课时</th>
          <th>最近学习</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.courseId">
          <td>
            <a href="#" @click.prevent="open(r)">{{ r.title }}</a>
          </td>
          <td>
            <div class="bar"><span :style="{ width: r.percent + '%' }"></span></div>
            <span class="muted small">{{ r.percent }}%</span>
          </td>
          <td class="muted small">{{ r.completedLessons }} / {{ r.totalLessons }}</td>
          <td class="muted small">{{ new Date(r.lastLearnedAt).toLocaleString() }}</td>
          <td>
            <span class="tag" v-if="r.status === 'PUBLISHED'">在学</span>
            <span class="tag plain" v-else>已下架</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.bar {
  height: 6px;
  width: 120px;
  background: #eef1f5;
  border-radius: 3px;
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  background: var(--primary);
}
</style>
