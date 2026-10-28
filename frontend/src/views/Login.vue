<template>
  <!--
    =========================================================
    登录页面 - 左右分屏（Figma/Notion 风格）
    左侧 58%：品牌插画区（现代主义建筑室内插画 + 深色背景）
    右侧 42%：编辑式纸张表单
    窄屏（≤900px）：左侧隐藏，右侧全屏居中
    修改说明：仅视觉样式改造，业务逻辑零修改
    =========================================================
  -->
  <div class="login-page">
    <!-- ============== 左侧：品牌插画区 ============== -->
    <aside class="brand-panel">
      <!-- 插画主体：真实书页纹理支持悬停、点击、键盘与自动翻页。 -->
      <InteractiveLedgerIllustration />

      <!-- 编辑式品牌签名：强化归属感，不与右侧产品名称争夺层级。 -->
      <div class="brand-signature">
        <span class="brand-signature-logo"></span>
        <span class="brand-signature-text">PERSONAL FINANCE</span>
      </div>
    </aside>

    <!-- ============== 右侧：登录卡片区 ============== -->
    <main class="form-panel">
      <!-- 右上角语言切换 + 主题切换 -->
      <div class="top-actions">
        <div class="top-action-item"><ThemeSelect /></div>
        <div class="top-action-item"><LanguageSelect /></div>
      </div>

      <!-- 表单 + 版权垂直居中容器 -->
      <div class="login-wrapper">
        <!-- 复古皮革账本：封皮负责材质，内嵌账页承载登录内容。 -->
        <div class="login-surface" :class="{ 'is-page-turning': pageTurning }">
          <div class="ledger-page">
          <div class="card-header">
            <h1 class="card-title">{{ $t('auth.welcomeBack') }}</h1>
            <p class="card-subtitle">{{ $t('auth.loginSubtitle') }}</p>
          </div>

          <!-- 登录表单 -->
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="0"
            label-position="top"
            class="login-form"
            @keyup.enter="handleLogin"
          >
            <!-- 用户名 -->
            <el-form-item prop="username" :label="$t('auth.username')" for="login-username">
              <el-input
                v-model="form.username"
                input-id="login-username"
                name="username"
                autocomplete="username"
                spellcheck="false"
                :aria-label="$t('auth.username')"
                :placeholder="$t('auth.usernamePlaceholder')"
                size="large"
              />
            </el-form-item>

            <!-- 密码（Element Plus 自带 show-password 眼睛切换图标） -->
            <el-form-item prop="password" :label="$t('auth.password')" for="login-password">
              <el-input
                v-model="form.password"
                input-id="login-password"
                name="password"
                autocomplete="current-password"
                :aria-label="$t('auth.password')"
                type="password"
                :placeholder="$t('auth.passwordPlaceholder')"
                show-password
                size="large"
              />
            </el-form-item>

            <!-- 记住我 + 忘记密码 -->
            <el-form-item>
              <div class="form-options">
                <el-checkbox v-model="rememberMe" class="remember-checkbox">
                  {{ $t('auth.rememberMe') }}
                </el-checkbox>
                <button type="button" class="forgot-link" @click="handleForgot">
                  {{ $t('auth.forgotPassword') }}
                </button>
              </div>
            </el-form-item>

            <!-- 登录按钮（带涟漪动效） -->
            <el-form-item>
              <button
                type="button"
                class="login-btn"
                :class="{ 'is-loading': loading }"
                :disabled="loading"
                :aria-busy="loading"
                @click="handleLogin"
              >
                <span class="login-btn-text">{{ $t('auth.login') }}</span>
                <span class="login-btn-loader" v-if="loading"></span>
              </button>
            </el-form-item>
          </el-form>

          <!-- 还没有账户？立即注册 -->
          <div class="register-row">
            <span class="register-prefix">{{ $t('auth.noAccountPrefix') }}</span>
            <router-link to="/register" class="register-link">
              {{ $t('auth.goRegister') }}
            </router-link>
          </div>

          <!-- 右下角翻页巧思：按钮本身可聚焦，页角只承担视觉反馈。 -->
          <button
            type="button"
            class="ledger-page-corner"
            :aria-label="$t('auth.turnLedgerPage')"
            :title="$t('auth.turnLedgerPage')"
            @click="triggerLedgerPageTurn"
          ></button>
          <span class="ledger-turn-leaf" aria-hidden="true"></span>
          </div>
        </div>

        <!-- 底部版权 -->
        <p class="copyright-text">
          &copy; {{ new Date().getFullYear() }} {{ $t('common.appTitle') }}
        </p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { login } from '../api/user'
