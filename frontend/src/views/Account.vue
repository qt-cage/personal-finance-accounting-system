<template>
  <div class="management-page account-page">
    <PageHeader :title="$t('finance.accountTitle')" :description="$t('finance.accountSubtitle')">
      <template #actions>
        <el-button type="primary" @click="openDialog()">{{ $t('finance.addAccount') }}</el-button>
      </template>
    </PageHeader>
    <el-card shadow="never">
      <AppState v-if="loading" type="loading" :title="$t('common.loading')" :rows="4" />
      <AppState v-else-if="loadError" type="error" :title="$t('common.loadFailed')" :description="loadError" :action-label="$t('common.retry')" @retry="loadAccounts" />
      <AppState v-else-if="!accounts.length" type="empty" :title="$t('finance.noAccounts')" :description="$t('finance.emptyHint')" :action-label="$t('finance.createFirst')" @retry="openDialog()" />
      <template v-else>
        <p class="account-asof">{{ $t('finance.asOf', { date: accounts[0].asOfDate }) }}</p>
        <el-table :data="accounts" class="account-table" stripe>
          <el-table-column prop="name" :label="$t('finance.accountName')" min-width="130" />
          <el-table-column :label="$t('finance.accountType')" min-width="100">
            <template #default="{ row }">{{ typeLabel(row.accountType) }}</template>
          </el-table-column>
          <el-table-column :label="$t('finance.currentBalance')" min-width="135" align="right">
            <template #default="{ row }"><strong :class="{ debt: Number(row.balance) < 0 }">¥{{ formatAmount(row.balance) }}</strong></template>
          </el-table-column>
          <el-table-column prop="openingDate" :label="$t('finance.openingDate')" width="115" />
          <el-table-column :label="$t('common.operation')" min-width="260" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" @click="viewBills(row)">{{ $t('finance.viewBills') }}</el-button>
              <el-button text type="primary" @click="transferFrom(row)">{{ $t('finance.transfer') }}</el-button>
              <el-button text @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
              <el-button text type="danger" @click="remove(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="account-mobile-list">
          <article v-for="account in accounts" :key="account.id" class="account-mobile-card">
            <div class="account-mobile-heading">
              <strong class="account-mobile-name">{{ account.name }}</strong>
              <strong :class="{ debt: Number(account.balance) < 0 }">¥{{ formatAmount(account.balance) }}</strong>
            </div>
            <p class="account-mobile-meta">{{ typeLabel(account.accountType) }} · {{ $t('finance.openingDate') }} {{ account.openingDate }}</p>
            <div class="account-mobile-actions">
              <el-button text type="primary" @click="viewBills(account)">{{ $t('finance.viewBills') }}</el-button>
              <el-button text type="primary" @click="transferFrom(account)">{{ $t('finance.transfer') }}</el-button>
              <el-button text @click="openDialog(account)">{{ $t('common.edit') }}</el-button>
              <el-button text type="danger" @click="remove(account)">{{ $t('common.delete') }}</el-button>
            </div>
          </article>
        </div>
      </template>
    </el-card>
    <p class="account-hint">{{ $t('finance.balanceHint') }}</p>
    <el-dialog v-model="dialogVisible" :title="editing ? $t('finance.editAccount') : $t('finance.addAccount')" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-alert v-if="locked" :title="$t('planning.accountReferenced')" type="info" :closable="false" class="opening-alert" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item :label="$t('finance.accountName')" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item :label="$t('finance.accountType')" prop="accountType">
          <el-select v-model="form.accountType" :disabled="locked" style="width:100%">
            <el-option v-for="type in accountTypes" :key="type.code" :value="type.code" :label="type.name" :disabled="!type.enabled" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('finance.openingBalance')" prop="openingBalance">
          <el-input-number v-model="form.openingBalance" :precision="2" :min="-9999999999.99" :max="9999999999.99" :disabled="locked" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-form-item :label="$t('finance.openingDate')" prop="openingDate">
          <el-date-picker v-model="form.openingDate" type="date" value-format="YYYY-MM-DD" :disabled="locked" :disabled-date="date => date > new Date()" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccountList, addAccount, updateAccount, deleteAccount } from '../api/account'
import { getSystemDictionaries } from '../api/admin'
import { formatAmount } from '../utils/format'
import { formatLocalDate } from '../utils/businessDate'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'

