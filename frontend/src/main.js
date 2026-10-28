import { createApp } from 'vue'
import {
  ElAlert, ElAside, ElButton, ElCard, ElCheckbox, ElCol, ElConfigProvider,
  ElContainer, ElDatePicker, ElDialog, ElDrawer, ElDropdown, ElDropdownItem,
  ElDropdownMenu, ElForm, ElFormItem, ElHeader, ElIcon, ElInput, ElInputNumber,
  ElMain, ElMenu, ElMenuItem, ElOption, ElPagination, ElProgress, ElRadio,
  ElRadioButton, ElRadioGroup, ElResult, ElRow, ElSelect, ElSkeleton, ElTabPane,
  ElTable, ElTableColumn, ElTabs, ElTag, ElLoading
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './assets/css/themes.css'

const app = createApp(App)

// 图标由使用它的组件局部导入；仅安装当前界面需要的组件与 loading 指令。
for (const component of [
  ElAlert, ElAside, ElButton, ElCard, ElCheckbox, ElCol, ElConfigProvider,
  ElContainer, ElDatePicker, ElDialog, ElDrawer, ElDropdown, ElDropdownItem,
  ElDropdownMenu, ElForm, ElFormItem, ElHeader, ElIcon, ElInput, ElInputNumber,
  ElMain, ElMenu, ElMenuItem, ElOption, ElPagination, ElProgress, ElRadio,
  ElRadioButton, ElRadioGroup, ElResult, ElRow, ElSelect, ElSkeleton, ElTabPane,
  ElTable, ElTableColumn, ElTabs, ElTag, ElLoading
]) app.use(component)
app.use(router)
app.use(i18n)

// 应用启动时检查并应用已保存的主题
const savedTheme = localStorage.getItem('theme') || 'light'
document.documentElement.setAttribute('data-theme', savedTheme)

app.mount('#app')
