<template>
  <div class="management-page category-page">
    <PageHeader :title="$t('nav.category')" :description="$t('category.subtitle')">
      <template #actions>
        <el-button type="primary" :disabled="loading" @click="openDialog(null)">{{ $t('category.addCategory') }}</el-button>
      </template>
    </PageHeader>
    <!-- ============================================================
         分类管理页 - 大卡片包裹
         整体风格和账单页、预算页100%统一
         ============================================================ -->
    <el-card shadow="never" class="page-card">
      <!-- ============================================================
           顶部操作栏：Tab标签(左) + 添加按钮(右)，Flex两端对齐
           ============================================================ -->
      <div class="page-header">
        <el-tabs v-model="activeTab" class="category-tabs">
          <el-tab-pane :label="$t('category.expenseCategory')" name="1" />
          <el-tab-pane :label="$t('category.incomeCategory')" name="2" />
        </el-tabs>
      </div>

      <!-- ============================================================
           表格：斑马纹、统一行高、hover浅蓝、列宽规范
           ============================================================ -->
      <AppState
        v-if="loading"
        type="loading"
        compact
        :title="$t('common.loading')"
        :rows="6"
      />
      <AppState
        v-else-if="loadError"
        type="error"
        compact
        :title="$t('common.loadFailed')"
        :description="$t('common.loadFailedDescription')"
        :action-label="$t('common.retry')"
        @retry="loadCategories"
      />
      <el-table v-else :data="activeCategories" stripe class="category-table" :empty-text="$t('common.noData')">
        <!-- 分类名称 -->
        <el-table-column prop="name" :label="$t('category.categoryName')" min-width="140" />
        <!-- 图标列（Element Plus 内置图标渲染） -->
        <el-table-column :label="$t('category.icon')" width="120" align="center">
          <template #default="{ row }">
            <el-icon :size="16">
              <component :is="getIconComponent(row.icon)" />
            </el-icon>
          </template>
        </el-table-column>
        <!-- 排序 -->
        <el-table-column prop="sortOrder" :label="$t('category.sortOrder')" width="100" align="center" />
        <!-- 上移/下移 -->
        <el-table-column label="" width="140" align="center">
          <template #default="{ row }">
            <el-button text size="small" :disabled="!canMove(row, -1)" @click="handleMove(row, -1)">
              {{ $t('category.moveUp') }}
            </el-button>
            <el-button text size="small" :disabled="!canMove(row, 1)" @click="handleMove(row, 1)">
              {{ $t('category.moveDown') }}
            </el-button>
          </template>
        </el-table-column>
        <!-- 操作列 -->
        <el-table-column :label="$t('common.operation')" width="150" fixed="right" align="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" :disabled="row.userId === 0" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-button type="danger" text size="small" :disabled="row.userId === 0" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ============================================================
         添加/编辑弹窗
         ============================================================ -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? $t('category.editCategory') : $t('category.addCategory')"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item :label="$t('category.categoryName')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('category.inputName')" />
        </el-form-item>
        <el-form-item :label="$t('category.icon')" prop="icon">
          <el-input v-model="form.icon" :placeholder="$t('category.inputIcon')" />
        </el-form-item>
        <el-form-item :label="$t('bill.type')" prop="type">
          <el-radio-group v-model="form.type" :disabled="isEdit">
            <el-radio :value="1">{{ $t('bill.expense') }}</el-radio>
            <el-radio :value="2">{{ $t('bill.income') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('category.sortOrder')" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :step="1" style="width: 100%" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * ============================================================
 * 分类管理页 - 优化版
 *
 * 优化内容（毕设文档可用）：
 * 1. 统一大卡片包裹，和账单页/预算页视觉对齐
 * 2. 顶部Flex两端对齐：Tab(左) + 按钮(右)
 * 3. 图标列使用Element Plus内置图标组件渲染
 * 4. 上移/下移调整排序（前端交换+后端更新）
 * 5. 删除二次确认弹窗，防止误删
 * 6. 全量多语言 i18n 适配
 * 7. 斑马纹表格、统一行高、hover浅蓝高亮
 * ============================================================
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Promotion, Food, Van, ShoppingCart, Goods,
  Tickets, House, WarnTriangleFilled, PhoneFilled,
  School, TrendCharts, Money, Coin, Wallet,
  HelpFilled
} from '@element-plus/icons-vue'
import { getCategoryList, addCategory, updateCategory, deleteCategory } from '../api/category'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'

const { t } = useI18n()

const activeTab = ref('1')
const categories = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const loading = ref(false)
const loadError = ref('')

const form = reactive({ id: null, name: '', type: 1, icon: '', sortOrder: 0 })

const rules = {
  name: [{ required: true, message: t('validation.categoryNameRequired'), trigger: 'blur' }],
  type: [{ required: true, message: t('validation.typeRequired'), trigger: 'change' }],
  icon: [{ required: true, message: t('validation.iconRequired'), trigger: 'blur' }]
}

/* 根据当前Tab筛选数据 */
const activeCategories = computed(() =>
  categories.value.filter(c => c.type === parseInt(activeTab.value))
)
const editableCategories = computed(() =>
  activeCategories.value.filter(c => c.userId !== 0)
)

/* Element Plus 图标映射 */
const iconMap = {
  food: Food, transport: Van, shopping: ShoppingCart,
  goods: Goods, entertainment: Tickets, housing: House,
  medical: WarnTriangleFilled, phone: PhoneFilled,
  education: School, salary: Money, bonus: Coin,
  invest: TrendCharts, parttime: Wallet, other: Promotion,
  default: HelpFilled
}

function getIconComponent(iconName) {
  return iconMap[iconName?.toLowerCase()] || iconMap.default
}

async function loadCategories() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getCategoryList()
    categories.value = res.data || []
  } catch (error) {
    loadError.value = t('common.loadFailedDescription')
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, { id: row.id, name: row.name, type: row.type, icon: row.icon, sortOrder: row.sortOrder })
  } else {
    Object.assign(form, { id: null, name: '', type: parseInt(activeTab.value), icon: '', sortOrder: 0 })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateCategory({
        id: form.id,
        name: form.name,
        type: form.type,
        icon: form.icon,
        sortOrder: form.sortOrder
      })
    } else {
      await addCategory({
        name: form.name,
        type: form.type,
        icon: form.icon,
        sortOrder: form.sortOrder
      })
    }
    ElMessage.success(isEdit.value ? t('message.updateSuccess') : t('message.addSuccess'))
    dialogVisible.value = false
    loadCategories()
  } finally {
    submitting.value = false
  }
}

