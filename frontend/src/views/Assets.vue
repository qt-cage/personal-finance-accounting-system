<template>
  <div class="management-page assets-page">
    <PageHeader :title="$t('planning.assetsTitle')" :description="$t('planning.assetsSubtitle')" :stamp="data ? $t('finance.asOf',{date:data.asOfDate}) : ''">
      <template #actions><el-button :loading="loading" @click="load">{{ $t('common.query') }}</el-button></template>
    </PageHeader>
    <AppState v-if="loading && !data" type="loading" :title="$t('common.loading')" />
    <AppState v-else-if="error" type="error" :title="$t('common.loadFailed')" :description="error" :action-label="$t('common.retry')" @retry="load" />
    <template v-else-if="data">
      <MetricStrip :items="metrics" />
      <el-alert v-if="data.unassignedBillCount" :title="$t('planning.unassigned',{count:data.unassignedBillCount})" type="warning" :closable="false" class="assets-alert" />
      <div class="assets-charts">
        <el-card shadow="never"><template #header>{{ $t('planning.balanceChart') }}</template>
          <AppState v-if="!data.accounts.length" type="empty" compact :title="$t('finance.noAccounts')" :action-label="$t('finance.createFirst')" @retry="$router.push('/account')" />
          <div v-else ref="balanceEl" class="asset-chart" />
        </el-card>
        <el-card shadow="never"><template #header>{{ $t('planning.flowChart') }}</template><div ref="flowEl" class="asset-chart" /></el-card>
      </div>
      <p class="assets-hint">{{ $t('planning.assetHint') }}</p>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getAssetSnapshot } from '../api/planning'
import { formatAmount } from '../utils/format'
import { escapeHtml } from '../utils/html'
import * as echarts from '../utils/chartRuntime'
import PageHeader from '../components/PageHeader.vue'
import MetricStrip from '../components/MetricStrip.vue'
import AppState from '../components/AppState.vue'

const { t, locale }=useI18n()
const data=ref(null), loading=ref(false), error=ref(''), balanceEl=ref(), flowEl=ref()
let balanceChart, flowChart, resizeObserver, themeObserver
const metrics=computed(() => ['assets','liabilities','netAssets','cashflowNet'].map((key,index) => ({
  key, label:t(['planning.totalAssets','planning.totalLiabilities','planning.netAssets','planning.cashflow'][index]),
  value:'¥'+formatAmount(data.value?.[key]), tone: key==='liabilities' || Number(data.value?.[key])<0 ? 'danger' : 'primary'
})))
const color = key => getComputedStyle(document.documentElement).getPropertyValue(key).trim()

function render() {
  if (!data.value) return
  balanceChart?.dispose(); flowChart?.dispose(); balanceChart=undefined; flowChart=undefined
  const ink=color('--text-secondary'), grid=color('--border-light'), positive=color('--color-primary'), negative=color('--color-danger')
  if (balanceEl.value) {
    balanceChart ||= echarts.init(balanceEl.value)
    balanceChart.setOption({ tooltip:{trigger:'axis', formatter:items=>items.map(item=>`${escapeHtml(data.value.accounts[item.dataIndex].name)}: ¥${formatAmount(data.value.accounts[item.dataIndex].balance)}`).join('<br/>')}, grid:{left:60,right:20,bottom:75,top:20},
      xAxis:{type:'category',data:data.value.accounts.map(a=>a.name),axisLabel:{color:ink,rotate:25,overflow:'truncate',width:80}},
      yAxis:{type:'value',axisLabel:{color:ink},splitLine:{lineStyle:{color:grid}}},
      series:[{type:'bar',barMaxWidth:36,data:data.value.accounts.map(a=>({value:Number(a.balance),itemStyle:{color:Number(a.balance)<0?negative:positive}}))}] })
  }
  if (flowEl.value) {
    flowChart ||= echarts.init(flowEl.value)
    flowChart.setOption({ tooltip:{trigger:'axis', formatter:items=>items.map(item=>`${escapeHtml(item.seriesName)}: ¥${formatAmount(data.value.dailyFlow[item.dataIndex][item.seriesIndex===0?'income':'expense'])}`).join('<br/>')}, legend:{top:0,left:'center',data:[t('bill.income'),t('bill.expense')],textStyle:{color:ink}},
      grid:{left:55,right:16,bottom:45,top:45}, xAxis:{type:'category',data:data.value.dailyFlow.map(row=>row.day),axisLabel:{color:ink,hideOverlap:true,showMaxLabel:true,formatter:value=>value.slice(5)}},
      yAxis:{type:'value',axisLabel:{color:ink},splitLine:{lineStyle:{color:grid}}},
      series:[{name:t('bill.income'),type:'line',data:data.value.dailyFlow.map(row=>Number(row.income)),itemStyle:{color:positive}},
        {name:t('bill.expense'),type:'line',data:data.value.dailyFlow.map(row=>Number(row.expense)),itemStyle:{color:negative}}] })
  }
  resizeObserver?.disconnect()
  if (balanceEl.value) resizeObserver?.observe(balanceEl.value)
  if (flowEl.value) resizeObserver?.observe(flowEl.value)
}
async function load() {
  if (loading.value) return
  loading.value=true; error.value=''
  try { data.value=(await getAssetSnapshot()).data; await nextTick(); render() }
  catch { error.value=t('common.loadFailedDescription') }
  finally { loading.value=false }
}
watch(locale,render)
onMounted(async () => {
  resizeObserver=new ResizeObserver(() => { balanceChart?.resize(); flowChart?.resize() })
  themeObserver=new MutationObserver(render)
  themeObserver.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']})
  await load()
})
onBeforeUnmount(() => { resizeObserver?.disconnect(); themeObserver?.disconnect(); balanceChart?.dispose(); flowChart?.dispose() })
</script>

<style scoped>
.assets-charts { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px; }
.asset-chart { width:100%; height:300px; }
.assets-alert { margin-bottom:20px; }
.assets-hint { color:var(--text-secondary); font-size:13px; line-height:1.7; margin-top:20px; }
@media(max-width:900px) { .assets-charts { grid-template-columns:1fr; } }
</style>
