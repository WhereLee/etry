import { del, get, post } from '@/shared/api/http'

export function fetchCart() {
  return get('/cart')
}

export function addToCart(materialId) {
  return post('/cart', { materialId })
}

export function removeFromCart(materialId) {
  return del(`/cart/${materialId}`)
}

export function checkoutCart(materialIds, clientRequestId) {
  return post('/cart/checkout', { materialIds, clientRequestId })
}