const { t } = useI18n()
const router = useRouter()
const accounts = ref([])
const loading = ref(false)
const loadError = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const locked = ref(false)
const formRef = ref()
const accountTypes = ref([])
const typeKeys = { CASH: 'cash', BANK: 'bank', ALIPAY: 'alipay', WECHAT: 'wechat', CREDIT: 'credit', INVESTMENT: 'investment' }
const typeLabel = type => accountTypes.value.find(item => item.code === type)?.name || t(`finance.${typeKeys[type]}`)
const form = reactive({ name: '', accountType: 'CASH', openingBalance: 0, openingDate: '' })
const rules = computed(() => ({
  name: [{ required: true, whitespace: true, message: t('finance.nameRequired'), trigger: 'blur' }],
  accountType: [{ required: true, message: t('finance.accountRequired'), trigger: 'change' }],
  openingBalance: [{ required: true, message: t('finance.balanceRequired'), trigger: 'blur' }],
  openingDate: [{ required: true, message: t('finance.dateRequired'), trigger: 'change' }]
}))

async function loadAccounts() {
  loading.value = true
  loadError.value = ''
  try { const [accountResult, dictionaryResult] = await Promise.all([getAccountList(), getSystemDictionaries()]); accounts.value = accountResult.data || []; accountTypes.value = (dictionaryResult.data || []).filter(item => item.kind === 'ACCOUNT_TYPE') }
  catch { loadError.value = t('common.loadFailedDescription') }
  finally { loading.value = false }
}
function openDialog(row) {
  editing.value = row?.id || null
  locked.value = !!(row?.hasTransactions || row?.hasPlans)
  Object.assign(form, row ? { name: row.name, accountType: row.accountType, openingBalance: Number(row.openingBalance), openingDate: row.openingDate }
    : { name: '', accountType: 'CASH', openingBalance: 0, openingDate: formatLocalDate() })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
async function submit() {
  if (submitting.value) return
  if (!await formRef.value.validate().catch(() => false)) return
  submitting.value = true
  try {
    if (editing.value) await updateAccount(editing.value, form)
    else await addAccount(form)
    ElMessage.success(t(editing.value ? 'message.updateSuccess' : 'message.addSuccess'))
    dialogVisible.value = false
    await loadAccounts()
  } catch { /* 请求层已显示错误，保留账户表单以便修正。 */ }
  finally { submitting.value = false }
}
async function remove(row) {
  await ElMessageBox.confirm(t('finance.deleteAccountConfirm'), t('common.tip'), { type: 'warning' })
  await deleteAccount(row.id)
  ElMessage.success(t('finance.accountDeleted'))
  await loadAccounts()
}
const viewBills = row => router.push({ path: '/bill', query: { accountId: row.id } })
function transferFrom(row) {
  if (accounts.value.length < 2) return ElMessage.info(t('finance.needTwoAccounts'))
  router.push({ path: '/bill', query: { action: 'transfer', accountId: row.id } })
}
onMounted(loadAccounts)
</script>

<style scoped>
.account-asof, .account-hint { color: var(--text-secondary); font-size: 13px; line-height: 1.7; }
.account-asof { margin: 0 0 14px; }
.account-hint { margin: 18px 2px 0; }
.account-table { width: 100%; }
.debt { color: var(--color-danger); }
.opening-alert { margin-bottom: 16px; }
.account-mobile-list { display: none; }
@media (max-width: 600px) {
  .account-page :deep(.el-card__body) { padding: 14px; }
  .account-table { display: none; }
  .account-mobile-list { display: grid; gap: 14px; }
  .account-mobile-card { border-bottom: 1px solid var(--border-light); padding-bottom: 12px; }
  .account-mobile-card:last-child { border-bottom: 0; padding-bottom: 0; }
  .account-mobile-heading { display: flex; justify-content: space-between; align-items: start; gap: 12px; }
  .account-mobile-name { min-width: 0; overflow-wrap: anywhere; }
  .account-mobile-meta { color: var(--text-secondary); font-size: 12px; line-height: 1.7; margin: 8px 0; }
  .account-mobile-actions { display: flex; flex-wrap: wrap; gap: 4px; }
  .account-mobile-actions :deep(.el-button + .el-button) { margin-left: 0; }
}
</style>
