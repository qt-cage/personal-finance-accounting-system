import request from '../utils/request'

export function addBill(data) {
  return request.post('/bill', data)
}

export function updateBill(data) {
  return request.put('/bill', data)
}

export function deleteBill(id) {
  return request.delete(`/bill/${id}`)
}

export function deleteBatchBill(ids) {
  return request.delete('/bill/batch', { data: ids })
}

export function getBillPage(params) {
  return request.get('/bill/page', { params })
}

export function getRecentBills() {
  return request.get('/bill/recent')
}

export function getStatistics(params) {
  return request.get('/bill/statistics', { params })
}

export function getCategoryStats(params) {
  return request.get('/bill/category-stats', { params })
}

export function getMonthStats(params) {
  return request.get('/bill/month-stats', { params })
}

export function getYearStats(params) {
  return request.get('/bill/year-stats', { params })
}

export function getMonthSummary(params) {
  return request.get('/bill/month-summary', { params })
}
