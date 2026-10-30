import client from './client'

export const login = (username, password) => client.post('/auth/login', { username, password })

export const register = (username, password, nickname) =>
  client.post('/auth/register', { username, password, nickname })

export const logout = () => client.post('/auth/logout')

export const changePassword = (oldPassword, newPassword) =>
  client.put('/auth/password', { oldPassword, newPassword })

export const fetchMe = () => client.get('/auth/me')
