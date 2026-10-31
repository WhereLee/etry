<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMessage, listMessages, markAllRead, markRead } from '../api/message'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(true)
const error = ref('')
const current = ref(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const data = await listMessages({ page: page.value, size })
    rows.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

/** 点开即读，顺手标已读。有跳转链接的通知可以直接过去 */
async function open(row) {
  try {
    current.value = await getMessage(row.id)
    if (!row.isRead) {
      await markRead(row.id)
      row.isRead = true
    }
  } catch (e) {
    error.value = e.message
  }
}

async function readAll() {
  try {
    await markAllRead()
    rows.value.forEach((r) => {
      r.isRead = true
    })
  } catch (e) {
    error.value = e.message
  }
}

function follow(link) {
  if (link) router.push(link)
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
    <div class="row" style="justify-content: space-between; align-items: center">
      <h2 style="margin: 0">消息</h2>
      <button @click="readAll">全部标为已读</button>
    </div>

    <p class="error" v-if="error">{{ error }}</p>
    <div class="empty" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="rows.length === 0">没有消息</div>

    <div v-else style="margin-top: 16px">
      <div class="msg panel" v-for="r in rows" :key="r.id" :class="{ unread: !r.isRead }" @click="open(r)">
        <div class="row" style="justify-content: space-between">
          <strong>{{ r.title }}</strong>
          <span class="muted small">{{ new Date(r.createdAt).toLocaleString() }}</span>
        </div>
      </div>
    </div>

    <div class="panel detail" v-if="current">
      <h3>{{ current.title }}</h3>
      <div class="muted small">{{ new Date(current.createdAt).toLocaleString() }}</div>
      <p class="content">{{ current.content }}</p>
      <button v-if="current.link" class="primary" @click="follow(current.link)">去看看</button>
    </div>

    <div class="row" style="justify-content: center; margin-top: 24px" v-if="total > size">
      <button :disabled="page <= 1" @click="go(-1)">上一页</button>
      <span class="muted small">{{ page }} / {{ totalPages() }}</span>
      <button :disabled="page >= totalPages()" @click="go(1)">下一页</button>
    </div>
  </div>
</template>

<style scoped>
.msg {
  margin-bottom: 8px;
  cursor: pointer;
}

.msg.unread strong::after {
  content: '·';
  color: var(--primary);
  margin-left: 6px;
  font-size: 20px;
  line-height: 0;
  vertical-align: middle;
}

.detail {
  margin-top: 20px;
}

.detail h3 {
  margin: 0 0 4px;
  font-size: 15px;
}

.detail .content {
  white-space: pre-wrap;
  margin: 12px 0;
}
</style>