import { ElMessage } from 'element-plus'
import LanguageSelect from '../components/LanguageSelect/index.vue'
import ThemeSelect from '../components/ThemeSelect/index.vue'
import InteractiveLedgerIllustration from '../components/InteractiveLedgerIllustration.vue'
import { useCurrentUser } from '../composables/useCurrentUser'

const router = useRouter()
const { t, locale } = useI18n()
const formRef = ref()
const loading = ref(false)
const pageTurning = ref(false)
let pageTurnTimer
const { clearProfile } = useCurrentUser()

/** 记住我状态 */
const rememberMe = ref(false)

// 表单数据模型
const form = reactive({ username: '', password: '' })

// 表单校验规则（仅替换提示文字为多语言key）
const rules = {
  username: [{ required: true, message: t('validation.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('validation.passwordRequired'), trigger: 'blur' }]
}

/** 初始化：仅读取记住的用户名，浏览器不保存明文密码。 */
onMounted(() => {
  const savedUsername = localStorage.getItem('rememberedUsername')
  if (savedUsername) {
    form.username = savedUsername
    rememberMe.value = true
  }
  // 清理旧版本可能遗留的明文密码。
  localStorage.removeItem('rememberedPassword')
})

/** 忘记密码：当前版本为占位提示，未实现找回流程 */
function handleForgot() {
  ElMessage.info(t('auth.recoveryHelp'))
}

/** 触发一次克制的页角翻动，不改变表单内容或登录状态。 */
function triggerLedgerPageTurn() {
  if (pageTurning.value) return
  pageTurning.value = true
  window.clearTimeout(pageTurnTimer)
  pageTurnTimer = window.setTimeout(() => {
    pageTurning.value = false
  }, 1020)
}

onBeforeUnmount(() => window.clearTimeout(pageTurnTimer))

/** 登录处理函数（与原有一致） */
async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userId', res.data.userId)

    // “记住我”只保存用户名，不保存密码。
    if (rememberMe.value) {
      localStorage.setItem('rememberedUsername', form.username)
    } else {
      localStorage.removeItem('rememberedUsername')
    }

    // 如果后端返回了语言偏好，同步到本地
    if (res.data.language) {
      localStorage.setItem('language', res.data.language)
      locale.value = res.data.language
    }
    if (res.data.theme) {
      localStorage.setItem('theme', res.data.theme)
      document.documentElement.setAttribute('data-theme', res.data.theme)
    }
    clearProfile()
    ElMessage.success(res.message || t('auth.loginSuccess'))
    router.push('/dashboard')
  } catch (e) {
    // 校验失败已在响应拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ============================================================
   登录页 - 左右分屏（Figma/Notion 风格）
   左侧 58%：品牌插画区
   右侧 42%：登录卡片（保留原有 3D 样式）
   ============================================================ */

.login-page {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  z-index: 1000;
  overflow: hidden;
  background: #ffffff;
}

/* ============================================================
   左侧：品牌插画区（58%）
   深色背景与现代主义建筑室内插画融为一体，形成克制的品牌画廊氛围
   ============================================================ */
.brand-panel {
  position: relative;
  width: 58%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  /* 品牌区域固定使用插画自身的森林绿色场，不随表单主题改变色相。 */
  background: linear-gradient(135deg, #0c2717 0%, #0a2418 52%, #072014 100%);
  overflow: hidden;
  padding: 0;
  box-sizing: border-box;
}

.brand-signature {
  position: absolute;
  top: 44px;
  left: 44px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 11px;
  color: rgba(244, 237, 220, 0.86);
  pointer-events: none;
}

.brand-signature-logo {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border: 1px solid rgba(244, 237, 220, 0.48);
  border-radius: 5px;
  background: linear-gradient(145deg, rgba(244, 237, 220, 0.96), rgba(203, 216, 183, 0.78));
}

.brand-signature-text {
  font-family: Inter, "SF Pro Display", "Segoe UI", sans-serif;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.18em;
  white-space: nowrap;
}

/* ============================================================
   右侧：编辑式纸张表单（42%）
   以暖纸色、墨色排版和细线层级承接左侧手绘插画。
   ============================================================ */
.form-panel {
  --login-paper: #f1efe8;
  --login-paper-soft: rgba(255, 255, 255, 0.76);
  --login-paper-hover: #ffffff;
  --login-autofill: #ffffff;
  --login-ink: #242621;
  --login-muted: #676b64;
  --login-faint: #8b9088;
  --login-rule: rgba(36, 38, 33, 0.15);
  --login-rule-strong: rgba(36, 38, 33, 0.28);
  --login-focus: rgba(45, 90, 74, 0.14);
  --ledger-sheet: #fffdf7;
  --ledger-under-sheet: #ebe9e0;
  --ledger-ruling: rgba(50, 59, 47, 0.055);
  --ledger-margin-line: rgba(158, 82, 49, 0.38);
  --ledger-shadow: rgba(47, 44, 37, 0.08);
  --ledger-cover-base: #344337;
  --ledger-cover-border: rgba(35, 49, 39, 0.44);
  --ledger-page-edge: #d8c9b1;
  --panel-ledger-rule: rgba(58, 67, 56, 0.045);
  --panel-margin-rule: rgba(150, 90, 58, 0.075);
  --panel-fiber-dark: rgba(64, 59, 48, 0.10);
  --panel-fiber-light: rgba(255, 255, 255, 0.68);
  --panel-emboss-light: rgba(255, 255, 255, 0.42);
  --panel-emboss-shadow: rgba(64, 59, 48, 0.08);
  --panel-ruling-opacity: 0.78;
  --panel-grain-opacity: 0.46;
  position: relative;
  isolation: isolate;
  width: 42%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--login-paper);
  border-left: 1px solid var(--login-rule);
  padding: 32px clamp(28px, 4vw, 64px);
  box-sizing: border-box;
  overflow: hidden;
}

/* 外层只保留信笺纸的横向账簿线与装订边线，避免与登录簿争夺主体层级。 */
.form-panel::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: var(--panel-ruling-opacity);
  background-image:
    linear-gradient(
      to right,
      transparent 0,
      transparent 63px,
      var(--panel-margin-rule) 63px,
      var(--panel-margin-rule) 64px,
      transparent 64px
    ),
    repeating-linear-gradient(
      to bottom,
      transparent 0,
      transparent 47px,
      var(--panel-ledger-rule) 47px,
      var(--panel-ledger-rule) 48px
    );
}

/* 两组错位的微纤维颗粒模拟无涂布磨砂纸，透明度保持在背景层。 */
.form-panel::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  opacity: var(--panel-grain-opacity);
  background-image:
    radial-gradient(circle, var(--panel-fiber-dark) 0 0.45px, transparent 0.75px),
    radial-gradient(circle, var(--panel-fiber-light) 0 0.55px, transparent 0.85px);
  background-position: 0 0, 6px 9px;
  background-size: 13px 17px, 19px 23px;
  box-shadow:
    inset 0 1px 0 var(--panel-emboss-light),
    inset -1px 0 0 var(--panel-emboss-shadow);
}

/* 主题与语言以工具栏形式存在，不再包裹玻璃胶囊。 */
.top-actions {
  position: absolute;
  top: 28px;
  right: 32px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
}
.top-action-item {
  display: flex;
  align-items: center;
}
.top-action-item + .top-action-item {
  padding-left: 12px;
  border-left: 1px solid var(--login-rule);
}
.top-action-item :deep(.theme-btn),
.top-action-item :deep(button) {
  color: var(--login-muted) !important;
  border-radius: 6px !important;
  transition: color 0.16s ease, background-color 0.16s ease !important;
}
.top-action-item :deep(.theme-btn:hover),
.top-action-item :deep(button:hover) {
  background-color: rgba(72, 60, 43, 0.07) !important;
  color: var(--login-ink) !important;
}
.top-action-item :deep(.theme-btn:focus-visible),
.top-action-item :deep(button:focus-visible) {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.top-action-item :deep(.el-select__wrapper) {
  min-height: 36px;
  padding: 0 4px;
  color: var(--login-muted);
  background: transparent !important;
  border-radius: 4px !important;
  box-shadow: none !important;
  transition: color 0.16s ease, box-shadow 0.16s ease !important;
}
.top-action-item :deep(.el-select__wrapper:hover) {
  color: var(--login-ink);
}
.top-action-item :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 2px var(--login-focus) !important;
}
.top-action-item :deep(.el-select__selected-item),
.top-action-item :deep(.el-select__placeholder) {
  color: currentColor;
  font-size: 13px;
}

/* 表单与版权沿同一左边线排布。 */
.login-wrapper {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 30px;
}

.login-surface {
  position: relative;
  isolation: isolate;
  width: 100%;
  padding: 9px 9px 12px 12px;
  box-sizing: border-box;
  background-color: var(--ledger-cover-base);
  background-image: url("@/assets/login-ledger-cover-v1.webp");
  background-position: center;
  background-size: cover;
  background-blend-mode: luminosity;
  border: 1px solid var(--ledger-cover-border);
  border-radius: 10px 7px 8px 11px;
  box-shadow:
    inset 1px 0 0 rgba(255, 255, 255, 0.08),
    inset -1px 0 0 rgba(0, 0, 0, 0.10),
    0 8px 18px var(--ledger-shadow);
  animation: ledgerSettle 0.52s cubic-bezier(0.22, 0.72, 0.25, 1) both;
}

.login-surface::before {
  content: "";
  position: absolute;
  top: 10px;
  bottom: 11px;
  left: 4px;
  z-index: 0;
  width: 5px;
  border-right: 1px solid rgba(0, 0, 0, 0.19);
  border-left: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 8px;
  box-shadow: 2px 0 4px rgba(0, 0, 0, 0.10);
}

.login-surface::after {
  content: "";
  position: absolute;
  right: 7px;
  bottom: 4px;
  left: 12px;
  z-index: 1;
  height: 5px;
  background: repeating-linear-gradient(
    to bottom,
    var(--ledger-page-edge) 0,
    var(--ledger-page-edge) 1px,
    var(--ledger-under-sheet) 1px,
    var(--ledger-under-sheet) 2px
  );
  border-radius: 0 0 5px 2px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}

.ledger-page {
  position: relative;
  z-index: 2;
  padding: 36px 28px 28px 42px;
  overflow: hidden;
  background-color: var(--ledger-sheet);
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 35px,
    var(--ledger-ruling) 36px,
    transparent 37px
  );
  border: 1px solid var(--login-rule);
  border-radius: 2px 8px 8px 2px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.22),
    -1px 0 3px rgba(0, 0, 0, 0.09);
  perspective: 900px;
}

