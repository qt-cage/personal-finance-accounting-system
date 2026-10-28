<template>
  <div class="management-page admin-page">
    <PageHeader :title="$t('admin.title')" :description="$t('admin.subtitle')" />
    <el-card shadow="never">
      <el-tabs v-model="tab" @tab-change="load">
        <el-tab-pane v-for="name in ['users','categories','dictionaries','logs','settings']" :key="name" :name="name" :label="$t(`admin.${name}`)" />
      </el-tabs>
      <AppState v-if="loading" type="loading" compact :title="$t('common.loading')" />
      <AppState v-else-if="error" type="error" :title="$t('common.loadFailed')" :action-label="$t('common.retry')" @retry="load" />
      <template v-else>
        <template v-if="tab === 'users'">
          <div class="admin-actions"><el-input v-model="userQuery.keyword" :placeholder="$t('admin.keyword')" clearable @keyup.enter="search" /><el-button @click="search">{{ $t('common.query') }}</el-button></div>
          <el-table :data="users" :empty-text="$t('admin.empty')">
            <el-table-column prop="username" :label="$t('admin.username')" min-width="150" /><el-table-column prop="nickname" :label="$t('admin.nickname')" min-width="130" />
            <el-table-column :label="$t('admin.role')" min-width="120"><template #default="{ row }">{{ $t(row.role === 'ADMIN' ? 'admin.admin' : 'admin.user') }}</template></el-table-column>
            <el-table-column :label="$t('admin.status')" min-width="90"><template #default="{ row }">{{ $t(row.status === 'ACTIVE' ? 'admin.active' : 'admin.disabled') }}</template></el-table-column>
            <el-table-column :label="$t('common.operation')" width="80"><template #default="{ row }"><el-button text @click="editUser(row)">{{ $t('common.edit') }}</el-button></template></el-table-column>
          </el-table>
          <el-pagination v-model:current-page="userQuery.page" :page-size="20" :total="userTotal" layout="total,prev,pager,next" @current-change="load" />
        </template>
        <template v-else-if="tab === 'categories'">
          <p class="admin-hint">{{ $t('admin.sharedHint') }}</p><el-button @click="editCategory()">{{ $t('admin.addCategory') }}</el-button>
          <el-table :data="categories" :empty-text="$t('admin.empty')">
            <el-table-column prop="name" :label="$t('bill.category')" min-width="130" /><el-table-column :label="$t('bill.type')" width="90"><template #default="{ row }">{{ $t(row.type === 1 ? 'bill.expense' : 'bill.income') }}</template></el-table-column>
            <el-table-column :label="$t('finance.parentCategory')" min-width="120"><template #default="{ row }">{{ categories.find(c => c.id === row.parentId)?.name || '—' }}</template></el-table-column>
            <el-table-column :label="$t('common.operation')" min-width="130"><template #default="{ row }"><el-button text @click="editCategory(row)">{{ $t('common.edit') }}</el-button><el-button text type="danger" @click="removeCategory(row)">{{ $t('common.delete') }}</el-button></template></el-table-column>
          </el-table>
        </template>
        <template v-else-if="tab === 'dictionaries'">
          <el-table :data="dictionaries" :empty-text="$t('admin.empty')"><el-table-column :label="$t('admin.kind')" min-width="110"><template #default="{ row }">{{ $t(row.kind === 'CURRENCY' ? 'admin.currency' : 'admin.accountType') }}</template></el-table-column><el-table-column prop="code" :label="$t('admin.code')" min-width="125" /><el-table-column prop="name" :label="$t('admin.name')" min-width="120" /><el-table-column :label="$t('admin.status')" width="80"><template #default="{ row }">{{ $t(row.enabled ? 'admin.active' : 'admin.disabled') }}</template></el-table-column><el-table-column :label="$t('common.operation')" width="80"><template #default="{ row }"><el-button text @click="editDictionary(row)">{{ $t('common.edit') }}</el-button></template></el-table-column></el-table>
        </template>
        <template v-else-if="tab === 'logs'">
          <el-table :data="logs" :empty-text="$t('admin.empty')"><el-table-column prop="username" :label="$t('admin.actor')" min-width="130" /><el-table-column prop="method" :label="$t('admin.method')" width="90" /><el-table-column prop="route" :label="$t('admin.route')" min-width="260" /><el-table-column prop="outcome" :label="$t('admin.result')" width="90" /><el-table-column prop="createTime" :label="$t('admin.time')" min-width="170" /></el-table>
          <el-pagination v-model:current-page="logPage" :page-size="20" :total="logTotal" layout="total,prev,pager,next" @current-change="load" />
        </template>
        <el-form v-else :model="settings" label-position="top" class="settings-form" @submit.prevent="saveSettings"><el-form-item :label="$t('admin.siteName')"><el-input v-model="settings.siteName" maxlength="50" /></el-form-item><el-form-item><span class="switch-label">{{ $t('admin.registration') }}</span><el-switch v-model="settings.registrationEnabled" :aria-label="$t('admin.registration')" /></el-form-item><el-button type="primary" :loading="saving" @click="saveSettings">{{ $t('profile.saveChanges') }}</el-button></el-form>
      </template>
    </el-card>
    <el-dialog v-model="userDialog" :title="$t('admin.editUser')" width="min(440px,calc(100vw - 32px))" :close-on-click-modal="false"><p>{{ userForm.username }}</p><el-form label-position="top"><el-form-item :label="$t('admin.role')"><el-select v-model="userForm.role"><el-option value="USER" :label="$t('admin.user')" /><el-option value="ADMIN" :label="$t('admin.admin')" /></el-select></el-form-item><el-form-item :label="$t('admin.status')"><el-select v-model="userForm.status"><el-option value="ACTIVE" :label="$t('admin.active')" /><el-option value="DISABLED" :label="$t('admin.disabled')" /></el-select></el-form-item></el-form><template #footer><el-button @click="userDialog=false">{{ $t('common.cancel') }}</el-button><el-button type="primary" :loading="saving" @click="saveUser">{{ $t('common.confirm') }}</el-button></template></el-dialog>
    <el-dialog v-model="categoryDialog" :title="$t(categoryForm.id ? 'admin.editCategory' : 'admin.addCategory')" width="min(480px,calc(100vw - 32px))" :close-on-click-modal="false"><el-form label-position="top"><el-form-item :label="$t('bill.category')"><el-input v-model="categoryForm.name" maxlength="30" /></el-form-item><el-form-item :label="$t('bill.type')"><el-radio-group v-model="categoryForm.type" :disabled="!!categoryForm.id" @change="categoryForm.parentId=null"><el-radio :value="1">{{ $t('bill.expense') }}</el-radio><el-radio :value="2">{{ $t('bill.income') }}</el-radio></el-radio-group></el-form-item><el-form-item :label="$t('finance.parentCategory')"><el-select v-model="categoryForm.parentId" clearable><el-option v-for="c in parents" :key="c.id" :value="c.id" :label="c.name" /></el-select></el-form-item><el-form-item :label="$t('category.sortOrder')"><el-input-number v-model="categoryForm.sortOrder" :min="0" :max="999" /></el-form-item></el-form><template #footer><el-button @click="categoryDialog=false">{{ $t('common.cancel') }}</el-button><el-button type="primary" :loading="saving" @click="saveCategory">{{ $t('common.confirm') }}</el-button></template></el-dialog>
    <el-dialog v-model="dictionaryDialog" :title="$t('admin.editDictionary')" width="min(440px,calc(100vw - 32px))"><el-form label-position="top"><el-form-item :label="$t('admin.code')">{{ dictionaryForm.code }}</el-form-item><el-form-item :label="$t('admin.name')"><el-input v-model="dictionaryForm.name" maxlength="50" /></el-form-item><el-form-item><span class="switch-label">{{ $t('admin.enabled') }}</span><el-switch v-model="dictionaryForm.enabled" :aria-label="$t('admin.enabled')" /></el-form-item></el-form><template #footer><el-button @click="dictionaryDialog=false">{{ $t('common.cancel') }}</el-button><el-button type="primary" :loading="saving" @click="saveDictionary">{{ $t('common.confirm') }}</el-button></template></el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, ElSwitch } from 'element-plus'
