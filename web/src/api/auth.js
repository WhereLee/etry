import { get, post, put } from './http'

export function login(username, password) {
  return post('/auth/login', { username, password })
}

export function register(username, password, nickname) {
  return post('/auth/register', { username, password, nickname })
}

export function logout() {
  return post('/auth/logout')
}

export function me() {
  return get('/auth/me')
}

/**
 * 改密后 token_version 会 +1，当前 token 立刻作废，
 * 返回的 needRelogin=true 表示必须重新登录。
 */
export function changePassword(oldPassword, newPassword) {
  return put('/auth/password', { oldPassword, newPassword })
}
