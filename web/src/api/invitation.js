import { get, post, put } from './http'

export function createTeacherInvitation(body) {
  return post('/admin/teacher-invitations', body)
}

export function listTeacherInvitations(params) {
  return get('/admin/teacher-invitations', params)
}

export function revokeTeacherInvitation(id) {
  return put(`/admin/teacher-invitations/${id}/revoke`)
}

export function previewTeacherInvitation(token) {
  return post('/auth/teacher-invitations/preview', { token })
}

export function acceptTeacherInvitation(body) {
  return post('/auth/teacher-invitations/accept', body)
}
