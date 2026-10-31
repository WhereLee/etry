<script setup>
import { onMounted, ref } from 'vue'
import {
  courseDetail,
  listCourses,
  materials,
  offline,
  previewPlay,
  publish,
  review
} from '../api/admin'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('PENDING')
const keyword = ref('')
const loading = ref(true)
const error = ref('')
const notice = ref('')

const detail = ref(null)
const detailMaterials = ref([])
const previewUrl = ref('')
const rejectReason = ref('')
const busy = ref(false)

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
    const data = await listCourses({
      status: status.value || undefined,
      keyword: keyword.value || undefined,
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

function flash(msg) {
  notice.value = msg
  setTimeout(() => {
    if (notice.value === msg) notice.value = ''
  }, 2500)
}

async function open(id) {
  error.value = ''
  previewUrl.value = ''
  try {
    detail.value = await courseDetail(id)
    detailMaterials.value = await materials(id).catch(() => [])
  } catch (e) {
    error.value = e.message
  }
}

function close() {
  detail.value = null
  detailMaterials.value = []
  previewUrl.value = ''
  rejectReason.value = ''
}

async function doReview(approved) {
  busy.value = true
  try {
    await review(detail.value.id, approved, approved ? null : rejectReason.value || '资料不完整')
    flash(approved ? '已通过' : '已驳回')
    close()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function doOffline() {
  busy.value = true
  try {
    await offline(detail.value.id)
    flash('已下架')
    close()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function doPublish() {
  busy.value = true
  try {
    await publish(detail.value.id)
    flash('已上架')
    close()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function playPreview(lesson) {
  try {
    const data = await previewPlay(lesson.id)
    previewUrl.value = data.playUrl
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="row wrap" style="margin-bottom: 16px">
      <h2 style="margin: 0">课程审核</h2>
      <div class="spacer"></div>
      <select v-model="status" style="max-width: 140px" @change="page = 1; load()">
        <option value="">全部状态</option>
        <option v-for="(label, key) in STATUS_LABEL" :key="key" :value="key">{{ label }}</option>
      </select>
      <input v-model.trim="keyword" placeholder="标题关键词" style="max-width: 200px" @keyup.enter="page = 1; load()" />
      <button @click="page = 1; load()">搜索</button>
    </div>

    <p class="error" v-if="error">{{ error }}</p>
    <p class="notice" v-if="notice">{{ notice }}</p>

    <div class="empty" v-if="loading">加载中…</div>
    <div class="empty" v-else-if="rows.length === 0">没有符合条件的课程</div>

    <table class="list" v-else>
      <thead>
        <tr>
          <th>课程</th>
          <th style="width: 120px">讲师</th>
          <th style="width: 90px">状态</th>
          <th style="width: 120px">提交时间</th>
          <th style="width: 80px"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in rows" :key="c.id">
          <td>{{ c.title }}</td>
          <td class="muted small">{{ c.teacherName || '-' }}</td>
          <td>
            <span class="tag" v-if="c.status === 'PENDING'">待审核</span>
            <span class="tag plain" v-else>{{ STATUS_LABEL[c.status] }}</span>
          </td>
          <td class="muted small">{{ new Date(c.updatedAt).toLocaleString() }}</td>
          <td><button @click="open(c.id)">查看</button></td>
        </tr>
      </tbody>
    </table>

    <div class="row" style="justify-content: center; margin-top: 16px" v-if="total > size">
      <button :disabled="page <= 1" @click="page--; load()">上一页</button>
      <span class="muted small">{{ page }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
      <button :disabled="page * size >= total" @click="page++; load()">下一页</button>
    </div>

    <div class="mask" v-if="detail">
      <div class="dialog">
        <div class="row">
          <h3 style="margin: 0">{{ detail.title }}</h3>
          <div class="spacer"></div>
          <button @click="close">关闭</button>
        </div>

        <div class="row wrap small muted" style="margin: 8px 0">
          <span>{{ detail.teacherName || '未知讲师' }}</span>
          <span>· {{ STATUS_LABEL[detail.status] }}</span>
          <span v-if="detail.level">· {{ detail.level }}</span>
          <span v-if="detail.sequential">· 按顺序学习</span>
        </div>

        <p class="muted">{{ detail.summary || '暂无简介' }}</p>

        <div class="block">
          <strong class="small">章节课时</strong>
          <div v-for="ch in detail.chapters" :key="ch.id" class="chapter">
            <div class="muted small">{{ ch.title }}</div>
            <div class="row small" v-for="l in ch.lessons" :key="l.id" style="justify-content: space-between">
              <span>{{ l.title }}</span>
              <span class="row" style="gap: 8px">
                <span class="muted">{{ l.hasVideo ? `${Math.round((l.durationSeconds || 0) / 60)} 分钟` : '无视频' }}</span>
                <button v-if="l.hasVideo" class="small-btn" @click="playPreview(l)">预览</button>
              </span>
            </div>
          </div>
          <div class="empty" v-if="!detail.chapters?.length">没有章节</div>
        </div>

        <div class="block" v-if="previewUrl">
          <strong class="small">视频预览</strong>
          <video :src="previewUrl" controls style="width: 100%; margin-top: 6px; border-radius: 6px"></video>
        </div>

        <div class="block" v-if="detailMaterials.length">
          <strong class="small">课件</strong>
          <div class="row small" v-for="m in detailMaterials" :key="m.id" style="justify-content: space-between">
            <a :href="m.downloadUrl" target="_blank" rel="noreferrer">{{ m.title }}</a>
            <span class="muted">{{ Math.round(m.sizeBytes / 1024) }} KB</span>
          </div>
        </div>

        <div class="block" v-if="detail.status === 'PENDING'">
          <input v-model="rejectReason" placeholder="驳回原因（通过时不用填）" />
          <div class="row" style="margin-top: 10px">
            <button class="primary" :disabled="busy" @click="doReview(true)">通过并上架</button>
            <button :disabled="busy" @click="doReview(false)">驳回</button>
          </div>
        </div>

        <div class="block" v-else-if="detail.status === 'PUBLISHED'">
          <button :disabled="busy" @click="doOffline">下架这门课</button>
        </div>

        <div class="block" v-else-if="detail.status === 'OFFLINE'">
          <button class="primary" :disabled="busy" @click="doPublish">重新上架</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 16px;
  overflow-y: auto;
  z-index: 50;
}

.dialog {
  background: var(--panel);
  border-radius: 10px;
  padding: 20px;
  width: 100%;
  max-width: 720px;
}

.block {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.chapter {
  margin-top: 8px;
}

.small-btn {
  padding: 1px 8px;
  font-size: 12px;
}

.notice {
  color: var(--success);
  font-size: 13px;
}
</style>
