import client from './client'

/**
 * 下单：同一笔业务必须复用同一个幂等键，否则后端会当成两笔订单。
 */
export const createOrder = (payload, idempotencyKey) =>
  client.post('/orders', payload, { headers: { 'Idempotency-Key': idempotencyKey } })

export const fetchOrders = (params) => client.get('/orders', { params })

export const fetchOrder = (id) => client.get(`/orders/${id}`)
