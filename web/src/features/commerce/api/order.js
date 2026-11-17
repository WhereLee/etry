import { get, post } from '@/shared/api/http'

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