<template>
  <div v-if="variant === 'settings'" class="theme-setting-grid" :aria-busy="saving">
    <button
      v-for="option in themeOptions"
      :key="option.value"
      type="button"
      class="theme-setting-option"
      :class="{ active: currentTheme === option.value }"
      :disabled="saving"
      @click="handleThemeChange(option.value)"
    >
      <el-icon :size="20"><component :is="option.icon" /></el-icon>
      <span>{{ $t(option.labelKey) }}</span>
    </button>
  </div>
  <el-dropdown v-else trigger="click" @command="handleThemeChange">
    <el-button text size="default" class="theme-btn" :aria-label="$t('profile.theme')">
      <el-icon :size="18" aria-hidden="true">
        <component :is="currentIcon" />
      </el-icon>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="light" :class="{ active: currentTheme === 'light' }">
          <el-icon><Sunny /></el-icon> {{ $t('theme.light') }}
        </el-dropdown-item>
        <el-dropdown-item command="dark" :class="{ active: currentTheme === 'dark' }">
          <el-icon><Moon /></el-icon> {{ $t('theme.dark') }}
        </el-dropdown-item>
        <el-dropdown-item command="eye-care" :class="{ active: currentTheme === 'eye-care' }">
          <el-icon><View /></el-icon> {{ $t('theme.eyeCare') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Sunny, Moon, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useCurrentUser } from '../../composables/useCurrentUser'

const { t } = useI18n()
defineProps({ variant: { type: String, default: 'header' } })
const { profile, setTheme } = useCurrentUser()
const saving = ref(false)
const currentTheme = computed(() => profile.theme || localStorage.getItem('theme') || 'light')

/** 主题图标映射 */
const iconMap = {
  light: Sunny,
  dark: Moon,
  'eye-care': View
}
const currentIcon = computed(() => iconMap[currentTheme.value] || Sunny)
const themeOptions = [
  { value: 'light', icon: Sunny, labelKey: 'theme.light' },
  { value: 'dark', icon: Moon, labelKey: 'theme.dark' },
  { value: 'eye-care', icon: View, labelKey: 'theme.eyeCare' }
]

async function handleThemeChange(theme) {
  if (theme === currentTheme.value || saving.value) return
  saving.value = true
  try {
    await setTheme(theme)
  } catch (error) {
    ElMessage.warning(t('profile.preferenceRollback'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.theme-btn {
  border: none;
  color: #606266;
  padding: 4px 6px;
  transition: transform 0.2s ease;
}
.theme-btn:hover {
  transform: scale(1.1);
}
.el-dropdown-menu__item.active {
  color: var(--color-primary);
  font-weight: 600;
}
[data-theme="dark"] .theme-btn {
  color: #b0b0c0;
}

.theme-setting-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

.theme-setting-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 82px;
  padding: 12px 8px;
  color: var(--text-regular);
  background: var(--bg-input-gradient);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: color 0.18s ease,
              background-color 0.18s ease,
              border-color 0.18s ease,
              transform 0.18s ease;
}

.theme-setting-option:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: var(--color-primary);
}

.theme-setting-option.active {
  color: var(--color-primary);
  background: var(--color-primary-light);
  border-color: var(--color-primary);
  font-weight: 600;
}

.theme-setting-option:disabled {
  cursor: wait;
  opacity: 0.65;
}

@media (max-width: 520px) {
  .theme-setting-grid {
    grid-template-columns: 1fr;
  }

  .theme-setting-option {
    flex-direction: row;
    min-height: 48px;
  }
}
</style>
