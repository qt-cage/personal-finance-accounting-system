import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import i18n from '../i18n'
import { API_BASE_URL } from './backend'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000
})

let handlingUnauthorized = false

function clearSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  window.dispatchEvent(new Event('auth:cleared'))
}

function isPublicAuthRequest(config) {
  return ['/user/login', '/user/register'].some(path => config?.url?.includes(path))
}

function handleUnauthorized() {
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  clearSession()
  ElMessage.warning(i18n.global.t('common.sessionExpired'))

  const navigation = router.currentRoute.value.path === '/login'
    ? Promise.resolve()
    : router.replace('/login')

  Promise.resolve(navigation).finally(() => {
    window.setTimeout(() => { handlingUnauthorized = false }, 500)
  })
}

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 携带当前语言到请求头，后端根据此字段返回对应语言的消息
  const lang = localStorage.getItem('language') || 'zh-cn'
  config.headers.lang = lang
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401 && !isPublicAuthRequest(response.config)) {
        handleUnauthorized()
      } else {
        ElMessage.error(res.message || i18n.global.t('message.requestFailed'))
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    if (error.response?.status === 401 && !isPublicAuthRequest(error.config)) {
      handleUnauthorized()
    } else {
      ElMessage.error(error.response?.data?.message || i18n.global.t('message.networkError'))
    }
    return Promise.reject(error)
  }
)

export default request
