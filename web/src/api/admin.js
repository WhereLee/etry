import { get, put } from './http'

export function listCourses(params) {
  return get('/admin/courses', params)
}

export function courseDetail(id) {
  return get(`/admin/courses/${id}`)
}

export function review(id, approved, rejectReason) {
  return put(`/admin/courses/${id}/review`, { approved, rejectReason })
}

export function offline(id) {
  return put(`/admin/courses/${id}/offline`)
}

export function publish(id) {
  return put(`/admin/courses/${id}/publish`)
}

export function materials(id) {
  return get(`/admin/courses/${id}/materials`)
}

export function previewPlay(lessonId) {
  return get(`/admin/courses/lessons/${lessonId}/play`)
}

export function overview() {
  return get('/admin/stats/overview')
}

export function topCourses(limit = 10) {
  return get('/admin/stats/top-courses', { limit })
}
