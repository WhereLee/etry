import { get, post, put } from '@/shared/api/http'

export function listCatalog() {
  return get('/catalog/series')
}

export function createOrder(seriesId, clientRequestId) {
  return post('/orders', { seriesId, clientRequestId })
}

export function listOrders(params) {
  return get('/orders', params)
}

export function getOrder(orderNo) {
  return get(`/orders/${orderNo}`)
}

export function payOrder(orderNo) {
  return post(`/orders/${orderNo}/pay`)
}

export function closeOrder(orderNo) {
  return post(`/orders/${orderNo}/close`)
}

export function getMockCashier(orderNo) {
  return get(`/payments/mock/${orderNo}`)
}

export function simulateMockPayment(orderNo, scenario) {
  return post(`/payments/mock/${orderNo}/events`, { scenario })
}

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

export function updateSeriesSale(seriesId, saleStatus, priceCents) {
  return put(`/admin/content/series/${seriesId}/sale`, { saleStatus, priceCents })
}
