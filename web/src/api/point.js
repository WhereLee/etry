import client from './client'

export const fetchPoints = (flowLimit = 20) => client.get('/points', { params: { flowLimit } })
