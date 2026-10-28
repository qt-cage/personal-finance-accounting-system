<template>
  <div class="file-tools">
    <el-button :loading="exporting" @click="exportFile">{{ $t(exportOnly ? 'files.exportAll' : 'files.export') }}</el-button>
    <el-button v-if="!exportOnly" @click="openImport">{{ $t('files.import') }}</el-button>
  </div>
  <el-dialog v-model="exportVisible" :title="$t('files.exported')" width="min(460px,calc(100vw - 32px))">
    <p>{{ $t('files.expires') }}</p>
    <el-button type="primary" :loading="downloading" @click="downloadFile">{{ $t('files.download') }}</el-button>
  </el-dialog>
  <el-dialog v-model="importVisible" :title="$t('files.import')" width="min(800px,calc(100vw - 32px))" :close-on-click-modal="false">
    <p>{{ $t('files.hint') }}</p>
    <el-collapse>
      <el-collapse-item :title="$t('files.accounts')"><p class="reference-list">{{ accounts.map(a => `${a.id} · ${a.name}`).join(' / ') }}</p></el-collapse-item>
      <el-collapse-item :title="$t('files.categories')"><p class="reference-list">{{ categories.map(c => `${c.id} · ${c.name} (${t(c.type === 1 ? 'bill.expense' : 'bill.income')})`).join(' / ') }}</p></el-collapse-item>
    </el-collapse>
    <div class="file-tools import-actions">
      <el-button @click="template">{{ $t('files.template') }}</el-button>
      <label class="csv-picker"><span>{{ $t('files.choose') }}</span><input type="file" accept=".csv,text/csv" :disabled="importing || validating" @change="selectFile" /></label>
    </div>
    <el-alert v-if="preview" :title="$t('files.importSummary', preview)" :type="preview.errors ? 'warning' : 'success'" :closable="false" />
    <el-table v-if="preview" :data="preview.rows" max-height="300" class="preview-table">
      <el-table-column prop="row" :label="$t('files.row')" width="75" />
      <el-table-column :label="$t('bill.date')" min-width="115"><template #default="{ row }">{{ row.values[4] }}</template></el-table-column>
      <el-table-column :label="$t('bill.amount')" min-width="100"><template #default="{ row }">{{ row.values[3] }}</template></el-table-column>
      <el-table-column :label="$t('files.error')" min-width="240"><template #default="{ row }">{{ row.error || $t('files.valid') }}</template></el-table-column>
    </el-table>
    <template #footer>
      <el-button :disabled="importing" @click="importVisible=false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="importing" :disabled="!preview?.token || !!preview?.errors || validating" @click="confirm">{{ $t('files.confirm', { count: preview?.count || 0 }) }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElCollapse, ElCollapseItem } from 'element-plus'
import { exportBills, downloadExport, previewImport, confirmImport, importTemplate, saveDownload } from '../api/files'
import { getAccountList } from '../api/account'
import { getCategoryList } from '../api/category'
const props = defineProps({ filters: { type: Object, default: () => ({}) }, exportOnly: Boolean })
const emit = defineEmits(['imported'])
const { t } = useI18n()
const exporting = ref(false), downloading = ref(false), validating = ref(false), importing = ref(false)
const exportVisible = ref(false), importVisible = ref(false), preview = ref(null), exportToken = ref('')
const accounts = ref([]), categories = ref([])
async function exportFile() {
  exporting.value = true
  try { const res = await exportBills(props.filters); exportToken.value = res.data.token; exportVisible.value = true }
  catch { /* 请求层显示错误。 */ } finally { exporting.value = false }
}
async function downloadFile() {
  downloading.value = true
  try { saveDownload(await downloadExport(exportToken.value), '个人账单.xlsx'); exportVisible.value = false }
  catch { /* 可重新生成过期链接。 */ } finally { downloading.value = false }
}
async function openImport() {
  try {
    const [a, c] = await Promise.all([getAccountList(), getCategoryList()])
    accounts.value = a.data || []; categories.value = c.data || []; preview.value = null; importVisible.value = true
  } catch { /* 请求层显示错误。 */ }
}
async function template() { try { saveDownload(await importTemplate(), 'bill-template.csv') } catch {} }
async function selectFile(event) {
  const file = event.target.files?.[0]; event.target.value = ''; if (!file) return
  validating.value = true; preview.value = null
  try { preview.value = (await previewImport(file)).data } catch {} finally { validating.value = false }
}
async function confirm() {
  if (importing.value || validating.value || !preview.value?.token) return
  importing.value = true
  try {
    const res = await confirmImport(preview.value.token)
    ElMessage.success(t('files.imported', { count: res.data.imported })); importVisible.value = false; emit('imported')
  } catch {} finally { importing.value = false }
}
</script>
<style scoped>
.file-tools{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.file-tools .el-button+.el-button{margin-left:0}.import-actions{margin:18px 0}.reference-list{line-height:1.8;overflow-wrap:anywhere}.csv-picker{display:inline-flex;flex-direction:column;gap:8px;font-size:13px;color:var(--text-secondary)}.csv-picker input{max-width:100%}.preview-table{margin-top:16px}
</style>
