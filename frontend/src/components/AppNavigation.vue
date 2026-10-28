<template>
  <div class="app-navigation">
    <div class="nav-brand">
      <span class="nav-brand-mark" aria-hidden="true"></span>
      <span class="nav-brand-name">{{ siteName || $t('nav.systemName') }}</span>
    </div>

    <el-menu
      :default-active="$route.path"
      router
      background-color="transparent"
      @select="$emit('navigate')"
    >
      <el-menu-item index="/dashboard" :aria-label="$t('nav.dashboard')" :title="$t('nav.dashboard')">
        <el-icon><DataAnalysis /></el-icon>
        <span>{{ $t('nav.dashboard') }}</span>
      </el-menu-item>
      <el-menu-item index="/bill" :aria-label="$t('nav.bill')" :title="$t('nav.bill')">
        <el-icon><Document /></el-icon>
        <span>{{ $t('nav.bill') }}</span>
      </el-menu-item>
      <el-menu-item index="/account" :aria-label="$t('finance.accountTitle')" :title="$t('finance.accountTitle')">
        <el-icon><Wallet /></el-icon>
        <span>{{ $t('finance.accountTitle') }}</span>
      </el-menu-item>
      <el-menu-item index="/category" :aria-label="$t('nav.category')" :title="$t('nav.category')">
        <el-icon><Menu /></el-icon>
        <span>{{ $t('nav.category') }}</span>
      </el-menu-item>
      <el-menu-item index="/budget" :aria-label="$t('nav.budget')" :title="$t('nav.budget')">
        <el-icon><Wallet /></el-icon>
        <span>{{ $t('nav.budget') }}</span>
      </el-menu-item>
      <el-menu-item index="/stats" :aria-label="$t('nav.stats')" :title="$t('nav.stats')">
        <el-icon><TrendCharts /></el-icon>
        <span>{{ $t('nav.stats') }}</span>
      </el-menu-item>
      <el-menu-item index="/recurring" :aria-label="$t('planning.recurringTitle')" :title="$t('planning.recurringTitle')"><el-icon><Document /></el-icon><span>{{ $t('planning.recurringTitle') }}</span></el-menu-item>
      <el-menu-item index="/assets" :aria-label="$t('planning.assetsTitle')" :title="$t('planning.assetsTitle')"><el-icon><DataAnalysis /></el-icon><span>{{ $t('planning.assetsTitle') }}</span></el-menu-item>
      <el-menu-item index="/notifications" :aria-label="$t('files.messages')" :title="$t('files.messages')"><el-icon><Bell /></el-icon><span>{{ $t('files.messages') }}</span></el-menu-item>
      <el-menu-item v-if="profile.role === 'ADMIN'" index="/admin" :aria-label="$t('admin.title')" :title="$t('admin.title')"><el-icon><Setting /></el-icon><span>{{ $t('admin.title') }}</span></el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { DataAnalysis, Document, Menu, Wallet, TrendCharts, Bell, Setting } from '@element-plus/icons-vue'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useCurrentUser } from '../composables/useCurrentUser'
import { getSystemSettings } from '../api/admin'
const { profile } = useCurrentUser()
const siteName = ref('')
async function loadSettings() { try { siteName.value = (await getSystemSettings()).data.siteName } catch {} }
onMounted(() => { loadSettings(); window.addEventListener('system:settings-changed', loadSettings) })
onBeforeUnmount(() => window.removeEventListener('system:settings-changed', loadSettings))

defineEmits(['navigate'])
</script>

<style scoped>
.app-navigation {
  --nav-ink: rgba(247, 241, 227, 0.88);
  --nav-muted: rgba(231, 223, 204, 0.62);
  --nav-active-bg: rgba(178, 194, 174, 0.12);
  --nav-hover-bg: rgba(255, 255, 255, 0.055);
  --nav-copper: #b0835f;
  height: 100%;
  display: flex;
  flex-direction: column;
  color: var(--nav-ink);
}

.nav-brand {
  min-height: 72px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(247, 241, 227, 0.09);
}

.nav-brand-mark {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  border: 1px solid rgba(247, 241, 227, 0.55);
  border-radius: 5px;
  background: linear-gradient(145deg, #f2ead6, #bdc8a9);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.58), 0 3px 8px rgba(0, 0, 0, 0.18);
}

.nav-brand-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--nav-ink);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.app-navigation :deep(.el-menu) {
  flex: 1;
  padding: 18px 12px;
  border-right: 0;
}

.app-navigation :deep(.el-menu-item) {
  position: relative;
  height: 42px;
  margin: 0 0 4px;
  padding: 0 13px !important;
  border: 0;
  border-radius: 6px;
  color: var(--nav-muted);
  font-size: 13px;
  font-weight: 500;
  transition: color 0.16s ease, background-color 0.16s ease;
}

.app-navigation :deep(.el-menu-item:hover) {
  color: var(--nav-ink);
  background: var(--nav-hover-bg);
  transform: none;
}

.app-navigation :deep(.el-menu-item.is-active) {
  color: var(--nav-ink);
  background: var(--nav-active-bg);
  font-weight: 600;
}

.app-navigation :deep(.el-menu-item.is-active::before) {
  display: none;
}

.app-navigation :deep(.el-menu-item.is-active::after) {
  content: "";
  position: absolute;
  top: 13px;
  right: -3px;
  width: 6px;
  height: 16px;
  border-radius: 2px 0 0 2px;
  background: var(--nav-copper);
  box-shadow: inset 1px 0 rgba(255, 255, 255, 0.22), -2px 0 5px rgba(0, 0, 0, 0.16);
}

.app-navigation :deep(.el-menu-item .el-icon) {
  margin-right: 11px;
  color: currentColor;
  font-size: 16px;
}

@media (min-width: 761px) and (max-width: 1100px) {
  .nav-brand {
    justify-content: center;
    padding: 0;
  }

  .nav-brand-name,
  .app-navigation :deep(.el-menu-item span) {
    display: none;
  }

  .app-navigation :deep(.el-menu) {
    padding: 18px 10px;
  }

  .app-navigation :deep(.el-menu-item) {
    justify-content: center;
    padding: 0 !important;
  }

  .app-navigation :deep(.el-menu-item .el-icon) {
    margin-right: 0;
  }
}
</style>
