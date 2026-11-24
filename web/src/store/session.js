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
  if (session.user?.role === role) return true
  return (session.user?.roles || []).includes(role)
}

export function hasPermission(permission) {
  return (session.user?.permissions || []).includes(permission)
}

export function hasAnyPermission(permissions) {
  return permissions.some((permission) => hasPermission(permission))
}

export function homePath() {
  if (!isLoggedIn()) return '/materials'
  if (session.mustChangePassword) return '/password'
  if (hasRole('SUPER_ADMIN') || hasPermission('user:read')) return '/admin/users'
  if (hasRole('TEACHER')) return '/teacher/series'
  if (hasPermission('order:read')) return '/admin/orders'
  if (hasPermission('recon:read')) return '/admin/reconciliation'
  if (hasPermission('audit:read')) return '/admin/audit-logs'
  if (hasPermission('material:read')) return '/admin/materials'
  return '/materials'
}
