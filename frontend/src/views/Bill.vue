<template>
  <div class="management-page bill-page">
    <PageHeader :title="$t('nav.bill')" :description="$t('bill.subtitle')">
      <template #actions>
        <BillFileTools :filters="exportFilters" @imported="loadBills" />
        <el-button @click="openTransfer()">{{ $t('finance.addTransfer') }}</el-button>
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
            <el-option :label="$t('finance.transfer')" :value="3" />
          </el-select>
          <el-select v-model="query.accountId" :placeholder="$t('finance.selectAccount')" clearable :loading="accountLoading">
            <el-option v-for="account in accounts" :key="account.id" :label="account.name" :value="account.id" />
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
              :type="billTone(row.type)"
              class="type-tag"
            >{{ $t(billTypeKey(row.type)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('bill.category')" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.type === 3 ? $t('finance.transfer') : row.categoryName }}</template>
        </el-table-column>
        <el-table-column :label="$t('finance.account')" min-width="155" show-overflow-tooltip>
          <template #default="{ row }">{{ row.accountName || $t('finance.pending') }}<span v-if="row.type === 3"> → {{ row.transferAccountName }}</span></template>
        </el-table-column>
        <el-table-column :label="$t('bill.amount')" width="160" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.type === 3 ? 'var(--text-secondary)' : `var(--color-${billTone(row.type)})`, fontWeight: 600, fontVariantNumeric: 'tabular-nums' }">
              {{ billSign(row.type) }} ¥{{ formatAmount(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('bill.remark')" min-width="140" show-overflow-tooltip />
        <el-table-column :label="$t('common.operation')" width="145" fixed="right" align="right">
          <template #default="{ row }">
            <el-button text size="small" @click="receiptBill=row.id; receiptsVisible=true">{{ $t('files.receipts') }}</el-button>
            <el-button v-if="row.type !== 3" type="primary" text size="small" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <span v-if="row.type !== 3" class="action-divider">|</span>
            <el-button type="danger" text size="small" @click="handleDelete(row)">{{ row.type === 3 ? $t('finance.reverse') : $t('common.delete') }}</el-button>
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

    <BillAttachments v-model="receiptsVisible" :bill-id="receiptBill" />
    <!-- ============================================================
         新增/编辑弹窗
         ============================================================ -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? $t('bill.editBill') : $t('bill.addBill')"
      width="min(520px, calc(100vw - 32px))"
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
            <el-option v-for="c in categories" :key="c.id" :label="categoryLabel(c, categories)" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-alert v-if="categoryError" :title="$t('common.loadFailed')" type="error" :closable="false" show-icon>
          <el-button text type="primary" @click="loadCategories">{{ $t('common.retry') }}</el-button>
        </el-alert>
        <el-form-item :label="$t('finance.account')" prop="accountId">
          <el-select v-model="billForm.accountId" :placeholder="$t('finance.pending')" :clearable="!initialAccountAssigned" :loading="accountLoading" :disabled="accountLoading || accountError" style="width:100%">
            <el-option v-for="account in accounts" :key="account.id" :label="account.name" :value="account.id" />
          </el-select>
        </el-form-item>
        <el-alert v-if="accountError" :title="$t('common.loadFailed')" type="error" :closable="false">
          <el-button text type="primary" @click="loadAccounts">{{ $t('common.retry') }}</el-button>
        </el-alert>
        <p v-else-if="!accounts.length" class="account-form-hint">{{ $t('finance.createAccountHint') }} <router-link to="/account">{{ $t('finance.addAccount') }}</router-link></p>
        <p v-else-if="isEdit && !initialAccountAssigned" class="account-form-hint">{{ $t('finance.pendingHint') }}</p>
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
        <el-button type="primary" :loading="submitting" :disabled="categoryLoading || categoryError || accountLoading || accountError" @click="handleSubmit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="transferVisible" :title="$t('finance.addTransfer')" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="false">
      <p class="account-form-hint">{{ $t('finance.transferHint') }}</p>
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-position="top">
        <el-form-item :label="$t('finance.fromAccount')" prop="accountId">
          <el-select v-model="transferForm.accountId" :placeholder="$t('finance.selectFrom')" style="width:100%">
            <el-option v-for="account in accounts" :key="account.id" :label="account.name" :value="account.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('finance.toAccount')" prop="transferAccountId">
          <el-select v-model="transferForm.transferAccountId" :placeholder="$t('finance.selectTo')" style="width:100%">
            <el-option v-for="account in accounts" :key="account.id" :label="account.name" :value="account.id" :disabled="account.id === transferForm.accountId" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('bill.amount')" prop="amount"><el-input-number v-model="transferForm.amount" :min="0.01" :max="9999999999.99" :precision="2" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('bill.date')" prop="billDate"><el-date-picker v-model="transferForm.billDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('bill.remark')"><el-input v-model="transferForm.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="transferring" @click="submitTransfer">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { getBillPage, addBill, updateBill, deleteBill, deleteBatchBill, addTransfer } from '../api/bill'
import { getAccountList } from '../api/account'
import { getCategoryList } from '../api/category'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatAmount } from '../utils/format'
import { formatLocalDate, getQuickDateRange, getMonthDateRange } from '../utils/businessDate'
import { createBillCategoryLoader } from '../utils/billCategoryLoader'
import { categoryLabel } from '../utils/categoryHierarchy'
import { billTypeKey, billTone, billSign } from '../utils/billPresentation'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'
import BillFileTools from '../components/BillFileTools.vue'
import BillAttachments from '../components/BillAttachments.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const query = reactive({ type: null, accountId: null, startDate: null, endDate: null, minAmount: null, maxAmount: null, page: 1, size: 10 })
const dateRange = ref(null)
const receiptsVisible = ref(false), receiptBill = ref(null)
const exportFilters = computed(() => ({ ...query, startDate: dateRange.value?.[0], endDate: dateRange.value?.[1] }))
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
let billRequest = 0
const loadError = ref('')
const accounts = ref([])
const accountLoading = ref(false)
const accountError = ref(false)
const initialAccountAssigned = ref(false)
const transferVisible = ref(false)
const transferring = ref(false)
const transferFormRef = ref()
const transferForm = reactive({ accountId: null, transferAccountId: null, amount: null, billDate: '', remark: '', requestId: '' })

const billForm = reactive({ id: null, type: 1, categoryId: null, accountId: null, amount: null, billDate: '', remark: '' })
const categoryLoader = createBillCategoryLoader({
  form: billForm,
  fetchCategories: getCategoryList,
  onOptions: options => { categories.value = options },
  onLoading: value => { categoryLoading.value = value },
  onError: error => { categoryError.value = !!error }
})
const billRules = {
  accountId: [{ validator: (rule, value, callback) => !isEdit.value && !value ? callback(new Error(t('finance.accountRequired'))) : callback(), trigger: 'change' }],
  type: [{ required: true, message: t('validation.typeRequired'), trigger: 'change' }],
  categoryId: [{ required: true, message: t('validation.categoryRequired'), trigger: 'change' }],
  amount: [{ required: true, message: t('validation.amountRequired'), trigger: 'blur' }],
  billDate: [{ required: true, message: t('validation.dateRequired'), trigger: 'change' }]
}
const layout = 'total, prev, pager, next'
const transferRules = {
  accountId: [{ required: true, message: t('finance.selectFrom'), trigger: 'change' }],
  transferAccountId: [{ validator: (rule, value, callback) => !value ? callback(new Error(t('finance.selectTo'))) : value === transferForm.accountId ? callback(new Error(t('finance.differentAccounts'))) : callback(), trigger: 'change' }],
  amount: billRules.amount,
  billDate: billRules.billDate
}

async function loadAccounts() {
  accountLoading.value = true
  accountError.value = false
  try { accounts.value = (await getAccountList()).data || []; return true }
  catch { accountError.value = true; return false }
  finally { accountLoading.value = false }
}
async function openTransfer() {
  if (!await loadAccounts()) return
  if (accounts.value.length < 2) return ElMessage.info(t('finance.needTwoAccounts'))
  Object.assign(transferForm, { accountId: accounts.value.some(a => a.id === query.accountId) ? query.accountId : null,
    transferAccountId: null, amount: null, billDate: formatLocalDate(), remark: '', requestId: crypto.randomUUID() })
  transferVisible.value = true
  nextTick(() => transferFormRef.value?.clearValidate())
}
async function submitTransfer() {
  if (transferring.value || !await transferFormRef.value.validate().catch(() => false)) return
  transferring.value = true
  try {
    await addTransfer(transferForm)
    ElMessage.success(t('finance.transferSuccess'))
    transferVisible.value = false
    await loadBills()
  } catch { /* 请求层显示错误，保留请求编号供重试，避免重复转账。 */ }
  finally { transferring.value = false }
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

function setQuickDate(type) {
  quickType.value = type
  const { start, end } = getQuickDateRange(type)
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
  query.accountId = null
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
  const current = ++billRequest
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
    const res = await getBillPage({ ...query })
    if (current !== billRequest) return
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    if (current === billRequest) loadError.value = t('common.loadFailedDescription')
  } finally {
    if (current === billRequest) loading.value = false
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
  initialAccountAssigned.value = !!row?.accountId
  if (row) {
    Object.assign(billForm, { id: row.id, type: row.type, categoryId: row.categoryId, accountId: row.accountId, amount: row.amount, billDate: row.billDate, remark: row.remark })
  } else {
    Object.assign(billForm, { id: null, type, categoryId: null, accountId: query.accountId, amount: null, billDate: formatLocalDate(), remark: '' })
  }
  loadCategories()
  loadAccounts()
  dialogVisible.value = true
  nextTick(() => billFormRef.value?.clearValidate())
}

async function handleSubmit() {
  if (submitting.value || categoryLoading.value || categoryError.value || accountLoading.value || accountError.value) return
  if (!await billFormRef.value.validate().catch(() => false)) return
  submitting.value = true
  try {
    if (isEdit.value) await updateBill(billForm)
    else await addBill(billForm)
    ElMessage.success(isEdit.value ? t('message.updateSuccess') : t('message.addSuccess'))
    dialogVisible.value = false
    loadBills()
  } catch { /* 请求层显示错误，保留表单。 */ }
  finally { submitting.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(t(row.type === 3 ? 'finance.reverseConfirm' : 'bill.deleteConfirm'), t('common.tip'), { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') })
  await deleteBill(row.id)
  ElMessage.success(t('bill.deleteSuccess'))
  loadBills()
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(t('bill.batchDeleteConfirm', { count: selectedIds.value.length }) + ' ' + t('finance.batchHint'), t('common.tip'), { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') })
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
async function openRequestedBill() {
  if (route.query.action === 'transfer') await openTransfer()
  else if (route.query.action === 'add') openDialog(null, route.query.type === '2' ? 2 : 1)
  else return
  const { action, type, ...remainingQuery } = route.query
  router.replace({ path: route.path, query: remainingQuery })
}

watch(() => [route.query.action, route.query.type], openRequestedBill)
function applyRequestedAccount() {
  const id = Number(route.query.accountId)
  query.accountId = Number.isSafeInteger(id) && id > 0 ? id : null
}
watch(() => route.query.accountId, () => { applyRequestedAccount(); query.page = 1; loadBills() })
onMounted(async () => {
  applyRequestedMonth()
  applyRequestedAccount()
  loadBills()
  await loadAccounts()
  openRequestedBill()
})
onBeforeUnmount(() => { billRequest++; categoryLoader.invalidate() })
</script>

<style scoped>
.account-form-hint { color: var(--text-secondary); font-size: 13px; line-height: 1.7; margin: 0 0 16px; }
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

/* 类型标签 */
.type-tag { border-radius: 4px; padding: 2px 8px; font-size: 12px; border: none; }
.type-tag.el-tag--danger { background: var(--color-danger-light); color: var(--color-danger); }
.type-tag.el-tag--success { background: var(--color-success-light); color: var(--color-success); }

/* 操作列分隔符 */
.action-divider { color: var(--border-strong); font-size: 12px; margin: 0 2px; }
.bill-table :deep(.el-button.is-text) { padding: 4px 2px; margin-left: 0; }
.bill-table :deep(.el-table-fixed-column--right) { background-color: var(--bg-card) !important; }

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
