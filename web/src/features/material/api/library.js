import axios from 'axios'
import { get } from '@/shared/api/http'
import { getToken } from '@/store/session'

export function fetchLibrary() {
  return get('/library/materials')
}

export function previewUrl(id) {
  return `/api/materials/${id}/preview`
}

export function openPreview(id) {
  window.open(previewUrl(id), '_blank', 'noopener')
}

// 下载需要携带 Authorization 且按二进制接收，走裸 axios 绕开 JSON 拦截器
export async function downloadMaterial(id, title) {
  let response
  try {
    response = await axios.get(`/api/library/materials/${id}/download`, {
      responseType: 'blob',
      timeout: 60000,
      headers: { Authorization: `Bearer ${getToken()}` }
    })
  } catch (error) {
    throw new Error(await blobErrorMessage(error))
  }
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${title || '学习资料'}.pdf`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

async function blobErrorMessage(error) {
  const data = error?.response?.data
  if (data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text())
      if (parsed.message) return parsed.message
    } catch {
      // 非 JSON 错误体，落到默认文案
    }
  }
  return data?.message || '下载失败，请稍后重试'
}