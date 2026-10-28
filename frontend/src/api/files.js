import request from '../utils/request'
export const exportBills = params => request.post('/bill/files/export', null, { params })
export const downloadExport = token => request.get(`/bill/files/download/${token}`, { responseType: 'blob' })
export const importTemplate = () => request.get('/bill/files/template', { responseType: 'blob' })
export function previewImport(file) { const data = new FormData(); data.append('file', file); return request.post('/bill/files/import/preview', data) }
export const confirmImport = token => request.post(`/bill/files/import/confirm/${token}`)
export const listAttachments = bill => request.get(`/bill/${bill}/attachments`)
export function uploadAttachment(bill, file) { const data = new FormData(); data.append('file', file); return request.post(`/bill/${bill}/attachments`, data) }
export const getAttachment = (bill, id) => request.get(`/bill/${bill}/attachments/${id}`, { responseType: 'blob' })
export const deleteAttachment = (bill, id) => request.delete(`/bill/${bill}/attachments/${id}`)
export const getNotifications = () => request.get('/notifications')
export const notificationPreference = enabled => request.put('/notifications/preference', null, { params: { enabled } })
export const readAllNotifications = () => request.put('/notifications/read-all')
export function saveDownload(blob, name) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = name; link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
