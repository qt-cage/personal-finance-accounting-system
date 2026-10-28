import request from '../utils/request'

export const getRecurringList = () => request.get('/recurring/list')
export const addRecurring = data => request.post('/recurring', data)
export const updateRecurring = (id, data) => request.put(`/recurring/${id}`, data)
export const deleteRecurring = id => request.delete(`/recurring/${id}`)
export const setRecurringEnabled = (id, enabled) => request.put(`/recurring/${id}/status`, null, { params: { enabled } })
export const runRecurring = () => request.post('/recurring/run')
export const getAssetSnapshot = () => request.get('/assets/snapshot')
