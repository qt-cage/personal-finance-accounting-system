<template>
  <!--
    使用 el-config-provider 包裹，实现 Element Plus 组件文案动态切换
    locale 为响应式对象，切换语言时自动更新
  -->
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'
import ja from 'element-plus/dist/locale/ja.mjs'

const { locale } = useI18n()

/**
 * Element Plus 语言包映射
 * 根据 vue-i18n 当前语言自动切换
 */
const localeMap = {
  'zh-cn': zhCn,
  'en': en,
  'ja': ja
}

const documentLangMap = {
  'zh-cn': 'zh-CN',
  en: 'en',
  ja: 'ja'
}

watch(locale, value => {
  document.documentElement.lang = documentLangMap[value] || 'zh-CN'
}, { immediate: true })

const elementLocale = computed(() => {
  return localeMap[locale.value] || zhCn
})
</script>
