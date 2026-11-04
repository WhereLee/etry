<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: '' },
  label: { type: String, default: '' }
})

const meta = computed(() => ({
  DRAFT: { label: '草稿', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
  ACTIVE: { label: '正常', type: 'success' },
  DISABLED: { label: '已停用', type: 'danger' },
  PENDING: { label: '待使用', type: 'warning' },
  USED: { label: '已使用', type: 'success' },
  REVOKED: { label: '已撤销', type: 'info' },
  EXPIRED: { label: '已过期', type: 'danger' },
  CREATED: { label: '待支付', type: 'warning' },
  PAYING: { label: '支付中', type: 'primary' },
  PAID: { label: '已支付', type: 'success' },
  FULFILLED: { label: '已完成', type: 'success' },
  PAY_FAILED: { label: '支付失败', type: 'danger' },
  CLOSED: { label: '已关闭', type: 'info' },
  REFUNDING: { label: '退款中', type: 'warning' },
  REFUNDED: { label: '已退款', type: 'info' },
  REFUND_FAILED: { label: '退款失败', type: 'danger' },
  ON_SALE: { label: '销售中', type: 'success' },
  OFF_SALE: { label: '未开售', type: 'info' }
}[props.status] || { label: props.status || '未知', type: 'info' }))
</script>

<template>
  <el-tag :type="meta.type" effect="light" round>{{ label || meta.label }}</el-tag>
</template>
