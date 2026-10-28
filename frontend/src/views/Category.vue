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
      <p class="category-hierarchy-hint">{{ $t('finance.categoryHint') }}</p>
      <el-table v-if="!loading && !loadError" :data="treeCategories" row-key="id" default-expand-all stripe class="category-table" :empty-text="$t('common.noData')">
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
            <el-button type="danger" text size="small" :disabled="row.userId === 0 || !!row.children?.length" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
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
      width="min(500px, calc(100vw - 32px))"
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
        <el-form-item :label="$t('finance.parentCategory')" prop="parentId">
          <el-select v-model="form.parentId" :placeholder="$t('finance.topLevel')" clearable :disabled="hasChildren" style="width:100%">
            <el-option v-for="parent in availableParents" :key="parent.id" :label="parent.name" :value="parent.id" />
          </el-select>
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
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Promotion, Food, Van, ShoppingCart, Goods,
  Tickets, House, WarnTriangleFilled, PhoneFilled,
  School, TrendCharts, Money, Coin, Wallet,
  HelpFilled
} from '@element-plus/icons-vue'
import { getCategoryList, addCategory, updateCategory, deleteCategory, reorderCategories } from '../api/category'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'
import { categoryTree } from '../utils/categoryHierarchy'

const { t } = useI18n()

const activeTab = ref('1')
const categories = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const moving = ref(false)
const formRef = ref()
const loading = ref(false)
const loadError = ref('')

const form = reactive({ id: null, name: '', type: 1, icon: '', sortOrder: 0, parentId: null })

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
const treeCategories = computed(() => categoryTree(activeCategories.value))
const availableParents = computed(() => categories.value.filter(category => category.type === form.type && category.parentId == null && category.id !== form.id))
const hasChildren = computed(() => !!form.id && categories.value.some(category => category.parentId === form.id))
watch(() => form.type, () => { if (!availableParents.value.some(category => category.id === form.parentId)) form.parentId = null })

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
    Object.assign(form, { id: row.id, name: row.name, type: row.type, icon: row.icon, sortOrder: row.sortOrder, parentId: row.parentId })
  } else {
    Object.assign(form, { id: null, name: '', type: parseInt(activeTab.value), icon: '', sortOrder: 0, parentId: null })
  }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function handleSubmit() {
  if (submitting.value || !await formRef.value.validate().catch(() => false)) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateCategory({
        id: form.id,
        name: form.name,
        type: form.type,
        icon: form.icon,
        sortOrder: form.sortOrder,
        parentId: form.parentId
      })
    } else {
      await addCategory({
        name: form.name,
        type: form.type,
        icon: form.icon,
        sortOrder: form.sortOrder,
        parentId: form.parentId
      })
    }
    ElMessage.success(isEdit.value ? t('message.updateSuccess') : t('message.addSuccess'))
    dialogVisible.value = false
    loadCategories()
  } catch { /* 请求层展示错误，保留分类表单。 */ }
  finally {
    submitting.value = false
  }
}

/** 由服务端在一个事务中完成相邻分类移动。 */
function canMove(row, direction) {
  if (moving.value || row.userId === 0) return false
  const siblings = editableCategories.value.filter(item => (item.parentId || null) === (row.parentId || null))
  const index = siblings.findIndex(item => item.id === row.id)
  return index >= 0 && index + direction >= 0 && index + direction < siblings.length
}

async function handleMove(row, direction) {
  if (moving.value) return
  const list = editableCategories.value.filter(item => (item.parentId || null) === (row.parentId || null))
  const index = list.findIndex(item => item.id === row.id)
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= list.length) return

  const target = list[targetIndex]

  moving.value = true
  try {
    await reorderCategories({ id: row.id, targetId: target.id })
    ElMessage.success(t('message.updateSuccess'))
    await loadCategories()
  } catch (e) {
    ElMessage.error(t('message.requestFailed'))
    await loadCategories()
  } finally {
    moving.value = false
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
.category-hierarchy-hint { color: var(--text-secondary); font-size: 13px; line-height: 1.7; margin: 0 0 16px; }
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
