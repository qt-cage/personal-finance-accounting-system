<template>
  <div class="management-page recurring-page">
    <PageHeader :title="$t('planning.recurringTitle')" :description="$t('planning.recurringSubtitle')">
      <template #actions><el-button :loading="processing" @click="processDue">{{ $t('planning.runDue') }}</el-button><el-button type="primary" @click="open()">{{ $t('planning.addPlan') }}</el-button></template>
    </PageHeader>
    <el-alert :title="$t(automatic ? 'planning.automaticOn' : 'planning.automaticOff')" :type="automatic ? 'success' : 'info'" :closable="false" class="recurring-alert" />
    <el-card shadow="never">
      <AppState v-if="loading" type="loading" :title="$t('common.loading')" />
      <AppState v-else-if="error" type="error" :title="$t('common.loadFailed')" :action-label="$t('common.retry')" @retry="load" />
      <AppState v-else-if="!plans.length" type="empty" :title="$t('planning.noPlans')" :description="$t('planning.noPlansHint')" />
      <el-table v-else :data="plans" stripe>
        <el-table-column prop="name" :label="$t('planning.planName')" min-width="140" />
        <el-table-column :label="$t('bill.type')" width="80"><template #default="{row}">{{ $t(row.type===1?'bill.expense':'bill.income') }}</template></el-table-column>
        <el-table-column :label="$t('bill.amount')" width="130" align="right"><template #default="{row}">¥{{ formatAmount(row.amount) }}</template></el-table-column>
        <el-table-column :label="$t('planning.frequency')" width="90"><template #default="{row}">{{ frequencyLabel(row.frequency) }}</template></el-table-column>
        <el-table-column prop="nextDate" :label="$t('planning.nextDate')" width="120" />
        <el-table-column :label="$t('planning.status')" min-width="120"><template #default="{row}">{{ planStatus(row) }}<small v-if="row.lastError" class="plan-error">{{ row.lastError }}</small></template></el-table-column>
        <el-table-column :label="$t('common.operation')" min-width="200"><template #default="{row}">
          <el-button text type="primary" @click="toggle(row)">{{ $t(row.enabled?'planning.pause':'planning.resume') }}</el-button>
          <el-button text @click="open(row)">{{ $t('common.edit') }}</el-button><el-button text type="danger" @click="remove(row)">{{ $t('common.delete') }}</el-button>
        </template></el-table-column>
      </el-table>
    </el-card>
    <p class="recurring-hint">{{ $t('planning.planHint') }}</p>
    <el-dialog v-model="visible" :title="$t(editing?'planning.editPlan':'planning.addPlan')" width="min(600px, calc(100vw - 32px))" :close-on-click-modal="false">
      <el-alert v-if="scheduleLocked" :title="$t('planning.scheduleLocked')" type="info" :closable="false" class="recurring-alert" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item :label="$t('planning.planName')" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item :label="$t('bill.type')"><el-radio-group v-model="form.type" @change="onTypeChange"><el-radio :value="1">{{ $t('bill.expense') }}</el-radio><el-radio :value="2">{{ $t('bill.income') }}</el-radio></el-radio-group></el-form-item>
        <div class="plan-fields">
          <el-form-item :label="$t('bill.category')" prop="categoryId"><el-select v-model="form.categoryId" :loading="optionsLoading" :disabled="optionsLoading || optionsError" style="width:100%"><el-option v-for="c in categories" :key="c.id" :label="categoryLabel(c,categories)" :value="c.id" /></el-select></el-form-item>
          <el-form-item :label="$t('finance.account')" prop="accountId"><el-select v-model="form.accountId" style="width:100%"><el-option v-for="account in accounts" :key="account.id" :label="account.name" :value="account.id" /></el-select></el-form-item>
        </div>
        <el-alert v-if="optionsError" :title="$t('common.loadFailed')" type="error" :closable="false"><el-button text @click="loadCategories">{{ $t('common.retry') }}</el-button></el-alert>
        <el-form-item :label="$t('bill.amount')" prop="amount"><el-input-number v-model="form.amount" :precision="2" :min="0.01" :max="9999999999.99" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item :label="$t('planning.frequency')"><el-select v-model="form.frequency" :disabled="scheduleLocked" style="width:100%"><el-option v-for="frequency in frequencies" :key="frequency" :value="frequency" :label="frequencyLabel(frequency)" /></el-select></el-form-item>
        <div class="plan-fields">
          <el-form-item :label="$t('planning.startDate')" prop="startDate"><el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" :disabled="scheduleLocked" style="width:100%" /></el-form-item>
          <el-form-item :label="$t('planning.endDate')"><el-date-picker v-model="form.endDate" value-format="YYYY-MM-DD" clearable style="width:100%" /></el-form-item>
        </div>
        <el-form-item :label="$t('bill.remark')"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible=false">{{ $t('common.cancel') }}</el-button><el-button type="primary" :loading="saving" :disabled="optionsLoading || optionsError" @click="save">{{ $t('common.confirm') }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRecurringList, addRecurring, updateRecurring, deleteRecurring, setRecurringEnabled, runRecurring } from '../api/planning'
