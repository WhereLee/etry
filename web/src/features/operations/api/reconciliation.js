import http, { get, post } from '@/shared/api/http'

export function listStatements(params) {
  return get('/admin/reconciliation/statements', params)
}

export function uploadStatement(provider, statementDate, file) {
  const form = new FormData()
  form.append('provider', provider)
  form.append('statementDate', statementDate)
  form.append('file', file)
  return http.post('/admin/reconciliation/statements', form)
}

export function listRuns(fileId) {
  return get(`/admin/reconciliation/statements/${fileId}/runs`)
}

export function executeReconciliation(fileId) {
  return post(`/admin/reconciliation/statements/${fileId}/runs`)
}

export function listBreaks(runId, params) {
  return get(`/admin/reconciliation/runs/${runId}/breaks`, params)
}

export function actOnBreak(breakId, body) {
  return post(`/admin/reconciliation/breaks/${breakId}/actions`, body)
}
