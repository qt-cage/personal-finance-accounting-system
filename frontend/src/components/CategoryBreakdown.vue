<template>
  <el-card shadow="never" class="breakdown">
    <template #header><strong>{{ title }}</strong></template>
    <div v-if="!ordered.length" class="empty-breakdown">
      <p>{{ emptyTitle }}</p>
      <el-button size="small" @click="$emit('create')">{{ $t('bill.addBill') }}</el-button>
      <el-button size="small" text @click="$emit('previous')">{{ $t('dashboard.lastMonth') }}</el-button>
    </div>
    <ul v-else>
      <li v-for="row in visibleRows" :key="row.categoryId">
        <div class="row-heading"><span class="category-name">{{ row.categoryName }}</span>
          <strong>¥{{ formatAmount(row.total) }}</strong><span class="ratio">{{ row.percent.toFixed(1) }}%</span></div>
        <div class="bar" aria-hidden="true"><span :class="tone" :style="{ width: `${row.percent}%` }"></span></div>
      </li>
    </ul>
    <button v-if="ordered.length > 6" class="expand-list" :aria-expanded="expanded" @click="expanded = !expanded">
      {{ $t(expanded ? 'dashboard.collapseCategories' : 'dashboard.expandCategories', { count: ordered.length }) }}
    </button>
  </el-card>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { formatAmount } from '../utils/format'
import { buildCategoryBreakdown } from '../utils/categoryBreakdown'
const props = defineProps({ title: String, emptyTitle: String, rows: { type: Array, default: () => [] }, tone: { type: String, default: 'success' } })
defineEmits(['create', 'previous'])
const expanded = ref(false)
const ordered = computed(() => buildCategoryBreakdown(props.rows))
const visibleRows = computed(() => expanded.value ? ordered.value : ordered.value.slice(0, 6))
watch(() => props.rows, () => { expanded.value = false })
</script>
<style scoped>
.breakdown { min-width:0; }
.breakdown :deep(.el-card__header) { font-size:14px; padding:16px 20px; }
.breakdown :deep(.el-card__body) { padding:4px 20px 16px; }
ul { list-style:none; margin:0; padding:0; }
li { padding:16px 0 0; }
.row-heading { display:flex; align-items:baseline; gap:12px; font-size:13px; }
.category-name { flex:1; min-width:0; overflow-wrap:anywhere; }
strong, .ratio { font-variant-numeric:tabular-nums; }
.row-heading strong { white-space:nowrap; }
.ratio { min-width:44px; text-align:right; color:var(--text-secondary); font-size:12px; }
.bar { margin-top:9px; height:4px; background:var(--border-lighter); border-radius:3px; overflow:hidden; }
.bar span { display:block; height:100%; }
.bar .danger { background:var(--color-danger); }
.bar .success { background:var(--color-success); }
.empty-breakdown { padding:14px 0 4px; color:var(--text-secondary); font-size:14px; }
.expand-list { margin-top:18px; padding:4px 0; border:0; background:none; color:var(--color-primary); cursor:pointer; font:inherit; font-size:13px; }
.expand-list:focus-visible { outline:2px solid var(--color-primary); outline-offset:4px; }
@media(max-width:420px) { .row-heading { flex-wrap:wrap; gap:6px; } .category-name { flex-basis:100%; } .ratio { margin-left:auto; } }
</style>
