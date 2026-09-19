<template>
  <div class="dashboard-root">
    <PageHeader :title="$t('nav.dashboard')" :stamp="$t('dashboard.periodStamp', { period: loadedMonth || queryMonth })">
      <template #actions><el-button type="primary" @click="openBill">{{ $t('bill.addBill') }}</el-button></template>
    </PageHeader>
    <div class="filter-bar">
      <el-date-picker v-model="queryMonth" type="month" value-format="YYYY-MM" :clearable="false" :aria-label="$t('common.selectMonth')" :disabled="loading" />
      <div class="quick-months">
        <el-button :disabled="loading" @click="setQuickMonth(0)">{{ $t('dashboard.thisMonth') }}</el-button>
        <el-button :disabled="loading" @click="setQuickMonth(-1)">{{ $t('dashboard.lastMonth') }}</el-button>
      </div>
      <el-button class="query-button" :loading="loading" @click="loadDashboardData">{{ $t('common.query') }}</el-button>
    </div>
    <AppState v-if="loading && !loaded" type="loading" :title="$t('common.loading')" :rows="5" />
    <AppState v-else-if="loadError && !loaded" type="error" :title="$t('common.loadFailed')" :description="$t('common.loadFailedDescription')" :action-label="$t('common.retry')" @retry="loadDashboardData" />
    <div v-else v-loading="loading" class="dashboard-content">
      <AppState v-if="loadError" type="error" compact :title="$t('common.loadFailed')" :description="$t('common.loadFailedDescription')" :action-label="$t('common.retry')" @retry="loadDashboardData" />
      <MetricStrip :items="metricItems" />
      <div class="budget-action"><router-link :to="{ path: '/budget', query: { month: loadedMonth } }">{{ $t('dashboard.manageBudget') }} →</router-link></div>
      <div class="category-grid">
        <CategoryBreakdown :title="$t('dashboard.expenseCategoryRatio')" :rows="expenses" tone="danger" :empty-title="$t('dashboard.noExpenseData')" @create="openBill" @previous="setQuickMonth(-1)" />
        <CategoryBreakdown :title="$t('dashboard.incomeCategoryRatio')" :rows="incomes" tone="success" :empty-title="$t('dashboard.noIncomeData')" @create="openBill" @previous="setQuickMonth(-1)" />
      </div>
      <el-card shadow="never" class="recent-card">
        <template #header><div class="card-heading"><strong>{{ $t('dashboard.recentRecords') }}</strong><router-link :to="{ path: '/bill', query: { month: loadedMonth } }">{{ $t('dashboard.viewAll') }} →</router-link></div></template>
        <el-table :data="recentBills" :empty-text="$t('common.noData')">
          <el-table-column prop="billDate" :label="$t('bill.date')" width="118" />
          <el-table-column :label="$t('bill.type')" width="72"><template #default="{ row }">{{ row.type === 1 ? $t('bill.expense') : $t('bill.income') }}</template></el-table-column>
          <el-table-column prop="categoryName" :label="$t('bill.category')" min-width="140" show-overflow-tooltip />
          <el-table-column :label="$t('bill.amount')" align="right" min-width="150"><template #default="{ row }"><span class="amount" :class="row.type === 1 ? 'expense' : 'income'">{{ row.type === 1 ? '−' : '+' }} ¥{{ formatAmount(row.amount) }}</span></template></el-table-column>
          <el-table-column prop="remark" :label="$t('bill.remark')" min-width="170" show-overflow-tooltip />
        </el-table>
      </el-card>
    </div>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { getStatistics, getCategoryStats, getBillPage } from '../api/bill'
