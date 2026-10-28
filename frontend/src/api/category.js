import request from '../utils/request'

export function getCategoryList(params) {
  return request.get('/category/list', { params })
}

export function addCategory(data) {
  return request.post('/category', data)
}

export function updateCategory(data) {
  return request.put('/category', data)
}

export function deleteCategory(id) {
  return request.delete(`/category/${id}`)
}

export function reorderCategories(data) {
  return request.post('/category/reorder', data)
}
