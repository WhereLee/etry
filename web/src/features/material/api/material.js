import { get } from '@/shared/api/http'

export const CATEGORIES = [
  { value: '', label: '全部' },
  { value: 'EXAM_PAPER', label: '试卷' },
  { value: 'TEACHING_AID', label: '教辅' },
  { value: 'LECTURE_NOTES', label: '讲义' },
  { value: 'TEXTBOOK', label: '课本' }
]

export const CATEGORY_LABELS = Object.fromEntries(CATEGORIES.map((c) => [c.value, c.label]))

export const STAGE_LABELS = {
  PRIMARY: '小学',
  JUNIOR: '初中',
  SENIOR: '高中',
  GENERAL: '通用'
}

export function listMaterials(params) {
  return get('/materials', params)
}

export function searchMaterials(keyword, size = 20) {
  return get('/materials/search', { keyword, size })
}

export function getMaterial(id) {
  return get(`/materials/${id}`)
}

export function priceText(cents, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format((cents || 0) / 100)
}