import * as api from '../api/admin'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'
const { t } = useI18n(), tab = ref('users'), loading = ref(false), error = ref(false), saving = ref(false)
const users = ref([]), categories = ref([]), dictionaries = ref([]), logs = ref([])
const userQuery = reactive({ keyword: '', page: 1, size: 20 }), userTotal = ref(0), logPage = ref(1), logTotal = ref(0)
const settings = reactive({ siteName: '', registrationEnabled: true })
const userDialog = ref(false), categoryDialog = ref(false), dictionaryDialog = ref(false)
const userForm = reactive({}), categoryForm = reactive({}), dictionaryForm = reactive({})
const parents = computed(() => categories.value.filter(c => !c.parentId && c.type === categoryForm.type && c.id !== categoryForm.id))
let generation = 0
async function load() {
  const current = ++generation, section = tab.value; loading.value = true; error.value = false
  try {
    const res = await ({ users: () => api.getAdminUsers(userQuery), categories: api.getAdminCategories, dictionaries: api.getAdminDictionaries, logs: () => api.getAdminLogs({ page: logPage.value, size: 20 }), settings: api.getAdminSettings })[section]()
    if (current !== generation) return
    if (section === 'users') { users.value = res.data.records; userTotal.value = res.data.total }
    else if (section === 'logs') { logs.value = res.data.records; logTotal.value = res.data.total }
    else if (section === 'categories') categories.value = res.data
    else if (section === 'dictionaries') dictionaries.value = res.data
    else Object.assign(settings, res.data)
  } catch { if (current === generation) error.value = true } finally { if (current === generation) loading.value = false }
}
function search() { userQuery.page = 1; load() }
function editUser(row) { Object.assign(userForm, row); userDialog.value = true }
function editCategory(row) { Object.assign(categoryForm, row || { id: null, name: '', type: 1, parentId: null, sortOrder: 1, icon: 'other' }); categoryDialog.value = true }
function editDictionary(row) { Object.assign(dictionaryForm, row, { enabled: !!row.enabled }); dictionaryDialog.value = true }
async function save(action, close) { if (saving.value) return; saving.value = true; try { await action(); close?.(); ElMessage.success(t('admin.saved')); await load() } catch {} finally { saving.value = false } }
const saveUser = () => save(() => api.updateAdminUser(userForm.id, { role: userForm.role, status: userForm.status }), () => { userDialog.value = false })
const saveCategory = () => save(() => categoryForm.id ? api.updateAdminCategory(categoryForm) : api.addAdminCategory(categoryForm), () => { categoryDialog.value = false })
const saveDictionary = () => save(() => api.updateAdminDictionary(dictionaryForm.id, { name: dictionaryForm.name, enabled: dictionaryForm.enabled }), () => { dictionaryDialog.value = false })
const saveSettings = () => save(() => api.updateAdminSettings(settings), () => window.dispatchEvent(new Event('system:settings-changed')))
async function removeCategory(row) { try { await ElMessageBox.confirm(t('admin.deleteConfirm'), t('common.tip'), { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }); await save(() => api.deleteAdminCategory(row.id)) } catch {} }
onMounted(load)
</script>
<style scoped>
.admin-actions{display:flex;gap:12px;max-width:480px;margin:12px 0 20px}.admin-hint{font-size:13px;line-height:1.8;color:var(--text-secondary)}.settings-form{max-width:500px;padding:20px 0}.switch-label{margin-right:16px}.admin-page :deep(.el-pagination){margin-top:18px}.admin-page :deep(.el-table){margin-top:16px}.admin-page :deep(.el-select){width:100%}@media(max-width:600px){.admin-page :deep(.el-card__body){padding:14px}.admin-actions{flex-wrap:wrap}.admin-page :deep(.el-pagination){overflow-x:auto}.admin-page :deep(.el-tabs__nav-wrap){max-width:100%}}
</style>
