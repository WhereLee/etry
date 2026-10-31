<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { myCourses } from '../api/learning'
import { unenroll } from '../api/course'

const router = useRouter()
const rows = ref([])
const loading = ref(true)
const error = ref('')

onMounted(load)

async function load() {
  loading.value = true
  try {
    rows.value = await myCourses(50)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function open(row) {
  if (row.status !== 'PUBLISHED') {
    error.value = '这门课已经下架了，暂时不能继续学。学过的进度还在，重新上架后可以接着看。'
    return
  }
  router.push({ name: 'learn', params: { id: row.courseId } })
}

/** 一列看完：先看课还在不在，再看我和它是什么关系 */
function statusText(row) {
  if (row.status !== 'PUBLISHED') return '已下架'
  if (row.relationStatus === 'COMPLETED') return '已学完'
  if (row.relationStatus === 'JOINED') return '未开始'
  return '在学'
}

function statusTone(row) {
  if (row.status !== 'PUBLISHED') return 'plain'
  if (row.relationStatus === 'COMPLETED') return 'done'
  return ''
}

async function removeRow(row) {
  try {
    await unenroll(row.courseId)
    rows.value = rows.value.filter((r) => r.courseId !== row.courseId)
    error.value = ''
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="page">
    <h2>我的学习</h2>
    <p class="muted small">加入过但还没开始的课也在这里。不想学了可以移出，进度不会清。</p>
    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="rows.length === 0">还没有加入任何课程</div>

    <table class="list" v-else>
      <thead>
        <tr>
          <th style="width: 34%">课程</th>
          <th>进度</th>
          <th>课时</th>
          <th>最近学习</th>
          <th>状态</th>
          <th></th>
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
            <span class="tag" :class="statusTone(r)">{{ statusText(r) }}</span>
          </td>
          <td>
            <button class="link-btn" @click="removeRow(r)">移出</button>
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

.tag.done {
  background: #e8f5e9;
  color: #2e7d32;
  border-color: #c8e6c9;
}

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
