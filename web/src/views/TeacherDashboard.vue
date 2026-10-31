<script setup>
import { onMounted, ref } from 'vue'
import { dashboard } from '../api/teacher'

const data = ref(null)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    data.value = await dashboard()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <h2>讲师工作台</h2>
    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>

    <template v-if="data">
      <div class="stats">
        <div class="panel stat">
          <div class="num">{{ data.summary.learnerTotal }}</div>
          <div class="muted small">学过我课的人</div>
        </div>
        <div class="panel stat">
          <div class="num">{{ data.summary.completerTotal }}</div>
          <div class="muted small">至少学完一门的人</div>
        </div>
        <div class="panel stat">
          <div class="num">{{ data.summary.ratingAvg || '-' }}</div>
          <div class="muted small">评价均分（{{ data.summary.ratingTotal }} 条）</div>
        </div>
        <div class="panel stat">
          <div class="num" :class="{ warn: data.summary.unansweredQuestions > 0 }">
            {{ data.summary.unansweredQuestions }}
          </div>
          <div class="muted small">待回复提问</div>
        </div>
      </div>

      <table class="list" style="margin-top: 16px">
        <thead>
          <tr>
            <th>课程</th>
            <th style="width: 90px">状态</th>
            <th style="width: 80px">学习人数</th>
            <th style="width: 80px">完课人数</th>
            <th style="width: 80px">完课率</th>
            <th style="width: 100px">评价</th>
            <th style="width: 90px">待回复</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in data.courses" :key="c.courseId">
            <td>{{ c.title }}</td>
            <td>
              <span class="tag" v-if="c.status === 'PUBLISHED'">已上架</span>
              <span class="tag plain" v-else>{{ c.status }}</span>
            </td>
            <td>{{ c.learners }}</td>
            <td>{{ c.completers }}</td>
            <td>{{ c.completionRate }}%</td>
            <td class="muted small">{{ c.ratingTotal ? `${c.ratingAvg} (${c.ratingTotal})` : '-' }}</td>
            <td :class="{ warn: c.unanswered > 0 }">{{ c.unanswered }}</td>
          </tr>
        </tbody>
      </table>

      <div class="empty" v-if="!data.courses.length">还没有课程</div>
    </template>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.stat {
  text-align: center;
}

.num {
  font-size: 24px;
  font-weight: 700;
}

.num.warn,
td.warn {
  color: var(--danger);
}
</style>
