import { del, get, post, put } from './http'

export function listTeacherSeries() {
  return get('/teacher/series')
}

export function getTeacherSeries(id) {
  return get(`/teacher/series/${id}`)
}

export function createTeacherSeries(body) {
  return post('/teacher/series', body)
}

export function updateTeacherSeries(id, body) {
  return put(`/teacher/series/${id}`, body)
}

export function updateTeacherSeriesStatus(id, status) {
  return put(`/teacher/series/${id}/status`, { status })
}

export function createTeacherChapter(seriesId, body) {
  return post(`/teacher/series/${seriesId}/chapters`, body)
}

export function updateTeacherChapter(seriesId, chapterId, body) {
  return put(`/teacher/series/${seriesId}/chapters/${chapterId}`, body)
}

export function deleteTeacherChapter(seriesId, chapterId) {
  return del(`/teacher/series/${seriesId}/chapters/${chapterId}`)
}

export function createTeacherVideo(seriesId, chapterId, body) {
  return post(`/teacher/series/${seriesId}/chapters/${chapterId}/videos`, body)
}

export function requestTeacherVideoUpload(seriesId, chapterId, videoId, body) {
  return post(`/teacher/series/${seriesId}/chapters/${chapterId}/videos/${videoId}/upload-url`, body)
}

export function completeTeacherVideoUpload(seriesId, chapterId, videoId, body) {
  return post(`/teacher/series/${seriesId}/chapters/${chapterId}/videos/${videoId}/complete`, body)
}

export function updateTeacherVideo(seriesId, chapterId, videoId, body) {
  return put(`/teacher/series/${seriesId}/chapters/${chapterId}/videos/${videoId}`, body)
}

export function deleteTeacherVideo(seriesId, chapterId, videoId) {
  return del(`/teacher/series/${seriesId}/chapters/${chapterId}/videos/${videoId}`)
}