.ledger-page::before {
  content: "";
  position: absolute;
  top: 22px;
  bottom: 22px;
  left: 24px;
  width: 1px;
  background: var(--ledger-margin-line);
}

.ledger-page-corner {
  position: absolute;
  right: 3px;
  bottom: 3px;
  z-index: 6;
  width: 58px;
  height: 58px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 4px 0 7px 0;
  cursor: pointer;
}

.ledger-page-corner::before {
  content: "";
  position: absolute;
  right: 10px;
  bottom: 10px;
  width: 18px;
  height: 18px;
  border-right: 1px solid var(--ledger-margin-line);
  border-bottom: 1px solid var(--ledger-margin-line);
  opacity: 0.72;
}

.ledger-page-corner:focus-visible {
  outline: none;
}

.ledger-page-corner:focus-visible::before {
  width: 16px;
  height: 16px;
  border-color: var(--color-primary);
  opacity: 1;
}

.ledger-turn-leaf {
  position: absolute;
  right: 0;
  bottom: 0;
  z-index: 5;
  width: 116px;
  height: 116px;
  pointer-events: none;
  clip-path: polygon(100% 0, 100% 100%, 0 100%);
  background: linear-gradient(
    135deg,
    transparent 0 48%,
    rgba(64, 52, 37, 0.16) 49%,
    var(--ledger-sheet) 52% 100%
  );
  opacity: 0.68;
  transform: translate(0, 0) rotateY(0);
  transform-origin: 100% 100%;
  filter: drop-shadow(-2px 3px 3px rgba(64, 52, 37, 0.10));
  transition: transform 0.24s ease, opacity 0.24s ease;
}

