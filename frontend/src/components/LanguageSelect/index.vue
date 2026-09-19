<template>
  <!-- 全局语言切换下拉框组件 -->
  <el-select
    :model-value="currentLang"
    :placeholder="$t('nav.language')"
    :aria-label="$t('nav.language')"
    :style="{ width: variant === 'settings' ? '100%' : '120px' }"
    :loading="saving"
    @change="handleChange"
  >
    <el-option label="简体中文" value="zh-cn" />
    <el-option label="English" value="en" />
    <el-option label="日本語" value="ja" />
  </el-select>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useCurrentUser } from '../../composables/useCurrentUser'

defineProps({ variant: { type: String, default: 'header' } })
const { locale, t } = useI18n()
const { profile, setLanguage } = useCurrentUser()
const saving = ref(false)
const currentLang = computed(() => profile.language || locale.value || 'zh-cn')

/**
 * 切换语言
 * 1. 更新 vue-i18n locale
 * 2. 保存到 localStorage
 * 3. 调用后端接口同步用户语言偏好
 */
async function handleChange(lang) {
  if (lang === currentLang.value || saving.value) return
  saving.value = true
  try {
    await setLanguage(lang)
  } catch (error) {
    ElMessage.warning(t('profile.preferenceRollback'))
  } finally {
    saving.value = false
  }
}
</script>
