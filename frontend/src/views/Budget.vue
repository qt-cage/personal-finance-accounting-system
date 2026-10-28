<template>
  <div class="management-page budget-page">
    <PageHeader
      :title="$t('nav.budget')"
      :description="$t('budget.subtitle')"
      :stamp="$t('budget.periodStamp', { period: loadedMonth || month })"
    >
      <template #actions>
        <el-button type="primary" :disabled="loading" @click="openDialog(null)">{{ $t('budget.setBudget') }}</el-button>
      </template>
    </PageHeader>
    <!-- ============================================================
         筛选区卡片：Flex两端对齐，圆角12px，统一阴影
         ============================================================ -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <!-- 左侧：月份选择器 + 查询按钮 -->
        <div class="filter-controls">
          <el-date-picker
            v-model="month"
            type="month"
            :placeholder="$t('budget.selectMonth')"
            value-format="YYYY-MM"
            class="month-picker"
            :clearable="false"
            :disabled="loading"
          />
          <el-button type="primary" :loading="loading" @click="loadBudgets">{{ $t('common.query') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- ============================================================
         总览数据卡片行：当月总预算 / 已支出 / 剩余
         数据由前端自动汇总，不改后端接口
         ============================================================ -->
    <AppState
      v-if="loading"
      type="loading"
      :title="$t('common.loading')"
      :rows="7"
    />
    <AppState
      v-else-if="loadError"
      type="error"
      :title="$t('common.loadFailed')"
      :description="$t('common.loadFailedDescription')"
      :action-label="$t('common.retry')"
      @retry="loadBudgets"
    />
    <template v-else>
      <MetricStrip :items="metricItems" />

    <!-- ============================================================
         预算表格卡片：斑马纹、列宽规范、进度条三级颜色
         ============================================================ -->
      <el-card shadow="never" class="table-card">
        <el-table :data="budgetList" stripe class="budget-table" :empty-text="$t('common.noData')">
        <el-table-column :label="$t('budget.category')" min-width="120">
          <template #default="{ row }">
            <el-tag class="category-tag">{{ row.categoryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" :label="$t('budget.budgetAmount')" width="130" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="spentAmount" :label="$t('budget.spentAmount')" width="130" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.spentAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$t('budget.remaining')" width="130" align="right">
          <template #default="{ row }">
            <span :style="{ color: subtractAmounts(row.amount, row.spentAmount) >= 0 ? 'var(--color-success)' : 'var(--color-danger)', fontWeight: 600 }">
              ¥{{ formatAmount(subtractAmounts(row.amount, row.spentAmount)) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('budget.progress')" width="200" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="calcPct(row)"
              :color="calcColor(row)"
              :stroke-width="8"
              style="max-width: 160px; display: inline-block"
            />
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.operation')" width="150" fixed="right" align="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-button type="danger" text size="small" @click="handleDelete(row.id)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- ============================================================
         设置/编辑预算弹窗
         ============================================================ -->
    <el-dialog
      v-model="dialogVisible"
      :title="`${isEdit ? $t('budget.editBudget') : $t('budget.setBudget')} · ${form.month}`"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item :label="$t('budget.category')" prop="categoryId">
          <el-select v-model="form.categoryId" :placeholder="$t('budget.selectCategory')" clearable :loading="categoryLoading" style="width: 100%">
            <el-option :label="$t('budget.totalCategory')" :value="0" />
            <el-option v-for="c in categories" :key="c.id" :label="categoryLabel(c, categories)" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('budget.budgetAmount')" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :precision="2"
            :step="100"
            style="width: 100%"
            controls-position="right"
          />
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
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { getBudgetList, saveBudget, deleteBudget } from '../api/budget'
import { getCategoryList } from '../api/category'
import { getStatistics } from '../api/bill'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatAmount } from '../utils/format'
import { formatLocalMonth, getMonthDateRange } from '../utils/businessDate'
import { subtractAmounts } from '../utils/money'
import { buildBudgetSummary } from '../utils/budgetSummary'
import PageHeader from '../components/PageHeader.vue'
import MetricStrip from '../components/MetricStrip.vue'
import AppState from '../components/AppState.vue'
import { categoryLabel } from '../utils/categoryHierarchy'

const { t } = useI18n()
const route = useRoute()

function validMonth(value) {
  return typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

const month = ref(validMonth(route.query.month) ? route.query.month : formatLocalMonth())
const loadedMonth = ref('')
let budgetRequest = 0
const budgetList = ref([])
const categories = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const monthTotalSpent = ref(0)
const loading = ref(false)
const loadError = ref('')
const categoryLoading = ref(false)

const form = reactive({ categoryId: 0, amount: null, month: '' })
const rules = {
  amount: [{ required: true, message: t('validation.budgetAmountRequired'), trigger: 'blur' }]
}

const summary = computed(() => buildBudgetSummary(budgetList.value, monthTotalSpent.value))

const metricItems = computed(() => [
  {
    key: 'budget',
    label: t('budget.totalBudget'),
    value: summary.value.hasTotalBudget ? `¥${formatAmount(summary.value.totalBudget)}` : '—',
    tone: summary.value.hasTotalBudget ? 'primary' : 'neutral',
    caption: summary.value.hasTotalBudget ? t('budget.totalBudgetCaption')
      : t(summary.value.hasCategoryBudgets ? 'budget.totalNotSet' : 'budget.notSet')
  },
  {
    key: 'spent',
    label: t('budget.totalSpent'),
    value: `¥${formatAmount(summary.value.totalSpent)}`,
    tone: 'warning',
    caption: t('budget.totalSpentCaption')
  },
  {
    key: 'remaining',
    label: t('budget.totalRemaining'),
    value: summary.value.hasTotalBudget ? `¥${formatAmount(summary.value.remaining)}` : '—',
    tone: !summary.value.hasTotalBudget ? 'neutral' : summary.value.remaining >= 0 ? 'success' : 'danger',
    caption: !summary.value.hasTotalBudget ? t('budget.remainingUnavailable')
      : summary.value.remaining >= 0 ? t('budget.remainingHealthy') : t('budget.remainingExceeded')
  }
])

/** 进度百分比 */
function calcPct(row) {
  if (!row.amount || parseFloat(row.amount) === 0) return 0
  return Math.min(Math.round((parseFloat(row.spentAmount || 0) / parseFloat(row.amount)) * 100), 100)
}

/** 进度条三级颜色：与 Dashboard 预算预警色保持一致（正常墨绿 / 80% 琥珀预警 / 100% 赤陶超支） */
function calcColor(row) {
  const pct = calcPct(row)
  if (pct >= 100) return 'var(--color-danger)'
  if (pct >= 80) return 'var(--color-warning)'
  return 'var(--color-primary)'
}

async function loadBudgets() {
  const current = ++budgetRequest
  const requestedMonth = month.value
  loading.value = true
  loadError.value = ''
  try {
    const { startDate, endDate } = getMonthDateRange(requestedMonth)
    const [budgetRes, statsRes] = await Promise.all([
      getBudgetList({ month: requestedMonth }),
      getStatistics({ startDate, endDate })
    ])
    if (current !== budgetRequest) return
    budgetList.value = budgetRes.data || []
    monthTotalSpent.value = statsRes.data?.expense || 0
    loadedMonth.value = requestedMonth
  } catch (error) {
    if (current === budgetRequest) loadError.value = t('common.loadFailedDescription')
  } finally {
    if (current === budgetRequest) loading.value = false
  }
}

async function loadCategories() {
  categoryLoading.value = true
  try {
    const res = await getCategoryList({ type: 1 })
    categories.value = res.data || []
  } catch (error) {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    Object.assign(form, { categoryId: row.categoryId ?? 0, amount: row.amount, month: row.month || loadedMonth.value })
  } else {
    Object.assign(form, { categoryId: 0, amount: null, month: month.value })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (submitting.value) return
  submitting.value = true
  try {
    if (!await formRef.value.validate().catch(() => false)) return
    await saveBudget({
      categoryId: form.categoryId === 0 ? null : form.categoryId,
      amount: form.amount,
      month: form.month
    })
    ElMessage.success(t('message.setSuccess'))
    dialogVisible.value = false
    loadBudgets()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  await ElMessageBox.confirm(t('budget.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await deleteBudget(id)
  ElMessage.success(t('budget.deleteSuccess'))
  loadBudgets()
}

watch(() => route.query.month, value => {
  if (!validMonth(value) || value === month.value) return
  month.value = value
  loadBudgets()
})

onMounted(() => {
  loadCategories()
  loadBudgets()
})
onBeforeUnmount(() => { budgetRequest++ })
</script>

<style scoped>
/* ============================================================
   预算管理页样式 - 与首页/账单页风格统一
   圆角12px、柔和阴影、Flex两端对齐
   ============================================================ */

/* 筛选卡片 */
.filter-card {
  margin-bottom: 20px;
  box-shadow: var(--shadow-soft) !important;
}
.filter-card :deep(.el-card__body) { padding: 16px; }

/* Flex容器：两端对齐 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

/* 左侧控件 */
.filter-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}
.filter-controls :deep(.el-input__wrapper) {
  height: 36px;
  border-radius: 6px;
}
.month-picker {
  width: 200px;
}
.filter-controls .el-button {
  height: 36px;
  border-radius: 6px;
}


/* 表格卡片 */
.table-card {
  padding: 0;
  overflow: hidden;
  box-shadow: var(--shadow-soft) !important;
}
.table-card :deep(.el-card__body) { padding: 12px 16px 14px; }

/* 分类标签 */
.category-tag {
  border-radius: 4px;
  font-size: 12px;
  border: none;
}

/* 表格覆盖 */

@media (max-width: 720px) {
  .filter-bar,
  .filter-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .month-picker,
  .filter-controls .el-button {
    width: 100%;
  }

  .table-card :deep(.el-card__body) {
    padding: 10px 0 12px;
  }
}
</style>
