import request from '../utils/request'

export function login(data) {
  return request.post('/user/login', data)
}

export function register(data) {
  return request.post('/user/register', data)
}

export function getUserInfo() {
  return request.get('/user/info')
}

export function updateUserInfo(data) {
  return request.put('/user/info', null, { params: data })
}

export function updateUserProfile(data) {
  return request.put('/user/profile', data)
}

export function uploadUserAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/user/avatar', formData)
}

export function removeUserAvatar() {
  return request.delete('/user/avatar')
}

export function changePassword(data) {
  return request.put('/user/password', data)
}

export function deleteAccount() {
  return request.delete('/user/account')
}