/** 上移/下移（前端交换sortOrder + 后端更新） */
function canMove(row, direction) {
  if (row.userId === 0) return false
  const index = editableCategories.value.findIndex(item => item.id === row.id)
  return index >= 0 && index + direction >= 0 && index + direction < editableCategories.value.length
}

async function handleMove(row, direction) {
  const list = editableCategories.value
  const index = list.findIndex(item => item.id === row.id)
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= list.length) return

  const target = list[targetIndex]

  // 临时保存原始sortOrder
  const rowOrder = row.sortOrder
  const targetOrder = target.sortOrder

  // 前端交换
  row.sortOrder = targetOrder
  target.sortOrder = rowOrder

  // 调用后端更新接口（按序更新两条）
  try {
    await updateCategory({ id: row.id, name: row.name, type: row.type, icon: row.icon, sortOrder: targetOrder })
    await updateCategory({ id: target.id, name: target.name, type: target.type, icon: target.icon, sortOrder: rowOrder })
    ElMessage.success(t('message.updateSuccess'))
    await loadCategories()
  } catch (e) {
    ElMessage.error(t('message.requestFailed'))
    loadCategories()
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(t('category.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteCategory(row.id)
  ElMessage.success(t('category.deleteSuccess'))
  loadCategories()
}

onMounted(() => loadCategories())
</script>

<style scoped>
/* ============================================================
   分类管理页样式 - 和账单页/预算页100%统一
   圆角12px、柔和阴影、Flex两端对齐
   ============================================================ */

/* 大卡片 */
.page-card {
  overflow: hidden;
  padding: 0;
  box-shadow: var(--shadow-soft) !important;
}
.page-card :deep(.el-card__body) { padding: 10px 16px 14px; }

/* 顶部操作栏 Flex两端对齐 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

/* Tab标签样式 */
.category-tabs {
  flex: 1;
}
.category-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.category-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 600;
  height: 44px;
  line-height: 44px;
  color: var(--text-regular);
}
.category-tabs :deep(.el-tabs__item.is-active) {
  color: var(--color-primary);
  font-weight: 700;
}
.category-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.category-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: var(--color-primary);
}

/* 添加按钮 */
.page-header .el-button {
  height: 36px;
  border-radius: 6px;
  flex-shrink: 0;
  margin-left: 16px;
}

/* 表格覆盖 */
.category-table {
  width: 100%;
}
.category-table :deep(.el-table__header th) {
  background-color: var(--bg-table-header) !important;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-regular) !important;
  border-bottom: 1px solid var(--border-color) !important;
}
.category-table :deep(.el-table__row) {
  height: 48px;
  transition: background-color 0.2s ease;
}
.category-table :deep(.el-table__row:hover) td {
  background-color: var(--bg-table-hover) !important;
}

@media (max-width: 720px) {
  .page-card :deep(.el-card__body) {
    padding: 8px 0 12px;
  }

  .page-header {
    padding: 0 12px;
  }

  .category-tabs :deep(.el-tabs__item) {
    font-size: 13px;
  }
}
</style>
