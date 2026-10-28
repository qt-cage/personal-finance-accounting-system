<template>
  <div class="profile-page">
    <PageHeader :title="$t('profile.title')" :description="$t('profile.subtitle')" />

    <el-card v-if="loading && !loaded" shadow="never" class="profile-card loading-card">
      <el-skeleton :rows="7" animated />
    </el-card>

    <el-card v-else-if="loadError && !loaded" shadow="never" class="profile-card error-card">
      <el-result icon="warning" :title="$t('profile.loadFailed')" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" :loading="loading" @click="retryLoad">
            {{ $t('profile.retry') }}
          </el-button>
        </template>
      </el-result>
    </el-card>

    <template v-else>
      <el-card shadow="never" class="profile-card identity-card">
        <div class="identity-content">
          <div class="avatar-area">
            <UserAvatar :avatar="avatarPreview || profile.avatar" :name="displayName" :size="88" />
            <input
              ref="fileInput"
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png,.jpg,.jpeg,.png"
              @change="handleFileSelected"
            />
            <div class="avatar-actions">
              <el-button type="primary" :loading="avatarUploading" @click="openFilePicker">
                <el-icon><Upload /></el-icon>
                {{ $t('profile.uploadAvatar') }}
              </el-button>
              <el-button
                v-if="profile.avatar"
                :disabled="avatarUploading"
                @click="handleRemoveAvatar"
              >
                {{ $t('profile.removeAvatar') }}
              </el-button>
              <span class="avatar-hint">{{ $t('profile.avatarHint') }}</span>
            </div>
          </div>

          <div class="identity-meta">
            <span class="identity-kicker">{{ $t('profile.personalCard') }}</span>
            <h2>{{ displayName }}</h2>
            <span class="username">@{{ profile.username }}</span>
            <div class="join-time">
              <el-icon><Calendar /></el-icon>
              <span>{{ $t('profile.joinedAt', { date: formattedJoinDate }) }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <div class="profile-grid">
        <el-card shadow="never" class="profile-card section-card">
          <template #header>
            <div class="section-heading">
              <div class="section-icon"><User /></div>
              <div>
                <h2>{{ $t('profile.basicInfo') }}</h2>
                <p>{{ $t('profile.basicInfoHint') }}</p>
              </div>
            </div>
          </template>

          <el-form label-position="top" @submit.prevent="handleSaveNickname">
            <el-form-item :label="$t('profile.username')">
              <el-input :model-value="profile.username" disabled>
                <template #suffix><el-icon><Lock /></el-icon></template>
              </el-input>
              <div class="field-help">{{ $t('profile.usernameReadonly') }}</div>
            </el-form-item>
            <el-form-item :label="$t('profile.nickname')">
              <el-input
                v-model="nicknameDraft"
                maxlength="50"
                show-word-limit
                :placeholder="$t('profile.nicknamePlaceholder')"
                @keyup.enter="handleSaveNickname"
              />
            </el-form-item>
            <div class="form-actions">
              <el-button
                type="primary"
                :loading="profileSaving"
                :disabled="!nicknameChanged"
                @click="handleSaveNickname"
              >
                {{ $t('profile.saveChanges') }}
              </el-button>
            </div>
          </el-form>
        </el-card>

        <div class="right-column">
          <el-card shadow="never" class="profile-card section-card">
            <template #header>
              <div class="section-heading">
                <div class="section-icon"><Brush /></div>
                <div>
                  <h2>{{ $t('profile.preferences') }}</h2>
                  <p>{{ $t('profile.preferencesHint') }}</p>
                </div>
              </div>
            </template>

            <div class="setting-block">
              <label>{{ $t('profile.theme') }}</label>
              <ThemeSelect variant="settings" />
            </div>
            <div class="setting-block">
              <label>{{ $t('profile.language') }}</label>
              <LanguageSelect variant="settings" />
            </div>
            <div class="setting-block"><NotificationPreference /></div>
            <div class="setting-block"><BillFileTools export-only /></div>
          </el-card>

          <el-card shadow="never" class="profile-card section-card security-card">
            <template #header>
              <div class="section-heading">
                <div class="section-icon"><Key /></div>
                <div>
                  <h2>{{ $t('profile.security') }}</h2>
                  <p>{{ $t('profile.securityHint') }}</p>
                </div>
              </div>
            </template>
            <div class="security-action">
              <div>
                <strong>{{ $t('nav.changePassword') }}</strong>
                <span>{{ $t('profile.passwordHint') }}</span>
              </div>
              <el-button @click="passwordDialogVisible = true">{{ $t('profile.changePasswordAction') }}</el-button>
            </div>
          </el-card>
        </div>
      </div>
    </template>

    <ChangePasswordDialog v-model="passwordDialogVisible" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Brush, Calendar, Key, Lock, Upload, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UserAvatar from '../components/UserAvatar.vue'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'
import PageHeader from '../components/PageHeader.vue'
import LanguageSelect from '../components/LanguageSelect/index.vue'
import ThemeSelect from '../components/ThemeSelect/index.vue'
import { useCurrentUser } from '../composables/useCurrentUser'
import NotificationPreference from '../components/NotificationPreference.vue'
import BillFileTools from '../components/BillFileTools.vue'

const { t } = useI18n()
const {
  profile,
  displayName,
  loading,
  loaded,
  loadError,
  loadProfile,
  saveNickname,
  uploadAvatar,
  clearAvatar
} = useCurrentUser()

const nicknameDraft = ref('')
const profileSaving = ref(false)
const avatarUploading = ref(false)
const avatarPreview = ref('')
const fileInput = ref()
const passwordDialogVisible = ref(false)

const nicknameChanged = computed(() => nicknameDraft.value.trim() !== String(profile.nickname || '').trim())
const formattedJoinDate = computed(() => {
  if (!profile.createTime) return t('profile.unknownDate')
  const localeMap = { 'zh-cn': 'zh-CN', en: 'en-US', ja: 'ja-JP' }
  const date = new Date(profile.createTime)
  if (Number.isNaN(date.getTime())) return t('profile.unknownDate')
  return new Intl.DateTimeFormat(localeMap[profile.language] || 'zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric'
  }).format(date)
})

