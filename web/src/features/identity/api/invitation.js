import { post } from '@/shared/api/http'

export function previewTeacherInvitation(token) {
  return post('/auth/teacher-invitations/preview', { token })
}

export function acceptTeacherInvitation(body) {
  return post('/auth/teacher-invitations/accept', body)
}