import { getAccountList } from '../api/account'
import { getCategoryList } from '../api/category'
import { categoryLabel } from '../utils/categoryHierarchy'
import { createBillCategoryLoader } from '../utils/billCategoryLoader'
import { formatLocalDate } from '../utils/businessDate'
import { formatAmount } from '../utils/format'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'

const {t}=useI18n()
const plans=ref([]), accounts=ref([]), categories=ref([]), automatic=ref(false), loading=ref(false), error=ref('')
const visible=ref(false), editing=ref(null), scheduleLocked=ref(false), saving=ref(false), processing=ref(false), optionsLoading=ref(false), formRef=ref()
const optionsError=ref(false)
const frequencies=['DAILY','WEEKLY','MONTHLY']
const frequencyKeys={DAILY:'daily',WEEKLY:'weekly',MONTHLY:'monthly'}
const frequencyLabel=value=>t(`planning.${frequencyKeys[value]}`)
const form=reactive({name:'',type:1,categoryId:null,accountId:null,amount:null,frequency:'MONTHLY',startDate:'',endDate:null,remark:''})
const categoryLoader=createBillCategoryLoader({form,fetchCategories:getCategoryList,onOptions:items=>{categories.value=items},onLoading:value=>{optionsLoading.value=value},onError:value=>{optionsError.value=!!value}})
const rules=computed(()=>({name:[{required:true,whitespace:true,message:t('planning.nameRequired')}],
  categoryId:[{required:true,message:t('validation.categoryRequired')}], accountId:[{required:true,message:t('finance.accountRequired')}],
  amount:[{required:true,message:t('validation.amountRequired')}], startDate:[{required:true,message:t('validation.dateRequired')}]}))

function planStatus(plan) {
  if (!plan.enabled) return t('planning.paused')
  if (plan.endDate && plan.nextDate>plan.endDate) return t('planning.finished')
  return t(plan.lastError?'planning.failed':'planning.running')
}
async function load() {
  loading.value=true; error.value=''
  try { const value=(await getRecurringList()).data; plans.value=value.items; automatic.value=value.automaticEnabled }
  catch { error.value=t('common.loadFailedDescription') }
  finally { loading.value=false }
}
const loadCategories=()=>categoryLoader.load()
const onTypeChange=()=>categoryLoader.load({resetCategory:true})
async function open(plan) {
  editing.value=plan?.id || null; scheduleLocked.value=!!plan?.hasGenerated
  Object.assign(form,plan ? {name:plan.name,type:plan.type,categoryId:plan.categoryId,accountId:plan.accountId,amount:Number(plan.amount),frequency:plan.frequency,startDate:plan.startDate,endDate:plan.endDate,remark:plan.remark}
    : {name:'',type:1,categoryId:null,accountId:null,amount:null,frequency:'MONTHLY',startDate:formatLocalDate(),endDate:null,remark:''})
  try { accounts.value=(await getAccountList()).data || []; await loadCategories(); visible.value=true; nextTick(()=>formRef.value?.clearValidate()) }
  catch { /* 请求层已展示错误，保留当前列表。 */ }
}
async function save() {
  if(saving.value || optionsLoading.value || optionsError.value || !await formRef.value.validate().catch(()=>false))return
  saving.value=true
  try { if(editing.value)await updateRecurring(editing.value,form); else await addRecurring(form); visible.value=false; await load(); ElMessage.success(t('message.setSuccess')) }
  catch { /* 保留表单供修正。 */ }
  finally { saving.value=false }
}
async function toggle(plan) { try { await setRecurringEnabled(plan.id,!plan.enabled); await load() } catch { /* 请求层展示错误。 */ } }
async function remove(plan) {
  try { await ElMessageBox.confirm(t('planning.deleteConfirm'),t('common.tip'),{type:'warning'}); await deleteRecurring(plan.id); await load() }
  catch { /* 取消或失败均保留列表。 */ }
}
async function processDue() {
  if(processing.value)return
  processing.value=true
  try { const result=(await runRecurring()).data; ElMessage[result.failed?'warning':'success'](t('planning.runResult',result)); await load() }
  catch { /* 请求层展示错误。 */ }
  finally { processing.value=false }
}
onMounted(load)
onBeforeUnmount(()=>categoryLoader.invalidate())
</script>

<style scoped>
.recurring-alert { margin-bottom:20px; }
.recurring-hint { color:var(--text-secondary); font-size:13px; line-height:1.7; margin-top:18px; }
.plan-error { display:block; color:var(--color-danger); line-height:1.5; }
.plan-fields { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
@media(max-width:600px) { .plan-fields { grid-template-columns:1fr; gap:0; } }
</style>
