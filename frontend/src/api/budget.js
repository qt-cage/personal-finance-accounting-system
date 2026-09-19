import request from '../utils/request'

export function getBudgetList(params) {
  return request.get('/budget/list', { params })
}

export function saveBudget(data) {
  return request.post('/budget', data)
}

export function deleteBudget(id) {
  return request.delete(`/budget/${id}`)
}