.ledger-page-corner:hover + .ledger-turn-leaf,
.ledger-page-corner:focus-visible + .ledger-turn-leaf {
  opacity: 0.94;
  transform: translate(-7px, -7px) rotateY(-16deg) scale(1.04);
}

.login-surface.is-page-turning .ledger-turn-leaf {
  animation: ledgerCornerTurn 0.96s cubic-bezier(0.32, 0.02, 0.18, 1) both;
}

.login-surface.is-page-turning .ledger-page {
  animation: ledgerPageReaction 0.96s ease-out both;
}

@keyframes ledgerSettle {
  from { opacity: 0; transform: translateY(12px) rotateX(-1.4deg); }
  to { opacity: 1; transform: translateY(0) rotateX(0); }
}

@keyframes ledgerCornerTurn {
  0% { opacity: 0.76; transform: translate(0, 0) rotateY(0) scale(1); }
  34% { opacity: 1; transform: translate(-20px, -18px) rotateY(-52deg) scale(1.48); }
  68% { opacity: 0.92; transform: translate(-34px, -22px) rotateY(-128deg) scale(1.72); }
  100% { opacity: 0; transform: translate(-46px, -10px) rotateY(-176deg) scale(1.42); }
}

@keyframes ledgerPageReaction {
  0%, 100% { transform: translateX(0) rotateY(0); }
  42% { transform: translateX(-2px) rotateY(-0.7deg); }
}

