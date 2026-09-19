import { computed, reactive, ref } from 'vue'
import i18n from '../i18n'
import { getUserInfo, removeUserAvatar, updateUserProfile, uploadUserAvatar } from '../api/user'

const profile = reactive({
  username: '',
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
  profile.nickname = data.nickname || ''
  profile.avatar = data.avatar || ''
  profile.language = data.language || 'zh-cn'
  profile.theme = data.theme || 'light'
  profile.createTime = data.createTime || null
  applyLanguage(profile.language)
  applyTheme(profile.theme)
}

async function loadProfile(force = false) {
  if (loaded.value && !force) return profile
  if (loadingPromise && !force) return loadingPromise

  loading.value = true
  loadError.value = ''
  loadingPromise = getUserInfo()
    .then(res => {
      assignProfile(res.data || {})
      loaded.value = true
      return profile
    })
    .catch(error => {
      loadError.value = error.message || i18n.global.t('profile.loadFailed')
      throw error
    })
    .finally(() => {
      loading.value = false
      loadingPromise = null
    })
  return loadingPromise
}

async function saveNickname(nickname) {
  const normalized = String(nickname || '').trim()
  await updateUserProfile({ nickname: normalized })
  profile.nickname = normalized
}

async function setTheme(theme) {
  const previous = profile.theme || 'light'
  profile.theme = theme
  applyTheme(theme)
  if (!localStorage.getItem('token')) return
  try {
    await updateUserProfile({ theme })
  } catch (error) {
    profile.theme = previous
    applyTheme(previous)
    throw error
  }
}

async function setLanguage(language) {
  const previous = profile.language || 'zh-cn'
  profile.language = language
  applyLanguage(language)
  if (!localStorage.getItem('token')) return
  try {
    await updateUserProfile({ language })
  } catch (error) {
    profile.language = previous
    applyLanguage(previous)
    throw error
  }
}

async function uploadAvatar(file) {
  const res = await uploadUserAvatar(file)
  profile.avatar = res.data.avatarUrl
  return profile.avatar
}

async function clearAvatar() {
  await removeUserAvatar()
  profile.avatar = ''
}

function clearProfile() {
  profile.username = ''
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

window.addEventListener('auth:cleared', clearProfile)

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
