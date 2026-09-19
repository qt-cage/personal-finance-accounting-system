<template>
  <!--
    =========================================================
    注册页 - Linear/Stripe 极简居中卡片
    单列居中布局：极简品牌标识 + 注册表单
    修改说明：仅视觉样式改造，业务逻辑零修改
    =========================================================
  -->
  <div class="register-page">
    <!-- 右上角语言切换 -->
    <div class="lang-switch">
      <LanguageSelect />
    </div>

    <!-- 注册卡片 -->
    <div class="register-card">
      <!-- 极简品牌标识 -->
      <div class="brand-mark">
        <span class="brand-logo"></span>
        <span class="brand-name">{{ $t('common.appTitle') }}</span>
      </div>

      <!-- 卡片标题 -->
      <div class="card-header">
        <h1 class="card-title">{{ $t('auth.registerTitle') }}</h1>
        <p class="card-subtitle">{{ $t('auth.title') }}</p>
      </div>

      <!-- 注册表单 -->
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0" class="register-form">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            :placeholder="$t('auth.usernamePlaceholderShort')"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="$t('auth.passwordPlaceholderShort')"
            prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="$t('auth.passwordConfirmPlace')"
            prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input
            v-model="form.nickname"
            :placeholder="$t('auth.nicknamePlace')"
            prefix-icon="UserFilled"
            size="large"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="register-btn"
            :loading="loading"
            @click="handleRegister"
          >
            {{ $t('auth.register') }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 底部登录链接 -->
      <p class="login-link">
        {{ $t('auth.hasAccount') }}<router-link to="/login">{{ $t('auth.goLogin') }}</router-link>
      </p>

      <!-- 底部版权 -->
      <p class="copyright-text">
        &copy; {{ new Date().getFullYear() }} {{ $t('common.appTitle') }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { register } from '../api/user'
import { ElMessage } from 'element-plus'
import LanguageSelect from '../components/LanguageSelect/index.vue'

const router = useRouter()
const { t } = useI18n()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '', confirmPassword: '', nickname: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error(t('auth.confirmError')))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: t('validation.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 20, message: t('validation.usernameLength'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('validation.passwordRequired'), trigger: 'blur' },
    { min: 6, max: 20, message: t('validation.passwordLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('validation.confirmPasswordRequired'), trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function handleRegister() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await register({ username: form.username, password: form.password, nickname: form.nickname })
    ElMessage.success(res.message || t('auth.registerSuccess'))
    router.push('/login')
  } catch (e) {
    // do nothing
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ============================================================
   注册页 - Linear/Stripe 极简居中卡片
   全屏中性灰背景 + 单列居中卡片 + 极简品牌标识
   ============================================================ */

.register-page {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-page);
  z-index: 1000;
  padding: 24px;
  box-sizing: border-box;
}

/* 右上角语言切换 */
.lang-switch {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 10;
}

/* ============================================================
   注册卡片：1px 边框 + 小圆角 + 无阴影
   ============================================================ */
.register-card {
  width: 100%;
  max-width: 400px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 40px;
  box-shadow: none;
  animation: cardFadeIn 0.3s ease-out;
}

@keyframes cardFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ============================================================
   极简品牌标识：色块 + 纯文字
   ============================================================ */
.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 40px;
}
.brand-logo {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  background-color: var(--color-primary);
  flex-shrink: 0;
}
.brand-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

/* ============================================================
   卡片头部：大标题 + 副标题
   ============================================================ */
.card-header {
  margin-bottom: 32px;
}
.card-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
  line-height: 1.2;
}
.card-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* ============================================================
   表单
   ============================================================ */
.register-form {
  margin: 0;
}
.register-form :deep(.el-form-item) {
  margin-bottom: 20px;
}
.register-form :deep(.el-input__wrapper) {
  background-color: var(--bg-input) !important;
  border-radius: var(--radius-md) !important;
  height: 44px;
  padding: 0 12px;
}
.register-form :deep(.el-input__inner) {
  height: 44px;
  font-size: 14px;
  color: var(--text-primary);
}
.register-form :deep(.el-input__inner::placeholder) {
  color: var(--text-placeholder);
  font-size: 14px;
}
.register-form :deep(.el-input__prefix-inner) {
  font-size: 16px;
  color: var(--text-secondary);
  margin-right: 4px;
}

/* ============================================================
   注册按钮：扁平克制
   ============================================================ */
.register-btn {
  width: 100%;
  height: 44px;
  font-size: 14px;
  font-weight: 600;
  border-radius: var(--radius-md) !important;
  letter-spacing: 0;
  text-transform: none;
}

/* ============================================================
   底部登录链接
   ============================================================ */
.login-link {
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 24px;
}
.login-link a {
  color: var(--color-primary);
  text-decoration: none;
  margin-left: 4px;
  font-weight: 500;
  transition: color 0.15s ease;
}
.login-link a:hover {
  color: var(--color-primary-hover);
}

/* ============================================================
   底部版权
   ============================================================ */
.copyright-text {
  text-align: center;
  font-size: 12px;
  color: var(--text-placeholder);
  margin: 0;
  letter-spacing: 0;
}
</style>
