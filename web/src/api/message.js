import { get, put } from './http'

export function listMessages(params) {
  return get('/messages', params)
}

export function unreadCount() {
  return get('/messages/unread-count')
}

export function getMessage(id) {
  return get(`/messages/${id}`)
}

export function markRead(id) {
  return put(`/messages/${id}/read`)
}

export function markAllRead() {
  return put('/messages/read-all')
}
