<template><div class="notification-preference"><div class="preference-control"><span>{{ $t('files.enabled') }}</span><el-switch v-model="enabled" :aria-label="$t('files.enabled')" :disabled="busy || failed" :loading="busy" @change="save" /></div><p>{{ $t('files.preferenceHint') }}</p><el-button v-if="failed" text @click="load">{{ $t('common.retry') }}</el-button></div></template>
<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElSwitch } from 'element-plus'
import { getNotifications, notificationPreference } from '../api/files'
const { t } = useI18n(), enabled = ref(true), busy = ref(false), failed = ref(false)
async function load() { busy.value = true; try { enabled.value = (await getNotifications()).data.enabled; failed.value = false } catch { failed.value = true } finally { busy.value = false } }
async function save(value) { busy.value = true; try { await notificationPreference(value); ElMessage.success(t('files.saved')) } catch { enabled.value = !value } finally { busy.value = false } }
onMounted(load)
</script>
<style scoped>.preference-control{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:14px}p{font-size:12px;color:var(--text-secondary);line-height:1.6}</style>
