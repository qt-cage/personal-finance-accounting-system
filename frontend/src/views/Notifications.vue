<template>
  <div class="management-page">
    <PageHeader :title="$t('files.messages')" :description="$t('files.messageHint')"><template #actions><el-button :disabled="!unread || busy" @click="readAll">{{ $t('files.readAll') }}</el-button></template></PageHeader>
    <el-card shadow="never"><NotificationPreference /><p class="unread">{{ $t('files.unread', { count: unread }) }}</p><AppState v-if="busy" type="loading" compact :title="$t('common.loading')" /><AppState v-else-if="error" type="error" :title="$t('common.loadFailed')" :action-label="$t('common.retry')" @retry="load" /><el-empty v-else-if="!items.length" :description="$t('files.noMessages')" /><ul v-else class="messages"><li v-for="item in items" :key="item.id" :class="{ unread: !item.readFlag }"><p>{{ item.content }}</p><time>{{ item.createTime?.replace('T', ' ') }}</time></li></ul></el-card>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { ElEmpty } from 'element-plus'
import { getNotifications, readAllNotifications } from '../api/files'
import PageHeader from '../components/PageHeader.vue'
import AppState from '../components/AppState.vue'
import NotificationPreference from '../components/NotificationPreference.vue'
const items = ref([]), unread = ref(0), busy = ref(false), error = ref(false)
async function load() { busy.value = true; error.value = false; try { const res = await getNotifications(); items.value = res.data.items; unread.value = res.data.unread } catch { error.value = true } finally { busy.value = false } }
async function readAll() { busy.value = true; try { await readAllNotifications(); await load() } catch {} finally { busy.value = false } }
onMounted(load)
</script>
<style scoped>.messages{list-style:none;padding:0;margin:0}.messages li{border-top:1px solid var(--border-color);padding:16px 0;line-height:1.8}.messages p{margin:0}.messages time{font-size:12px;color:var(--text-secondary)}.unread{font-weight:600;color:var(--color-primary)}</style>
