import { get, put } from '@/shared/api/http'
import { getToken } from '@/store/session'

export function listSeries() {
  return get('/learning/series')
}

export function getSeries(id) {
  return get(`/learning/series/${id}`)
}

export function playVideo(id) {
  return get(`/learning/videos/${id}/play`)
}

export function saveProgress(videoId, payload) {
  return put(`/learning/videos/${videoId}/progress`, payload)
}

export function saveProgressKeepalive(videoId, payload) {
  const token = getToken()
  if (!token) return
  fetch(`/api/learning/videos/${videoId}/progress`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload),
    keepalive: true
  }).catch(() => {})
}
