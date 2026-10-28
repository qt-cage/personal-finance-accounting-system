import request from '../utils/request'

export const getAccountList = () => request.get('/account/list')
export const addAccount = data => request.post('/account', data)
export const updateAccount = (id, data) => request.put(`/account/${id}`, data)
export const deleteAccount = id => request.delete(`/account/${id}`)
