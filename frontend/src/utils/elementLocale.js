/**
 * Element Plus 语言包管理
 * 响应式语言包 ref，配合 el-config-provider 实现动态切换
 */
import { ref } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import ja from 'element-plus/dist/locale/ja.mjs'

/**
 * Element Plus 语言包映射表
 * key 与 vue-i18n 语言标识保持一致：zh-cn / en / ja
 */
const localeMap = {
  'zh-cn': zhCn,
  'en': en,
  'ja': ja
}

// 响应式 Element Plus 语言包对象，默认中文
const savedLang = localStorage.getItem('language') || 'zh-cn'
const elementLocale = ref(localeMap[savedLang] || zhCn)

/**
 * 切换 Element Plus 语言包
 * @param {string} lang - 语言标识：zh-cn / en / ja
 */
function setElementLocale(lang) {
  elementLocale.value = localeMap[lang] || zhCn
}

export { elementLocale, setElementLocale }
