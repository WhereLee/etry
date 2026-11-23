import { get } from '@/shared/api/http'

export function listAuditLogs(params) {
  return get('/admin/audit-logs', params)
}