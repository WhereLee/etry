import { reactive } from 'vue'

const KEY = 'etry.btry.session'

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}')
  } catch {
    return {}
  }
}

const saved = load()

export const session = reactive({
  token: saved.token || '',
  user: saved.user || null,
  mustChangePassword: !!saved.mustChangePassword
})

export function setSession(data) {
  session.token = data.token
  session.user = data.user
  session.mustChangePassword = !!data.mustChangePassword
  localStorage.setItem(KEY, JSON.stringify(session))
}

export function clearSession() {
  session.token = ''
  session.user = null
  session.mustChangePassword = false
  localStorage.removeItem(KEY)
}

export function getToken() {
  return session.token
}

export function isLoggedIn() {
  return !!session.token
}

export function hasRole(role) {
  return session.user?.role === role
}

export function homePath() {
  if (!isLoggedIn()) return '/login'
  if (session.mustChangePassword) return '/password'
  if (hasRole('ADMIN')) return '/admin/users'
  if (hasRole('TEACHER')) return '/teacher/series'
  return '/learning'
}