.card-header {
  margin-bottom: 34px;
}
.card-title {
  margin: 0 0 12px;
  color: var(--login-ink);
  font-family: "Songti SC", "STSong", "Noto Serif CJK SC", serif;
  font-size: clamp(34px, 3vw, 40px);
  font-weight: 600;
  letter-spacing: 0.03em;
  line-height: 1.18;
  text-wrap: balance;
}
.card-subtitle {
  font-size: 14px;
  color: var(--login-muted);
  margin: 0;
  line-height: 1.65;
  text-wrap: pretty;
}

.login-form {
  margin: 0;
}
.login-form :deep(.el-form-item) {
  margin-bottom: 22px;
}
.login-form :deep(.el-form-item__label) {
  height: auto;
  margin-bottom: 9px;
  padding: 0;
  color: var(--login-ink);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  line-height: 1.4;
}
.login-form :deep(.el-form-item__content) {
  display: block;
  line-height: normal;
}
.login-form :deep(.el-input__wrapper) {
  box-sizing: border-box;
  height: 50px;
  overflow: hidden;
  padding: 0 14px;
  background: var(--login-paper-soft) !important;
  border: 1px solid var(--login-rule);
  border-radius: 6px !important;
  box-shadow: none !important;
  transition: background-color 0.16s ease, border-color 0.16s ease !important;
}
.login-form :deep(.el-input__wrapper:hover) {
  background: var(--login-paper-hover) !important;
  border-color: var(--login-rule-strong);
  box-shadow: none !important;
}
.login-form :deep(.el-input__wrapper.is-focus) {
  padding: 0 13px;
  background: var(--login-paper-hover) !important;
  border: 2px solid var(--color-primary);
  box-shadow: none !important;
}
.login-form :deep(.el-input__inner) {
  height: 100%;
  color: var(--login-ink);
  font-size: 15px;
}
.login-form :deep(.el-input__inner::placeholder) {
  color: var(--login-faint);
  font-size: 14px;
}
.login-form :deep(.el-input__inner:-webkit-autofill),
.login-form :deep(.el-input__inner:-webkit-autofill:hover),
.login-form :deep(.el-input__inner:-webkit-autofill:focus) {
  caret-color: var(--login-ink);
  -webkit-text-fill-color: var(--login-ink) !important;
  -webkit-box-shadow: 0 0 0 1000px var(--login-autofill) inset !important;
}
.login-form :deep(.el-input__suffix-inner) {
  font-size: 16px;
  color: var(--login-muted);
  cursor: pointer;
}
.login-form :deep(.el-form-item__error) {
  padding-top: 6px;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.remember-checkbox :deep(.el-checkbox) {
  display: inline-flex;
  align-items: center;
  height: auto;
  margin-right: 0;
}
.remember-checkbox :deep(.el-checkbox__input) {
  display: inline-flex;
  align-items: center;
  line-height: 1;
  transform: translateY(1px);
}
.remember-checkbox :deep(.el-checkbox__inner) {
  width: 14px;
  height: 14px;
  background: transparent;
  border-color: var(--login-rule-strong);
  border-radius: 2px;
}
.remember-checkbox :deep(.el-checkbox__label) {
  font-size: 13px;
  color: var(--login-muted);
  line-height: 1;
  display: inline-flex;
  align-items: center;
  padding-left: 8px;
}
.remember-checkbox :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: var(--color-primary);
  border-color: var(--color-primary);
}
.forgot-link {
  appearance: none;
  padding: 6px 0;
  background: transparent;
  border: 0;
  font-size: 13px;
  color: var(--color-primary);
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s ease;
}
.forgot-link:hover {
  color: var(--color-primary-hover);
  text-decoration: underline;
}
.forgot-link:focus-visible,
.register-link:focus-visible,
.login-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}

