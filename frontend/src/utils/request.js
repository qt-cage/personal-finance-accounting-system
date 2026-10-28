import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import i18n from '../i18n'
import { API_BASE_URL } from './backend'
import { captureSession, isCurrentSession, staleSessionError } from './session'
import { parseFinancialJson } from './financialJson'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  transformResponse: [parseFinancialJson]
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
  config.session = captureSession()
  if (!isPublicAuthRequest(config) && !isCurrentSession(config.session)) throw staleSessionError()
  const token = config.session.token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 携带当前语言到请求头，后端根据此字段返回对应语言的消息
  const lang = localStorage.getItem('language') || 'zh-cn'
  config.headers.lang = lang
  return config
}, error => { throw error }, { synchronous: true })

request.interceptors.response.use(
  async response => {
    if (!isPublicAuthRequest(response.config) && !isCurrentSession(response.config.session)) {
      throw staleSessionError()
    }
    if (response.config.responseType === 'blob' && !response.data.type?.includes('json')) return response.data
    const res = response.config.responseType === 'blob' ? parseFinancialJson(await response.data.text()) : response.data
    if (!isPublicAuthRequest(response.config) && !isCurrentSession(response.config.session)) throw staleSessionError()
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
    if (!isPublicAuthRequest(error.config) && error.config?.session && !isCurrentSession(error.config.session)) {
      return Promise.reject(staleSessionError())
    }
    if (error.response?.status === 401 && !isPublicAuthRequest(error.config)) {
      handleUnauthorized()
    } else {
      ElMessage.error(error.response?.data?.message || i18n.global.t('message.networkError'))
    }
    return Promise.reject(error)
  }
)

export default request
