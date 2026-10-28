<template>
  <el-container class="layout-root">
    <el-aside width="228px" class="layout-aside">
      <AppNavigation />
    </el-aside>

    <el-container class="layout-right">
      <el-header class="app-header">
        <div class="header-left">
          <button
            type="button"
            class="mobile-menu-btn"
            :aria-label="$t('nav.openMenu')"
            @click="mobileNavVisible = true"
          >
            <el-icon><MenuIcon /></el-icon>
          </button>
          <span class="header-date-stamp">{{ currentDate }}</span>
        </div>

        <div class="header-right">
          <div class="header-action"><ThemeSelect /></div>
          <div class="header-action"><LanguageSelect /></div>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-control">
              <UserAvatar :avatar="profile.avatar" :name="displayName" :size="28" />
              <span class="user-name-text">{{ displayName }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon> {{ $t('nav.profile') }}
                </el-dropdown-item>
                <el-dropdown-item command="password">
                  <el-icon><Lock /></el-icon> {{ $t('nav.changePassword') }}
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon> {{ $t('nav.logout') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <div class="app-content">
          <router-view />
        </div>
      </el-main>
    </el-container>

    <el-drawer
      v-model="mobileNavVisible"
      direction="ltr"
      size="280px"
      :with-header="false"
      append-to-body
      class="mobile-nav-drawer"
    >
      <AppNavigation @navigate="mobileNavVisible = false" />
    </el-drawer>

    <ChangePasswordDialog v-model="pwdDialogVisible" />
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, Lock, Menu as MenuIcon, SwitchButton, User } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import AppNavigation from '../components/AppNavigation.vue'
import LanguageSelect from '../components/LanguageSelect/index.vue'
import ThemeSelect from '../components/ThemeSelect/index.vue'
import UserAvatar from '../components/UserAvatar.vue'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'
import { useCurrentUser } from '../composables/useCurrentUser'

const router = useRouter()
const { locale } = useI18n()
const { profile, displayName, loadProfile, clearProfile } = useCurrentUser()

const pwdDialogVisible = ref(false)
const mobileNavVisible = ref(false)

const currentDate = computed(() => new Intl.DateTimeFormat(locale.value, {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  weekday: 'short'
}).format(new Date()))

onMounted(async () => {
  try {
    await loadProfile()
  } catch (e) {
    // 资料加载失败不阻断财务业务页面，个人中心内仍可重试。
  }
})

function handleCommand(command) {
  if (command === 'logout') handleLogout()
  else if (command === 'password') pwdDialogVisible.value = true
  else if (command === 'profile') router.push('/profile')
}

function handleLogout() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  clearProfile()
  router.push('/login')
}
</script>

<style scoped>
.layout-root {
  min-height: 100vh;
  background: var(--bg-page);
}

.layout-aside {
  width: 228px !important;
  flex: 0 0 228px;
  overflow: hidden;
  background-color: #10251a !important;
  background-image:
    radial-gradient(circle at 24% 18%, rgba(255, 255, 255, 0.025) 0 0.55px, transparent 0.8px),
    radial-gradient(circle at 72% 66%, rgba(0, 0, 0, 0.12) 0 0.55px, transparent 0.8px);
  background-position: 0 0, 7px 11px;
  background-size: 15px 19px, 21px 25px;
  border-right: 1px solid rgba(19, 31, 23, 0.62) !important;
  border-radius: 0 !important;
  box-shadow: 2px 0 12px rgba(24, 31, 26, 0.08) !important;
}

.layout-right {
  min-width: 0;
}

.app-header {
  position: relative;
  z-index: 20;
  height: 64px !important;
  padding: 0 24px !important;
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: var(--bg-header) !important;
  border-bottom: 1px solid var(--border-color) !important;
  border-radius: 0 !important;
  box-shadow: inset 0 -1px var(--border-lighter) !important;
}

.header-left,
.header-right,
.header-action {
  display: flex;
  align-items: center;
}

.header-right {
  min-width: 0;
  gap: 4px;
}

.header-date-stamp {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.09em;
  font-variant-numeric: tabular-nums;
}

.mobile-menu-btn {
  display: none;
  width: 38px;
  height: 38px;
  margin-right: 10px;
  padding: 0;
  place-items: center;
  color: var(--text-primary);
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: 6px;
}

.user-control {
  min-height: 38px;
  margin-left: 10px;
  padding: 4px 7px 4px 5px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-regular);
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 7px;
  transition: color 0.16s ease, background-color 0.16s ease, border-color 0.16s ease;
}

.user-control:hover {
  color: var(--text-primary);
  background: var(--bg-menu-hover);
  border-color: var(--border-color);
}

.user-name-text {
  max-width: 120px;
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-arrow {
  color: var(--text-secondary);
  font-size: 11px;
}

.app-main {
  min-width: 0;
  padding: 30px 32px 44px !important;
  overflow-x: hidden;
  overflow-y: auto;
  background-color: var(--bg-page) !important;
  background-image: none;
}

.app-content {
  width: min(100%, 1440px);
  margin: 0 auto;
}

@media (min-width: 761px) and (max-width: 1100px) {
  .layout-aside {
    width: 76px !important;
    flex-basis: 76px;
  }

  .app-main {
    padding: 26px 26px 40px !important;
  }
}

@media (max-width: 760px) {
  .layout-aside {
    display: none;
  }

  .app-header {
    height: 58px !important;
    padding: 0 14px !important;
  }

  .mobile-menu-btn {
    display: grid;
  }

  .header-date-stamp,
  .user-name-text,
  .user-arrow {
    display: none;
  }

  .user-control {
    min-height: 34px;
    margin-left: 2px;
    padding: 2px;
  }

  .app-main {
    padding: 22px 16px 34px !important;
  }
}
</style>

<style>
.mobile-nav-drawer.el-drawer {
  background: #10251a;
  border-right: 1px solid rgba(19, 31, 23, 0.72);
}

.mobile-nav-drawer .el-drawer__body {
  padding: 0;
}
</style>

