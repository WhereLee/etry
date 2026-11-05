import { get, post, put } from '@/shared/api/http'

export function login(username, password) {
  return post('/auth/login', { username, password })
}

export function me() {
  return get('/auth/me')
}

export function changePassword(oldPassword, newPassword) {
  return put('/auth/password', { oldPassword, newPassword })
}