import { getBudgetList } from '../api/budget'
import { formatAmount } from '../utils/format'
import { formatLocalMonth, getMonthDateRange } from '../utils/businessDate'
import PageHeader from '../components/PageHeader.vue'
import MetricStrip from '../components/MetricStrip.vue'
import AppState from '../components/AppState.vue'
import CategoryBreakdown from '../components/CategoryBreakdown.vue'
const { t } = useI18n()
const router = useRouter()
const queryMonth = ref(formatLocalMonth())
const loadedMonth = ref('')
const stats = ref({ income: 0, expense: 0, balance: 0 })
const expenses = ref([]), incomes = ref([]), recentBills = ref([])
const totalBudget = ref(null)
const loading = ref(false), loaded = ref(false), loadError = ref('')
const metricItems = computed(() => {
  const period = loadedMonth.value
  const spent = Number(stats.value.expense || 0)
  const total = Number(totalBudget.value?.amount || 0)
  const percentage = total > 0 ? Math.round(spent / total * 100) : null
  return [
    { key: 'balance', label: `${period} · ${t('dashboard.periodBalance')}`, value: `¥${formatAmount(stats.value.balance)}`, tone: Number(stats.value.balance) < 0 ? 'danger' : 'neutral' },
    { key: 'income', label: `${period} · ${t('dashboard.income')}`, value: `¥${formatAmount(stats.value.income)}`, tone: 'success' },
    { key: 'expense', label: `${period} · ${t('dashboard.expense')}`, value: `¥${formatAmount(spent)}`, tone: 'danger' },
    { key: 'budget', label: t('dashboard.budgetProgress'), value: percentage == null ? '—' : `${percentage}%`, tone: percentage >= 100 ? 'danger' : percentage >= 80 ? 'warning' : 'primary', caption: percentage == null ? t('dashboard.noTotalBudget') : `¥${formatAmount(spent)} / ¥${formatAmount(total)}`, progress: percentage }
  ]
})
function openBill() { router.push({ path: '/bill', query: { action: 'add', month: loadedMonth.value || queryMonth.value } }) }
function setQuickMonth(offset) {
  const now = new Date()
  queryMonth.value = formatLocalMonth(new Date(now.getFullYear(), now.getMonth() + offset, 1))
  loadDashboardData()
}
async function loadDashboardData() {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  const requestedMonth = queryMonth.value
  try {
    const range = getMonthDateRange(requestedMonth)
    const [summary, expense, income, budgets, bills] = await Promise.all([
      getStatistics(range), getCategoryStats({ ...range, type: 1 }), getCategoryStats({ ...range, type: 2 }),
      getBudgetList({ month: requestedMonth }), getBillPage({ ...range, page: 1, size: 5 })
    ])
    // 所有请求成功后一起提交，使标题、指标和明细始终对应同一个账期。
    stats.value = summary.data
    expenses.value = expense.data || []
    incomes.value = income.data || []
    recentBills.value = bills.data.records || []
    totalBudget.value = budgets.data.find(row => row.categoryId == null) || null
    loadedMonth.value = requestedMonth
    loaded.value = true
  } catch { loadError.value = t('common.loadFailedDescription') }
  finally { loading.value = false }
}
onMounted(loadDashboardData)
</script>
<style scoped>
.filter-bar { display:flex; gap:12px; align-items:center; flex-wrap:wrap; padding:14px 16px; margin-bottom:20px; background:var(--bg-card); border:1px solid var(--border-color); border-radius:8px; }
.quick-months { display:flex; gap:8px; }
.quick-months .el-button + .el-button { margin-left:0; }
.query-button { margin-left:auto; }
.budget-action { margin:-10px 0 18px; text-align:right; font-size:13px; }
a { color:var(--color-primary); text-underline-offset:4px; }
a:hover { text-decoration:underline; }
.category-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px; margin-bottom:20px; align-items:start; }
.card-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; font-size:14px; }
.card-heading a { font-size:13px; }
.amount { font-variant-numeric:tabular-nums; white-space:nowrap; font-weight:600; }
.expense { color:var(--color-danger); }
.income { color:var(--color-success); }
@media(max-width:800px) { .category-grid { grid-template-columns:1fr; } }
@media(max-width:620px) { .filter-bar { gap:8px; padding:12px; } .filter-bar :deep(.el-date-editor) { width:100%; } .query-button { margin-left:auto; } }
</style>