.login-btn {
  position: relative;
  width: 100%;
  height: 50px;
  font-size: 15px;
  font-weight: 600;
  color: #ffffff;
  background: var(--color-primary);
  border: 1px solid rgba(20, 54, 42, 0.22);
  border-radius: 6px;
  cursor: pointer;
  overflow: hidden;
  letter-spacing: 0.08em;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
  transition: background-color 0.16s ease, border-color 0.16s ease,
              transform 0.12s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.login-btn:hover:not(:disabled) {
  background: var(--color-primary-hover);
  border-color: rgba(20, 54, 42, 0.34);
}
.login-btn:active:not(:disabled) {
  transform: translateY(1px);
}
.login-btn.is-loading {
  cursor: not-allowed;
  opacity: 0.85;
}
.login-btn-text {
  position: relative;
  z-index: 1;
}
.login-btn-loader {
  width: 16px;
  height: 16px;
  margin-left: 10px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: btnSpin 0.7s linear infinite;
}
@keyframes btnSpin {
  to { transform: rotate(360deg); }
}

.register-row {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--login-rule);
  text-align: left;
  font-size: 13px;
  color: var(--login-muted);
}
.register-prefix {
  margin-right: 4px;
}
.register-link {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 600;
  transition: color 0.15s ease;
}
.register-link:hover {
  color: var(--color-primary-hover);
  text-decoration: underline;
}

/* 底部版权 */
.copyright-text {
  width: 100%;
  margin: 0;
  color: var(--login-faint);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-align: left;
}

/* 中等宽度为表单释放空间，同时保留完整的双栏品牌体验。 */
@media (min-width: 901px) and (max-width: 1099px) {
  .brand-panel,
  .form-panel {
    width: 50%;
  }

  .brand-signature {
    top: 32px;
    left: 32px;
  }

  .top-actions {
    right: 24px;
  }
}

/* ============================================================
   响应式：窄屏 ≤900px 隐藏左侧插画，右侧全屏居中
   ============================================================ */
@media (max-width: 900px) {
  .login-page {
    background: #f2ede4;
  }
  .brand-panel {
    display: none;
  }
  .form-panel {
    width: 100%;
    --panel-ruling-opacity: 0.64;
    --panel-grain-opacity: 0.38;
    background: var(--login-paper);
    border-left: 0;
    padding: 84px 24px 32px;
  }
  .top-actions {
    top: 20px;
    right: 20px;
  }
}

@media (max-width: 520px) {
  .login-surface {
    padding: 8px 8px 11px 11px;
  }

  .login-surface::before {
    left: 3px;
    width: 4px;
  }

  .login-surface::after {
    left: 11px;
  }

  .ledger-page {
    padding: 32px 20px 26px 32px;
  }

  .ledger-page::before {
    left: 18px;
  }
}

/* 尊重用户偏好：减少动画 */
@media (prefers-reduced-motion: reduce) {
  .login-surface {
    animation: none !important;
  }
  .login-btn-loader {
    animation: none !important;
  }

  .ledger-turn-leaf {
    transition: none !important;
  }

  .login-surface.is-page-turning .ledger-turn-leaf {
    animation: none !important;
    opacity: 0.9;
  }
}
</style>