watch(() => profile.nickname, value => {
  nicknameDraft.value = value || ''
}, { immediate: true })

onMounted(async () => {
  try {
    await loadProfile()
  } catch (error) {
    // 页面内提供重试入口。
  }
})

onBeforeUnmount(() => releasePreview())

async function retryLoad() {
  try {
    await loadProfile(true)
  } catch (error) {
    // 页面内继续显示错误状态。
  }
}

async function handleSaveNickname() {
  if (!nicknameChanged.value || profileSaving.value) return
  const nickname = nicknameDraft.value.trim()
  if (nickname.length > 50) {
    ElMessage.warning(t('profile.nicknameTooLong'))
    return
  }
  profileSaving.value = true
  try {
    await saveNickname(nickname)
    nicknameDraft.value = nickname
    ElMessage.success(t('profile.profileSaved'))
  } catch (error) {
    // 保留草稿，便于用户修正或重试。
  } finally {
    profileSaving.value = false
  }
}

function openFilePicker() {
  if (!avatarUploading.value) fileInput.value?.click()
}

async function handleFileSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    ElMessage.warning(t('profile.avatarTypeError'))
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning(t('profile.avatarSizeError'))
    return
  }

  releasePreview()
  avatarPreview.value = URL.createObjectURL(file)
  avatarUploading.value = true
  try {
    await uploadAvatar(file)
    ElMessage.success(t('profile.avatarUploaded'))
  } catch (error) {
    // 全局响应拦截器负责展示错误，原头像保持不变。
  } finally {
    avatarUploading.value = false
    releasePreview()
  }
}

async function handleRemoveAvatar() {
  try {
    await ElMessageBox.confirm(
      t('profile.removeAvatarConfirm'),
      t('common.tip'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
    avatarUploading.value = true
    await clearAvatar()
    ElMessage.success(t('profile.avatarRemoved'))
  } catch {
    // 取消不提示；请求错误已由全局拦截器展示。
  } finally {
    avatarUploading.value = false
  }
}

function releasePreview() {
  if (avatarPreview.value?.startsWith('blob:')) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
}
</script>

<style scoped>
.profile-page {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
}

.profile-card {
  border: 1px solid var(--border-color) !important;
  border-radius: 10px !important;
  background: var(--bg-card) !important;
  box-shadow: var(--shadow-soft) !important;
}

.profile-card:hover {
  transform: none !important;
  box-shadow: var(--shadow-soft) !important;
}

.identity-card {
  position: relative;
  margin-bottom: 20px;
  overflow: hidden;
}

.identity-card::after {
  content: "";
  position: absolute;
  right: 22px;
  bottom: 16px;
  width: 34px;
  height: 1px;
  background: color-mix(in srgb, var(--color-warning) 58%, transparent);
}

.identity-content {
  display: flex;
  align-items: center;
  gap: 28px;
  min-height: 132px;
}

.avatar-area {
  display: flex;
  align-items: center;
  gap: 18px;
}

.avatar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.avatar-hint {
  flex-basis: 100%;
  color: var(--text-secondary);
  font-size: 12px;
}

.identity-meta {
  min-width: 0;
  padding-left: 28px;
  border-left: 1px solid var(--border-color);
}

.identity-kicker {
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.identity-meta h2 {
  margin: 7px 0 2px;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.username {
  color: var(--text-secondary);
  font-size: 13px;
}

.join-time {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  color: var(--text-secondary);
  font-size: 13px;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 20px;
  align-items: start;
}

.right-column {
  display: grid;
  gap: 20px;
}

.section-card :deep(.el-card__header) {
  padding: 17px 20px;
  border-bottom-color: var(--border-color);
}

.section-card :deep(.el-card__body) {
  padding: 20px;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-heading h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
}

.section-heading p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.section-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  color: var(--color-primary);
  background: transparent;
}

.field-help {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 12px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.setting-block + .setting-block {
  margin-top: 22px;
}

.setting-block > label {
  display: block;
  margin-bottom: 10px;
  color: var(--text-regular);
  font-size: 13px;
  font-weight: 600;
}

.security-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.security-action div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.security-action strong {
  color: var(--text-primary);
  font-size: 14px;
}

.security-action span {
  color: var(--text-secondary);
  font-size: 12px;
}

.loading-card,
.error-card {
  min-height: 340px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

@media (max-width: 1000px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .identity-content,
  .avatar-area {
    align-items: flex-start;
    flex-direction: column;
  }

  .identity-meta {
    width: 100%;
    padding: 18px 0 0;
    border-top: 1px solid var(--border-color);
    border-left: 0;
  }

  .security-action {
    align-items: stretch;
    flex-direction: column;
  }

  .avatar-actions,
  .avatar-actions .el-button,
  .form-actions .el-button,
  .security-action .el-button {
    width: 100%;
  }
}
</style>
