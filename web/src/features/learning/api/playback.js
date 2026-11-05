import { getToken } from '@/store/session'

export function reportPlayback(payload) {
  const token = getToken()
  if (!token) return Promise.resolve()
  return fetch('/api/playback/events', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload),
    keepalive: true
  }).catch(() => {})
}