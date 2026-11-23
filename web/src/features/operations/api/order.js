import { get, post } from '@/shared/api/http'

export function listAdminOrders(params) {
  return get('/admin/orders', params)
}

export function listAdminOrderExceptions(params) {
  return get('/admin/orders/exceptions', params)
}

export function getAdminOrder(orderNo) {
  return get(`/admin/orders/${orderNo}`)
}

export function queryAdminOrder(orderNo) {
  return post(`/admin/orders/${orderNo}/query`)
}

export function fulfillAdminOrder(orderNo) {
  return post(`/admin/orders/${orderNo}/fulfill`)
}

export function closeAdminOrder(orderNo) {
  return post(`/admin/orders/${orderNo}/close`)
}

export function refundAdminOrder(orderNo, body) {
  return post(`/admin/orders/${orderNo}/refund`, body)
}

export function refundDuplicateAdminOrder(orderNo, attemptNo, body) {
  return post(`/admin/orders/${orderNo}/attempts/${attemptNo}/refund`, body)
}
export function listRefundRequests(params) {
  return get('/admin/refund-requests', params)
}

export function approveRefundRequest(id) {
  return post(`/admin/refund-requests/${id}/approve`)
}

export function rejectRefundRequest(id, note) {
  return post(`/admin/refund-requests/${id}/reject`, { note })
}
