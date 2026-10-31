import { del, get, post } from './http'

export function listCourses(params) {
  return get('/courses', params)
}

export function getCourse(id) {
  return get(`/courses/${id}`)
}

export function playLesson(lessonId) {
  return get(`/courses/lessons/${lessonId}/play`)
}

export function listReviews(courseId, page = 1, size = 10) {
  return get(`/courses/${courseId}/reviews`, { page, size })
}

export function myReview(courseId) {
  return get(`/courses/${courseId}/reviews/mine`)
}

export function submitReview(courseId, rating, content) {
  return post(`/courses/${courseId}/reviews`, { rating, content })
}

export function listQuestions(courseId, page = 1, size = 10) {
  return get(`/courses/${courseId}/questions`, { page, size })
}

export function askQuestion(courseId, content) {
  return post(`/courses/${courseId}/questions`, { content })
}

export function getQuestion(id) {
  return get(`/questions/${id}`)
}

export function answerQuestion(id, content) {
  return post(`/questions/${id}/answers`, { content })
}

export function listMaterials(courseId) {
  return get(`/courses/${courseId}/materials`)
}

export function favorite(courseId) {
  return post(`/courses/${courseId}/favorite`)
}

export function unfavorite(courseId) {
  return del(`/courses/${courseId}/favorite`)
}

export function listFavorites(params) {
  return get('/favorites', params)
}

export function listTags() {
  return get('/tags')
}

export function listSubjects() {
  return get('/subjects')
}

export function getTeacher(id) {
  return get(`/teachers/${id}`)
}
