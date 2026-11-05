import axios from 'axios'
import { clearSession, getToken } from '@/store/session'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const hadToken = !!getToken()
    const status = error?.response?.status
    const message = error?.response?.data?.message || '网络请求失败，请稍后重试'
    if (status === 401 && hadToken) {
      clearSession()
      window.location.href = `${import.meta.env.BASE_URL}login`
    }
    if (status === 428 && hadToken) {
      window.location.href = `${import.meta.env.BASE_URL}password?forced=1`
    }
    return Promise.reject(new Error(message))
  }
)

export function get(url, params) {
  return http.get(url, { params })
}

export function post(url, body) {
  return http.post(url, body)
}

export function put(url, body) {
  return http.put(url, body)
}

export function del(url) {
  return http.delete(url)
}

export function upload(url, file) {
  const form = new FormData()
  form.append('file', file)
  return http.post(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export default http