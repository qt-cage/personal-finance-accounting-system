<template>
  <div class="management-page bill-page">
    <PageHeader :title="$t('nav.bill')" :description="$t('bill.subtitle')">
      <template #actions>
        <el-button type="primary" @click="openDialog(null)">{{ $t('bill.addBill') }}</el-button>
      </template>
    </PageHeader>
    <!-- ============================================================
         筛选区卡片：圆角12px、柔和阴影、Flex两端对齐
         优化：统一控件高度36px、分隔符浅灰、快捷日期选中闭环
         ============================================================ -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <!-- 左：筛选控件，基线对齐 -->
        <div class="filter-controls">
          <el-select v-model="query.type" :placeholder="$t('bill.selectType')" clearable>
            <el-option :label="$t('bill.expense')" :value="1" />
            <el-option :label="$t('bill.income')" :value="2" />
          </el-select>
          <div class="date-range-wrap">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              :range-separator="$t('bill.to')"
              :start-placeholder="$t('bill.startDate')"
              :end-placeholder="$t('bill.endDate')"
              value-format="YYYY-MM-DD"
              @change="onDateManualChange"
            />
            <span class="range-sep">{{ $t('bill.to') }}</span>
          </div>
          <div class="quick-btn-group">
            <el-button
              :type="quickType === 'week' ? 'primary' : 'default'"
              size="small" class="quick-btn"
              @click="setQuickDate('week')"
            >{{ $t('bill.thisWeek') }}</el-button>
            <el-button
              :type="quickType === 'month' ? 'primary' : 'default'"
              size="small" class="quick-btn"
              @click="setQuickDate('month')"
            >{{ $t('bill.thisMonth') }}</el-button>
            <el-button
              :type="quickType === 'quarter' ? 'primary' : 'default'"
              size="small" class="quick-btn"
              @click="setQuickDate('quarter')"
            >{{ $t('bill.thisQuarter') }}</el-button>
          </div>
          <div class="amount-range">
            <el-input v-model="query.minAmount" :placeholder="$t('bill.minAmount')" />
            <span class="range-sep">—</span>
            <el-input v-model="query.maxAmount" :placeholder="$t('bill.maxAmount')" />
          </div>
        </div>
        <!-- 右：按钮组，右对齐 -->
        <div class="filter-actions">
          <el-button type="primary" size="default" :loading="loading" @click="loadBills">{{ $t('common.query') }}</el-button>
          <el-button size="default" class="reset-btn" :disabled="loading" @click="resetQuery">{{ $t('common.reset') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- ============================================================
         表格卡片：斑马纹、行高48px、金额右对齐、空状态提示
         优化：列宽规范、空数据兜底、表格左上角批量栏
         ============================================================ -->
    <el-card shadow="never" class="table-card">
      <!-- 批量操作栏（表格左上角） -->
      <div v-if="selectedIds.length > 0" class="batch-bar-top">
        <el-button size="small" class="batch-delete-btn" @click="handleBatchDelete">
          {{ $t('bill.batchDelete') }}
        </el-button>
        <span class="batch-count">{{ $t('common.total', { total: selectedIds.length }) }}</span>
      </div>

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
        @retry="loadBills"
      />
      <template v-else>
        <el-table
          :data="tableData"
          stripe
          class="bill-table"
          @selection-change="onSelectionChange"
          :empty-text="t('bill.noData')"
        >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="billDate" :label="$t('bill.date')" width="110" />
        <el-table-column :label="$t('bill.type')" width="72" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.type === 1 ? 'danger' : 'success'"
              class="type-tag"
            >{{ row.type === 1 ? $t('bill.expense') : $t('bill.income') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="$t('bill.category')" min-width="100" show-overflow-tooltip />
        <el-table-column :label="$t('bill.amount')" width="160" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.type === 1 ? 'var(--color-danger)' : 'var(--color-success)', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }">
              {{ row.type === 1 ? '-' : '+' }} ¥{{ formatAmount(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('bill.remark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="$t('common.operation')" width="140" fixed="right" align="right">
          <template #default="{ row }">
            <el-button type="primary" text size="small" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <span class="action-divider">|</span>
            <el-button type="danger" text size="small" @click="handleDelete(row.id)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
        </el-table>

        <!-- 分页 -->
        <el-pagination
          class="pagination"
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :layout="layout"
          @current-change="loadBills"
        />
      </template>
    </el-card>

    <!-- ============================================================
         新增/编辑弹窗（业务逻辑零改动）
         ============================================================ -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? $t('bill.editBill') : $t('bill.addBill')"
      width="520px"
      :close-on-click-modal="false"
      @close="categoryLoader.invalidate"
    >
      <el-form :model="billForm" :rules="billRules" ref="billFormRef" label-width="80px">
        <el-form-item :label="$t('bill.type')" prop="type">
          <el-radio-group v-model="billForm.type" @change="onBillTypeChange">
            <el-radio :value="1">{{ $t('bill.expense') }}</el-radio>
            <el-radio :value="2">{{ $t('bill.income') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('bill.category')" prop="categoryId">
          <el-select v-model="billForm.categoryId" :placeholder="$t('bill.selectCategory')" :loading="categoryLoading" :disabled="categoryLoading || categoryError" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-alert v-if="categoryError" :title="$t('common.loadFailed')" type="error" :closable="false" show-icon>
          <el-button text type="primary" @click="loadCategories">{{ $t('common.retry') }}</el-button>
        </el-alert>
        <el-form-item :label="$t('bill.amount')" prop="amount">
          <el-input-number v-model="billForm.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" controls-position="right" />
        </el-form-item>
        <el-form-item :label="$t('bill.date')" prop="billDate">
          <el-date-picker v-model="billForm.billDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('bill.remark')">
          <el-input v-model="billForm.remark" :placeholder="$t('bill.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" :disabled="categoryLoading || categoryError" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * ============================================================
 * 账单管理页 - 精排版
 *
 * 优化内容：
 * 1. 筛选区控件基线对齐，分隔符浅灰弱化
 * 2. 快捷日期选中态联动闭环
 * 3. 金额统一格式：符号+空格+¥+千分位+两位小数
 * 4. 列对齐规范：日期左、类型中、金额右、操作右
 * 5. 批量操作栏置于表格左上角，选中后显示数量
 * 6. 空状态表格自动显示多语言提示
 * 7. 表头F5F7FA、行高48px、hover浅蓝
 * 业务逻辑零改动
 * ============================================================
 */
import { ref, reactive, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { getBillPage, addBill, updateBill, deleteBill, deleteBatchBill } from '../api/bill'
import { getCategoryList } from '../api/category'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatAmount } from '../utils/format'
import { formatLocalDate, getCurrentWeekRange, getMonthDateRange } from '../utils/businessDate'
import { createBillCategoryLoader } from '../utils/billCategoryLoader'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const query = reactive({ type: null, startDate: null, endDate: null, minAmount: null, maxAmount: null, page: 1, size: 10 })
const dateRange = ref(null)
const tableData = ref([])
const total = ref(0)
const quickType = ref('')
const selectedIds = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const billFormRef = ref()
const categories = ref([])
const categoryLoading = ref(false)
const categoryError = ref(false)
const loading = ref(false)
const loadError = ref('')

const billForm = reactive({ id: null, type: 1, categoryId: null, amount: null, billDate: '', remark: '' })
const categoryLoader = createBillCategoryLoader({
  form: billForm,
  fetchCategories: getCategoryList,
  onOptions: options => { categories.value = options },
  onLoading: value => { categoryLoading.value = value },
  onError: error => { categoryError.value = !!error }
})
const billRules = {
  type: [{ required: true, message: t('validation.typeRequired'), trigger: 'change' }],
  categoryId: [{ required: true, message: t('validation.categoryRequired'), trigger: 'change' }],
  amount: [{ required: true, message: t('validation.amountRequired'), trigger: 'blur' }],
  billDate: [{ required: true, message: t('validation.dateRequired'), trigger: 'change' }]
}
const layout = 'total, prev, pager, next'

function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

/** 快捷日期：选中后自动填充日期并高亮
 *  结束日期使用 new Date(year, month, 0) 自动计算当月最后一天
 *  大小月、闰年2月全部自动适配，永不生成非法日期
 */
function setQuickDate(type) {
  quickType.value = type
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth() + 1
  let start, end
  if (type === 'week') {
    const range = getCurrentWeekRange(now)
    start = range.start
    end = range.end
  } else if (type === 'month') {
    start = `${y}-${String(m).padStart(2, '0')}-01`
    // 当月最后一天：new Date(y, m, 0) → m为月份索引+1
    const lastDay = new Date(y, m, 0).getDate()
    end = `${y}-${String(m).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
  } else {
    // 本季度
    const q = Math.ceil(m / 3)
    const startM = (q - 1) * 3 + 1
    const endM = q * 3
    start = `${y}-${String(startM).padStart(2, '0')}-01`
    // 季度最后一天：new Date(y, endM, 0) → endM为月份索引+1
    const lastDay = new Date(y, endM, 0).getDate()
    end = `${y}-${String(endM).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
  }
  dateRange.value = [start, end]
  query.startDate = start
  query.endDate = end
}

/** 手动调整日期 → 取消快捷高亮 */
function onDateManualChange() {
  quickType.value = ''
}

function resetQuery() {
  query.type = null
  dateRange.value = null
  query.startDate = null
  query.endDate = null
  query.minAmount = null
  query.maxAmount = null
  query.page = 1
  quickType.value = ''
  loadBills()
}

async function loadBills() {
  if (dateRange.value) {
    query.startDate = dateRange.value[0]
    query.endDate = dateRange.value[1]
  } else {
    query.startDate = null
    query.endDate = null
  }
  if (query.minAmount === '') query.minAmount = null
  if (query.maxAmount === '') query.maxAmount = null
  loading.value = true
  loadError.value = ''
  try {
    const res = await getBillPage(query)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    loadError.value = t('common.loadFailedDescription')
  } finally {
    loading.value = false
  }
}

function loadCategories() {
  return categoryLoader.load()
}

function onBillTypeChange() {
  return categoryLoader.load({ resetCategory: true })
}

function openDialog(row, type = 1) {
  isEdit.value = !!row
  if (row) {
    Object.assign(billForm, { id: row.id, type: row.type, categoryId: row.categoryId, amount: row.amount, billDate: row.billDate, remark: row.remark })
  } else {
    Object.assign(billForm, { id: null, type, categoryId: null, amount: null, billDate: formatLocalDate(), remark: '' })
  }
  loadCategories()
  dialogVisible.value = true
  nextTick(() => billFormRef.value?.clearValidate())
}

async function handleSubmit() {
  if (submitting.value || categoryLoading.value || categoryError.value) return
  await billFormRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) await updateBill(billForm)
    else await addBill(billForm)
    ElMessage.success(isEdit.value ? t('message.updateSuccess') : t('message.addSuccess'))
    dialogVisible.value = false
    loadBills()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await ElMessageBox.confirm(t('bill.deleteConfirm'), t('common.tip'), { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') })
  await deleteBill(id)
  ElMessage.success(t('bill.deleteSuccess'))
  loadBills()
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(t('bill.batchDeleteConfirm', { count: selectedIds.value.length }), t('common.tip'), { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') })
  await deleteBatchBill(selectedIds.value)
  ElMessage.success(t('bill.deleteSuccess'))
  selectedIds.value = []
  loadBills()
}

function applyRequestedMonth() {
  const month = route.query.month
  if (typeof month !== 'string' || !/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return
  const range = getMonthDateRange(month)
  query.startDate = range.startDate
  query.endDate = range.endDate
  dateRange.value = [range.startDate, range.endDate]
  query.page = 1
}
watch(() => route.query.month, () => { applyRequestedMonth(); loadBills() })
function openRequestedBill() {
  if (route.query.action !== 'add') return
  openDialog(null, route.query.type === '2' ? 2 : 1)
  const { action, type, ...remainingQuery } = route.query
  router.replace({ path: route.path, query: remainingQuery })
}

watch(() => [route.query.action, route.query.type], openRequestedBill)
onMounted(() => {
  applyRequestedMonth()
  loadBills()
  openRequestedBill()
})
onBeforeUnmount(() => categoryLoader.invalidate())
</script>

<style scoped>
/* ============================================================
   账单管理页样式
   ============================================================ */

/* 筛选卡片 */
.filter-card {
  margin-bottom: 20px;
  overflow: visible;
  box-shadow: var(--shadow-soft) !important;
}
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.filter-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  flex: 1;
}
.filter-controls :deep(.el-select),
.filter-controls :deep(.el-date-editor),
.filter-controls .amount-range .el-input {
  width: 150px;
}
.filter-controls :deep(.el-select__wrapper),
.filter-controls :deep(.el-input__wrapper) {
  height: 36px;
  border-radius: 6px;
}
.filter-controls :deep(.el-date-editor .el-input__wrapper) {
  height: 36px;
  border-radius: 6px;
}

/* 日期范围选择器 */
.filter-controls .el-date-picker {
  width: 260px;
}
.date-range-wrap {
  position: relative;
}

/* 快捷日期按钮组 */
.quick-btn-group { display: flex; gap: 6px; white-space: nowrap; }
.quick-btn {
  height: 32px; border-radius: 6px; font-size: 12px; padding: 5px 12px;
  transition: color 0.25s ease, border-color 0.25s ease, background-color 0.25s ease;
}
.quick-btn.el-button--default {
  border-color: var(--border-light) !important; color: var(--text-regular) !important;
}
.quick-btn.el-button--default:hover {
  border-color: var(--color-primary) !important; color: var(--color-primary) !important;
}
/* 快捷按钮高亮态：浅主题色底（分段控件视觉，不用实心主按钮样式） */
.quick-btn.el-button--primary {
  background: var(--color-primary-light) !important;
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
  box-shadow: none !important;
}
.quick-btn.el-button--primary::after { display: none; }

/* 金额范围 */
.amount-range { display: flex; align-items: center; gap: 4px; }
.amount-range .el-input { width: 110px; }
.range-sep { color: var(--text-secondary); font-size: 14px; line-height: 1; padding: 0 4px; }

/* 右侧按钮组 */
.filter-actions {
  display: flex; align-items: center; gap: 8px;
  white-space: nowrap; flex-shrink: 0;
}
.filter-actions .el-button { height: 36px; border-radius: 6px; }
.reset-btn { border-color: var(--border-strong); color: var(--text-regular); }

/* 表格卡片 */
.table-card {
  padding: 0;
  overflow: hidden;
  box-shadow: var(--shadow-soft) !important;
}
.table-card :deep(.el-card__body) { padding: 16px 16px 12px; }

/* 批量操作栏（表格左上角） */
.batch-bar-top {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 12px;
}
.batch-delete-btn {
  color: var(--color-danger) !important; border-color: color-mix(in srgb, var(--color-danger) 50%, transparent) !important;
  background: var(--color-danger-light) !important;
  border-radius: 6px !important; font-size: 12px !important;
}
.batch-delete-btn:hover {
  background: var(--color-danger-light) !important;
  color: var(--color-danger) !important; border-color: var(--color-danger) !important;
}
.batch-delete-btn::after { display: none; }
.batch-count { font-size: 12px; color: var(--text-secondary); }

/* 表格统一样式 */
.bill-table { width: 100%; }
.bill-table :deep(.el-table__header th) {
  background-color: var(--bg-table-header) !important;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-regular) !important;
  border-bottom: 1px solid var(--border-color) !important;
}
.bill-table :deep(.el-table__row) { height: 48px; transition: background-color 0.2s ease; }
.bill-table :deep(.el-table__row:hover) td {
  background-color: var(--bg-table-hover) !important;
}

/* 类型标签 */
.type-tag { border-radius: 4px; padding: 2px 8px; font-size: 12px; border: none; }
.type-tag.el-tag--danger { background: var(--color-danger-light); color: var(--color-danger); }
.type-tag.el-tag--success { background: var(--color-success-light); color: var(--color-success); }

/* 操作列分隔符 */
.action-divider { color: var(--border-strong); font-size: 12px; margin: 0 2px; }

/* 分页 */
.pagination { margin-top: 20px; display: flex; justify-content: center; }

@media (max-width: 1180px) {
  .filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-actions {
    justify-content: flex-end;
  }
}

@media (max-width: 720px) {
  .filter-controls,
  .date-range-wrap,
  .quick-btn-group,
  .amount-range,
  .filter-actions {
    width: 100%;
  }

  .filter-controls :deep(.el-select),
  .filter-controls :deep(.el-date-editor),
  .filter-controls .amount-range .el-input,
  .filter-controls .el-date-picker {
    width: 100%;
  }

  .quick-btn-group .el-button,
  .filter-actions .el-button {
    flex: 1;
  }

  .amount-range {
    flex-wrap: nowrap;
  }

  .table-card :deep(.el-card__body) {
    padding: 12px 0;
  }

  .batch-bar-top,
  .pagination {
    padding-right: 12px;
    padding-left: 12px;
  }
}
</style>
