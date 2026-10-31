import { get, post } from './http'

/** 上报进度。播放器建议 10 秒一次；服务端只认 position，时长从课时表读 */
export function reportProgress(lessonId, positionSeconds) {
  return post('/learning/progress', { lessonId, positionSeconds })
}

export function courseProgress(courseId) {
  return get(`/learning/courses/${courseId}`)
}

export function myCourses(limit = 20) {
  return get('/learning/my-courses', { limit })
}