<!-- 暗色与护眼模式仅替换纸张、墨色和边线 Token，保持同一设计语言。 -->
<style>
[data-theme="dark"] .login-page {
  background: #181e1a !important;
}
[data-theme="dark"] .form-panel {
  --color-primary: #789078;
  --color-primary-hover: #8da287;
  --color-primary-light: rgba(120, 144, 120, 0.15);
  --login-paper: #181e1a;
  --login-paper-soft: rgba(238, 232, 216, 0.045);
  --login-paper-hover: rgba(238, 232, 216, 0.068);
  --login-autofill: #202722;
  --login-ink: #eee8dc;
  --login-muted: #b8b0a1;
  --login-faint: #918879;
  --login-rule: rgba(226, 217, 199, 0.14);
  --login-rule-strong: rgba(226, 217, 199, 0.26);
  --login-focus: rgba(120, 144, 120, 0.18);
  --ledger-sheet: #1d2420;
  --ledger-under-sheet: #111612;
  --ledger-ruling: rgba(214, 204, 183, 0.052);
  --ledger-margin-line: rgba(171, 112, 72, 0.38);
  --ledger-shadow: rgba(0, 0, 0, 0.30);
  --ledger-cover-base: #2b382f;
  --ledger-cover-border: rgba(8, 17, 11, 0.70);
  --ledger-page-edge: #766d5e;
  --panel-ledger-rule: rgba(204, 194, 171, 0.045);
  --panel-margin-rule: rgba(171, 112, 72, 0.072);
  --panel-fiber-dark: rgba(0, 0, 0, 0.18);
  --panel-fiber-light: rgba(218, 207, 183, 0.10);
  --panel-emboss-light: rgba(226, 217, 199, 0.055);
  --panel-emboss-shadow: rgba(0, 0, 0, 0.16);
  --panel-ruling-opacity: 0.68;
  --panel-grain-opacity: 0.32;
  background: var(--login-paper) !important;
  border-left-color: var(--login-rule);
}
[data-theme="dark"] .login-surface {
  background-blend-mode: soft-light;
  box-shadow:
    inset 1px 0 0 rgba(226, 217, 199, 0.055),
    inset -1px 0 0 rgba(0, 0, 0, 0.24),
    0 10px 24px rgba(0, 0, 0, 0.24);
}
[data-theme="dark"] .login-btn {
  color: #f5f0e6;
  border-color: rgba(18, 29, 22, 0.54);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.10);
}
[data-theme="dark"] .brand-signature {
  color: rgba(240, 235, 221, 0.70);
}

[data-theme="eye-care"] .login-page {
  background: #e9dfcf !important;
}
[data-theme="eye-care"] .form-panel {
  --login-paper: #e7dcc9;
  --login-paper-soft: rgba(248, 241, 228, 0.78);
  --login-paper-hover: #f8f1e5;
  --login-autofill: #f5eddf;
  --login-ink: #3b342b;
  --login-muted: #766b5c;
  --login-faint: #948776;
  --login-rule: rgba(78, 61, 40, 0.20);
  --login-rule-strong: rgba(78, 61, 40, 0.34);
  --login-focus: rgba(91, 122, 94, 0.16);
  --ledger-sheet: #f7efdf;
  --ledger-under-sheet: #d9cdbb;
  --ledger-ruling: rgba(91, 74, 50, 0.06);
  --ledger-margin-line: rgba(158, 82, 49, 0.38);
  --ledger-shadow: rgba(70, 55, 36, 0.10);
  --ledger-cover-base: #46513d;
  --ledger-cover-border: rgba(52, 65, 47, 0.46);
  --ledger-page-edge: #cdbb9f;
  --panel-ledger-rule: rgba(88, 71, 48, 0.055);
  --panel-margin-rule: rgba(145, 82, 48, 0.09);
  --panel-fiber-dark: rgba(78, 61, 40, 0.12);
  --panel-fiber-light: rgba(255, 248, 232, 0.52);
  --panel-emboss-light: rgba(255, 248, 232, 0.34);
  --panel-emboss-shadow: rgba(78, 61, 40, 0.09);
  --panel-ruling-opacity: 0.82;
  --panel-grain-opacity: 0.50;
  background: var(--login-paper) !important;
  border-left-color: var(--login-rule);
}
[data-theme="eye-care"] .brand-signature {
  color: rgba(241, 230, 206, 0.84);
}

@media (max-width: 900px) {
  [data-theme="dark"] .login-page {
    background: #181e1a !important;
  }
  [data-theme="eye-care"] .login-page {
    background: #e9dfcf !important;
  }
}
</style>
