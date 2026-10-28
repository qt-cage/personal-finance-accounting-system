<template>
  <el-dialog v-model="visible" :title="$t('files.receipts')" width="min(650px,calc(100vw - 32px))" @closed="clear">
    <p>{{ $t('files.receiptHint') }}</p>
    <label class="receipt-picker">{{ $t('files.upload') }}<input type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" :disabled="busy || items.length >= 5" @change="upload" /></label>
    <AppState v-if="error" type="error" :title="$t('files.previewFailed')" :action-label="$t('common.retry')" @retry="load" />
    <AppState v-else-if="busy" type="loading" compact :title="$t('common.loading')" />
    <el-empty v-else-if="!items.length" :description="$t('files.noReceipts')" />
    <div v-else class="receipts">
      <figure v-for="item in items" :key="item.id"><a :href="item.url" target="_blank" rel="noopener"><img :src="item.url" :alt="item.filename" /></a><figcaption>{{ item.filename }}</figcaption><el-button type="danger" text @click="remove(item)">{{ $t('files.remove') }}</el-button></figure>
    </div>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox, ElEmpty } from 'element-plus'
import { listAttachments, getAttachment, uploadAttachment, deleteAttachment } from '../api/files'
import AppState from './AppState.vue'
const props = defineProps({ modelValue: Boolean, billId: Number })
const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const items = ref([]), busy = ref(false), error = ref(false)
let generation = 0
function clear() { generation++; items.value.forEach(item => URL.revokeObjectURL(item.url)); items.value = [] }
async function load() {
  clear(); const current = generation; busy.value = true; error.value = false
  const urls = []
  try {
    const list = (await listAttachments(props.billId)).data || []
    for (const item of list) { item.url = URL.createObjectURL(await getAttachment(props.billId, item.id)); urls.push(item.url) }
    if (current === generation) items.value = list; else urls.forEach(url => URL.revokeObjectURL(url))
  } catch { urls.forEach(url => URL.revokeObjectURL(url)); if (current === generation) error.value = true }
  finally { if (current === generation) busy.value = false }
}
watch(() => props.modelValue, value => { if (value) load(); else clear() })
onBeforeUnmount(clear)
async function upload(event) { const file = event.target.files?.[0]; event.target.value = ''; if (!file || busy.value) return; busy.value = true; try { await uploadAttachment(props.billId, file); await load() } catch {} finally { busy.value = false } }
async function remove(item) { try { await ElMessageBox.confirm(t('files.removeConfirm'), t('common.tip'), { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }); await deleteAttachment(props.billId, item.id); await load() } catch {} }
</script>
<style scoped>
.receipt-picker{display:flex;flex-direction:column;gap:8px;font-size:13px;margin-bottom:20px}.receipt-picker input{max-width:100%}.receipts{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px}.receipts figure{margin:0;padding:12px;border:1px solid var(--border-color);border-radius:8px}.receipts img{width:100%;height:180px;object-fit:contain}.receipts figcaption{font-size:12px;overflow-wrap:anywhere;color:var(--text-secondary)}
</style>
