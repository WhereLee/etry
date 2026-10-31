import { del, get, post, put, upload } from './http'

export function listMyCourses(params) {
  return get('/teacher/courses', params)
}

export function getMyCourse(id) {
  return get(`/teacher/courses/${id}`)
}

export function createCourse(body) {
  return post('/teacher/courses', body)
}

export function updateCourse(id, body) {
  return put(`/teacher/courses/${id}`, body)
}

export function deleteCourse(id) {
  return del(`/teacher/courses/${id}`)
}

export function submitCourse(id) {
  return put(`/teacher/courses/${id}/submit`)
}

export function withdrawCourse(id) {
  return put(`/teacher/courses/${id}/withdraw`)
}

export function offlineCourse(id) {
  return put(`/teacher/courses/${id}/offline`)
}

export function uploadCover(file) {
  return upload('/teacher/covers', file)
}

export function addChapter(courseId, body) {
  return post(`/teacher/courses/${courseId}/chapters`, body)
}

export function deleteChapter(id) {
  return del(`/teacher/chapters/${id}`)
}

export function addLesson(chapterId, body) {
  return post(`/teacher/chapters/${chapterId}/lessons`, body)
}

export function updateLesson(id, body) {
  return put(`/teacher/lessons/${id}`, body)
}

export function deleteLesson(id) {
  return del(`/teacher/lessons/${id}`)
}

/** 视频直传签名：拿到 uploadUrl 后前端 PUT 上去，字节不经过服务器 */
export function videoTicket(body) {
  return post('/teacher/videos/ticket', body)
}

export function listMyMaterials(courseId) {
  return get(`/teacher/courses/${courseId}/materials`)
}

export function materialTicket(body) {
  return post('/teacher/materials/ticket', body)
}

export function addMaterial(courseId, body) {
  return post(`/teacher/courses/${courseId}/materials`, body)
}

export function deleteMaterial(id) {
  return del(`/teacher/materials/${id}`)
}

export function dashboard() {
  return get('/teacher/dashboard')
}
