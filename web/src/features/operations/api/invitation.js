import { get, post, put } from '@/shared/api/http'

export function createTeacherInvitation(body) {
  return post('/admin/teacher-invitations', body)
}

export function listTeacherInvitations(params) {
  return get('/admin/teacher-invitations', params)
}

export function revokeTeacherInvitation(id) {
  return put(`/admin/teacher-invitations/${id}/revoke`)
}