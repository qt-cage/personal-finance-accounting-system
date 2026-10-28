import { computed, reactive, ref } from 'vue'
import i18n from '../i18n'
import { getUserInfo, removeUserAvatar, updateUserProfile, uploadUserAvatar } from '../api/user'
import { captureSession, isCurrentSession, invalidateSession, staleSessionError } from '../utils/session'

const profile = reactive({
  username: '',
  role: 'USER',
  nickname: '',
  avatar: '',
  language: localStorage.getItem('language') || 'zh-cn',
  theme: localStorage.getItem('theme') || 'light',
  createTime: null
})
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')
let loadingPromise = null
let profileRequest = 0
let profileSession = null

function requireCurrentSession(session) {
  if (!isCurrentSession(session)) throw staleSessionError()
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme)
  localStorage.setItem('theme', theme)
}

function applyLanguage(language) {
  i18n.global.locale.value = language
  localStorage.setItem('language', language)
}

function assignProfile(data) {
  profile.username = data.username || ''
  profile.role = data.role === 'ADMIN' ? 'ADMIN' : 'USER'
  profile.nickname = data.nickname || ''
  profile.avatar = data.avatar || ''
  profile.language = data.language || 'zh-cn'
  profile.theme = data.theme || 'light'
  profile.createTime = data.createTime || null
  applyLanguage(profile.language)
  applyTheme(profile.theme)
}

async function loadProfile(force = false) {
  const session = captureSession()
  if (!isCurrentSession(session)) {
    clearProfile(false)
    throw staleSessionError()
  }
  if (loaded.value && !force && isCurrentSession(profileSession)) return profile
  if (loadingPromise && !force) return loadingPromise

  const request = ++profileRequest
  const current = () => request === profileRequest && isCurrentSession(session)
  loading.value = true
  loadError.value = ''
  loadingPromise = getUserInfo()
    .then(res => {
      if (!current()) throw staleSessionError()
      assignProfile(res.data || {})
      profileSession = session
      loaded.value = true
      return profile
    })
    .catch(error => {
      if (current()) loadError.value = error.message || i18n.global.t('profile.loadFailed')
      throw error
    })
    .finally(() => {
      if (current()) {
        loading.value = false
        loadingPromise = null
      }
    })
  return loadingPromise
}

async function saveNickname(nickname) {
  const session = captureSession()
  requireCurrentSession(session)
  const normalized = String(nickname || '').trim()
  await updateUserProfile({ nickname: normalized })
  requireCurrentSession(session)
  profile.nickname = normalized
}

async function setTheme(theme) {
  const session = captureSession()
  requireCurrentSession(session)
  const previous = profile.theme || 'light'
  profile.theme = theme
  applyTheme(theme)
  if (!localStorage.getItem('token')) return
  try {
    await updateUserProfile({ theme })
    requireCurrentSession(session)
  } catch (error) {
    if (isCurrentSession(session)) {
      profile.theme = previous
      applyTheme(previous)
    }
    throw error
  }
}

async function setLanguage(language) {
  const session = captureSession()
  requireCurrentSession(session)
  const previous = profile.language || 'zh-cn'
  profile.language = language
  applyLanguage(language)
  if (!localStorage.getItem('token')) return
  try {
    await updateUserProfile({ language })
    requireCurrentSession(session)
  } catch (error) {
    if (isCurrentSession(session)) {
      profile.language = previous
      applyLanguage(previous)
    }
    throw error
  }
}

async function uploadAvatar(file) {
  const session = captureSession()
  requireCurrentSession(session)
  const res = await uploadUserAvatar(file)
  requireCurrentSession(session)
  profile.avatar = res.data.avatarUrl
  return profile.avatar
}

async function clearAvatar() {
  const session = captureSession()
  requireCurrentSession(session)
  await removeUserAvatar()
  requireCurrentSession(session)
  profile.avatar = ''
}

function clearProfile(rebind = true) {
  invalidateSession(rebind)
  profileRequest++
  profileSession = null
  profile.username = ''
  profile.role = 'USER'
  profile.nickname = ''
  profile.avatar = ''
  profile.createTime = null
  profile.language = localStorage.getItem('language') || 'zh-cn'
  profile.theme = localStorage.getItem('theme') || 'light'
  loaded.value = false
  loading.value = false
  loadError.value = ''
  loadingPromise = null
}

window.addEventListener('auth:cleared', () => clearProfile())
window.addEventListener('storage', event => {
  if (event.storageArea !== localStorage || (event.key !== 'token' && event.key !== null)) return
  // 另一标签页切换身份时，连同旧账单、弹窗一起重建；重载前禁止用新令牌提交旧表单。
  clearProfile(false)
  window.location.reload()
})

export function useCurrentUser() {
  const displayName = computed(() => profile.nickname || profile.username || i18n.global.t('profile.defaultUser'))
  return {
    profile,
    displayName,
    loading,
    loaded,
    loadError,
    loadProfile,
    saveNickname,
    setTheme,
    setLanguage,
    uploadAvatar,
    clearAvatar,
    clearProfile
  }
}
