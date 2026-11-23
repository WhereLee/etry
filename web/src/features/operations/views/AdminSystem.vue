<script setup>
import { computed, onMounted, ref } from 'vue'
import { listRoles } from '@/features/operations/api/admin'
import PageHeader from '@/components/PageHeader.vue'

const loading = ref(true)
const error = ref('')
const roles = ref([])

const ROLE_NOTES = {
  BUYER: '注册买家：浏览/试读/购物车/下单支付/下载已购资料/申请整单退款。',
  OPERATOR: '运营：资料新建、编辑、上传文件、上下架与首页推荐位。',
  CUSTOMER_SERVICE: '客服：查订单与用户、处理退款申请、查看用户已购权益。',
  FINANCE: '财务：对账查询与处理、退款复核、订单/差异报表导出。',
  SUPER_ADMIN: '超级管理员：全部权限点，含账号与角色管理、审计日志、系统配置。'
}

const MODULE_LABELS = {
  material: '资料',
  commerce: '交易',
  identity: '账号',
  platform: '平台'
}

const permissionCatalog = computed(() => {
  const map = new Map()
  for (const role of roles.value) {
    for (const code of role.permissions) {
      if (!map.has(code)) map.set(code, { code, module: code.split(':')[0] })
    }
  }
  return [...map.values()].sort((a, b) => a.code.localeCompare(b.code))
})

const orderedRoles = computed(() =>
  [...roles.value].sort((a, b) => a.code.localeCompare(b.code)))

onMounted(async () => {
  try {
    roles.value = await listRoles()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
})

function has(role, code) {
  return role.permissions.includes(code)
}

function moduleLabel(module) {
  return MODULE_LABELS[module] || module
}
</script>

<template>
  <main class="page admin-page">
    <PageHeader
      eyebrow="SYSTEM CONFIGURATION"
      title="系统配置"
      description="预置角色与权限点矩阵（只读）。角色调整在「用户管理 → 角色」中完成，权限按权限点即时生效，写操作全部进入审计日志。"
    />

    <el-alert v-if="error" type="error" show-icon :closable="false" class="feedback" :title="error" />

    <section v-loading="loading" class="matrix-panel">
      <el-table :data="permissionCatalog" row-key="code" stripe size="small">
        <el-table-column label="权限点" min-width="180" fixed>
          <template #default="{ row }">
            <div class="perm-cell">
              <strong>{{ row.code }}</strong>
              <el-tag size="small" effect="plain" type="info">{{ moduleLabel(row.module) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-for="role in orderedRoles" :key="role.code" :label="role.name" min-width="110" align="center">
          <template #header>
            <el-tooltip :content="ROLE_NOTES[role.code] || role.code" placement="top">
              <span>{{ role.name }}</span>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <el-icon v-if="has(role, row.code)" class="perm-yes"><Check /></el-icon>
            <span v-else class="perm-no">—</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无角色数据" /></template>
      </el-table>
    </section>

    <section class="role-notes">
      <article v-for="role in orderedRoles" :key="role.code" class="role-note">
        <header>
          <strong>{{ role.name }}</strong>
          <code>{{ role.code }}</code>
          <el-tag size="small" :type="role.dataScope === 'ALL' ? 'danger' : 'info'" effect="plain">
            {{ role.dataScope === 'ALL' ? '全量数据' : '仅本人数据' }}
          </el-tag>
        </header>
        <p>{{ ROLE_NOTES[role.code] || role.remark || '—' }}</p>
      </article>
    </section>
  </main>
</template>

<style scoped>
.feedback {
  margin-bottom: 14px;
}

.matrix-panel {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.perm-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.perm-cell strong {
  font-family: var(--font-mono);
  font-size: 11px;
}

.perm-yes {
  color: var(--pine);
  font-size: 15px;
}

.perm-no {
  color: var(--muted);
  font-size: 12px;
}

.role-notes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.role-note {
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: rgba(255, 253, 248, 0.74);
}

.role-note header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-note strong {
  font-family: var(--font-display);
  font-size: 15px;
}

.role-note code {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
}

.role-note p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}
</style>