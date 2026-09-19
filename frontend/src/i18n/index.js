/**
 * vue-i18n 国际化初始化配置
 * 支持三种语言：简体中文(zh-cn)、英文(en)、日语(ja)
 */
import { createI18n } from 'vue-i18n'
import zhCn from './langs/zh-cn'
import en from './langs/en'
import ja from './langs/ja'

// 从 localStorage 读取已保存的语言设置，默认中文
const savedLang = localStorage.getItem('language') || 'zh-cn'

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: savedLang,
  fallbackLocale: 'zh-cn',
  messages: {
    'zh-cn': zhCn,
    'en': en,
    'ja': ja
  }
})

export default i18n
