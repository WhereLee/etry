<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listFavorites } from '../api/course'

const router = useRouter()
const records = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const data = await listFavorites({ page: 1, size: 50 })
    records.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

function open(c) {
  if (c.status !== 'PUBLISHED') return
  router.push({ name: 'course', params: { id: c.id } })
}
</script>

<template>
  <div class="page">
    <h2>我的收藏 <span class="muted small">共 {{ total }} 门</span></h2>
    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="records.length === 0">还没有收藏任何课程</div>

    <div class="grid-cards" v-else>
      <div
        class="card"
        v-for="c in records"
        :key="c.id"
        :class="{ offline: c.status !== 'PUBLISHED' }"
        @click="open(c)"
      >
        <div class="cover" :style="c.coverUrl ? { backgroundImage: `url(${c.coverUrl})` } : {}"></div>
        <div class="body">
          <div class="title">{{ c.title }}</div>
          <div class="muted small">{{ c.teacherName || '未知讲师' }}</div>
          <div class="row small">
            <span class="muted">{{ c.videoLessonCount }} 节可学</span>
            <span class="tag plain" v-if="c.status !== 'PUBLISHED'">已下架</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  cursor: pointer;
}

.card.offline {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
