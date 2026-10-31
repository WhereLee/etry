import { reactive } from 'vue'

const KEY = 'etry.session'

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}')
  } catch {
    return {}
  }
}

const saved = load()

/** 登录态。刷新页面从 localStorage 恢复；token 过期由接口拦截器统一处理 */
export const session = reactive({
  token: saved.token || '',
  userId: saved.userId ?? null,
  username: saved.username || '',
  nickname: saved.nickname || '',
  roles: saved.roles || [],
  mustChangePassword: !!saved.mustChangePassword
})

export function setSession(data) {
  Object.assign(session, {
    token: data.token,
    userId: data.userId,
    username: data.username,
    nickname: data.nickname,
    roles: data.roles || [],
    mustChangePassword: !!data.mustChangePassword
  })
  localStorage.setItem(KEY, JSON.stringify(session))
}

export function clearSession() {
  Object.assign(session, {
    token: '',
    userId: null,
    username: '',
    nickname: '',
    roles: [],
    mustChangePassword: false
  })
  localStorage.removeItem(KEY)
}

export function getToken() {
  return session.token
}

export function isLoggedIn() {
  return !!session.token
}

export function hasRole(code) {
  return session.roles.includes(code)
}

/** token 失效时由拦截器回调，router 在启动时注册跳转逻辑 */
let unauthorizedHandler = () => {}
export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn
}
export function fireUnauthorized() {
  unauthorizedHandler()
}
