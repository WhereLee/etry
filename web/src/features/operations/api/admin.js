import { get, post, put } from '@/shared/api/http'

export function listUsers(params) {
  return get('/admin/users', params)
}

export function createUser(body) {
  return post('/admin/users', body)
}

export function changeUserStatus(id, status) {
  return put(`/admin/users/${id}/status`, { status })
}

export function resetUserPassword(id, newPassword) {
  return put(`/admin/users/${id}/password`, { newPassword })
}

export function listAllSeries() {
  return get('/admin/series')
}

export function getUserSeries(id) {
  return get(`/admin/users/${id}/series`)
}

export function assignUserSeries(id, seriesIds) {
  return put(`/admin/users/${id}/series`, { seriesIds })
}

export function listAdminSeriesContent(params) {
  return get('/admin/content/series', params)
}

export function getAdminSeriesContent(id) {
  return get(`/admin/content/series/${id}`)
}

export function updateAdminSeriesStatus(id, status) {
  return put(`/admin/content/series/${id}/status`, { status })
}
