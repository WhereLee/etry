import client from './client'

export const fetchProducts = (params) => client.get('/products', { params })

export const fetchProduct = (id) => client.get(`/products/${id}`)
