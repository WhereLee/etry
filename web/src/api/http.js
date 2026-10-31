import axios from 'axios'
import { clearSession, fireUnauthorized, getToken } from '../store/session'

/**
 * 后端统一返回 {code, message, data, timestamp}，HTTP 状态码永远是 200。
 * 所以「成功与否」只能看 code，不能看 status。
 */
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
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      const error = new Error(body.message || '请求失败')
      error.code = body.code
      if (body.code === 40101) {
        // 只有「本来已登录」才说明是失效，应该踢回登录页；
        // 本来就匿名访问（比如未登录看课程详情时去拉评价），
        // 只是这个数据拿不到，不能把人跳走。
        const wasLoggedIn = !!getToken()
        clearSession()
        if (wasLoggedIn) {
          fireUnauthorized()
        }
      }
      return Promise.reject(error)
    }
    return body
  },
  (error) => {
    const message = error?.response?.data?.message || error.message || '网络错误'
    const wrapped = new Error(message)
    wrapped.code = error?.response?.status || -1
    return Promise.reject(wrapped)
  }
)

/** 统一取 data，调用方不用每次都点 .data */
export async function get(url, params) {
  const res = await http.get(url, { params })
  return res.data
}

export async function post(url, body) {
  const res = await http.post(url, body)
  return res.data
}

export async function put(url, body) {
  const res = await http.put(url, body)
  return res.data
}

export async function del(url) {
  const res = await http.delete(url)
  return res.data
}

/** 文件上传（头像、封面）；不要手动设 Content-Type，浏览器会带 boundary */
export async function upload(url, file) {
  const form = new FormData()
  form.append('file', file)
  const res = await http.post(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export default http
