<template>
  <div class="analysis-page stats-page">
    <PageHeader :title="$t('nav.stats')" :description="$t('stats.subtitle')" />
    <!-- ============================================================
         顶部一级Tab切换：图表分析 / 汇总报表
         ============================================================ -->
    <el-tabs v-model="activeTab" class="page-tabs" @tab-change="onTabChange">
      <el-tab-pane :label="$t('stats.chartAnalysis')" name="chart" />
      <el-tab-pane :label="$t('stats.reportTable')" name="table" />
    </el-tabs>

    <!-- =============================
         图表分析 Tab
         ============================= -->
    <div v-show="activeTab === 'chart'">
      <!-- 第一层：筛选栏 -->
      <el-card shadow="never" class="filter-card">
        <div class="filter-bar">
          <div class="filter-controls">
            <!-- 统计维度切换：月度/年度 -->
            <el-radio-group v-model="dimType" size="default" :disabled="chartLoading" @change="onDimChange">
              <el-radio-button value="month">{{ $t('stats.monthStats') }}</el-radio-button>
              <el-radio-button value="year">{{ $t('stats.yearStats') }}</el-radio-button>
            </el-radio-group>
            <!-- 月度筛选 -->
            <template v-if="dimType === 'month'">
              <el-select v-model="quickType" :placeholder="$t('stats.timeType')" :disabled="chartLoading" @change="onQuickChange" class="quick-select">
                <el-option :label="$t('stats.quickMonth')" value="month" />
                <el-option :label="$t('stats.quickWeek')" value="week" />
                <el-option :label="$t('stats.quickQuarter')" value="quarter" />
              </el-select>
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                :range-separator="$t('bill.to')"
                :start-placeholder="$t('bill.startDate')"
                :end-placeholder="$t('bill.endDate')"
                value-format="YYYY-MM-DD"
                class="date-picker"
                :disabled="chartLoading"
                @change="onDateChange"
              />
            </template>
            <!-- 年度筛选 -->
            <template v-if="dimType === 'year'">
              <el-select v-model="yearVal" class="quick-select" :disabled="chartLoading" @change="loadYearStats">
                <el-option v-for="y in yearOptions" :key="y" :label="$t('stats.yearLabel', { year: y })" :value="y" />
              </el-select>
            </template>
            <el-button type="primary" :loading="chartLoading" @click="handleQuery">{{ $t('common.query') }}</el-button>
          </div>
        </div>
      </el-card>

      <AppState
        v-if="chartLoading"
        type="loading"
        :title="$t('common.loading')"
        :rows="8"
      />
      <AppState
        v-else-if="chartError"
        type="error"
        :title="$t('common.loadFailed')"
        :description="$t('common.loadFailedDescription')"
        :action-label="$t('common.retry')"
        @retry="retryChartLoad"
      />
      <template v-else>
        <MetricStrip :items="metricItems" />

      <!-- 第三层：收支趋势折线图 -->
        <el-row :gutter="20" style="margin-bottom: 24px">
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">{{ $t('stats.expenseChart') }}</span></template>
            <div ref="expenseLineRef" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">{{ $t('stats.incomeChart') }}</span></template>
            <div ref="incomeLineRef" class="chart-container"></div>
          </el-card>
        </el-col>
        </el-row>

      <!-- 第四层：收支分类构成环形图 -->
        <el-row :gutter="20" style="margin-bottom: 24px">
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">{{ $t('stats.expenseCategoryRatio') }}</span></template>
            <div ref="expensePieRef" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="chart-card">
            <template #header><span class="chart-title">{{ $t('stats.incomeCategoryRatio') }}</span></template>
            <div ref="incomePieRef" class="chart-container"></div>
          </el-card>
        </el-col>
        </el-row>
      </template>
    </div>

    <!-- =============================
         汇总报表 Tab
         ============================= -->
    <div v-show="activeTab === 'table'">
      <el-card shadow="never" class="filter-card">
        <div class="filter-bar">
          <div class="filter-controls">
            <el-date-picker
              v-model="reportRange"
              type="daterange"
              :range-separator="$t('bill.to')"
              :start-placeholder="$t('bill.startDate')"
              :end-placeholder="$t('bill.endDate')"
              value-format="YYYY-MM-DD"
              class="date-picker"
              :disabled="reportLoading"
            />
            <el-button type="primary" :loading="reportLoading" @click="loadReportData">{{ $t('common.query') }}</el-button>
          </div>
        </div>
      </el-card>

      <AppState
        v-if="reportLoading"
        type="loading"
        :title="$t('common.loading')"
        :rows="7"
      />
      <AppState
        v-else-if="reportError"
        type="error"
        :title="$t('common.loadFailed')"
        :description="$t('common.loadFailedDescription')"
        :action-label="$t('common.retry')"
        @retry="loadReportData"
      />
      <el-card shadow="never" class="table-card" v-else-if="reportData.length > 0">
        <el-table :data="reportData" stripe class="report-table" :summary-method="reportSummary" show-summary>
          <el-table-column prop="month" :label="$t('stats.month')" width="140" align="center" />
          <el-table-column prop="income" :label="$t('stats.income')" width="160" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.income) }}</template>
          </el-table-column>
          <el-table-column prop="expense" :label="$t('stats.expense')" width="160" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.expense) }}</template>
          </el-table-column>
          <el-table-column prop="balance" :label="$t('stats.balance')" width="160" align="right">
            <template #default="{ row }">
              <span :style="{ color: row.balance >= 0 ? 'var(--color-success)' : 'var(--color-danger)' }">
                ¥{{ formatAmount(row.balance) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="momRatio" :label="$t('stats.momRatio')" width="140" align="right">
            <template #default="{ row }">
              <span v-if="row.momRatio != null" :style="{ color: row.momRatio >= 0 ? 'var(--color-danger)' : 'var(--color-success)' }">
                {{ row.momRatio >= 0 ? '+' : '' }}{{ row.momRatio.toFixed(2) }}%
              </span>
              <span v-else style="color: var(--text-secondary)">--</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
      <AppState
        v-else
        type="empty"
        :title="$t('common.noData')"
        :description="$t('common.noDataDescription')"
      />
    </div>
  </div>

</template>

<script setup>
/**
 * ============================================================
 * 统计分析页 - 年度对比 + 汇总报表扩展
 *
 * 新增功能：
 * 1. 顶部一级Tab：图表分析 / 汇总报表
 * 2. 图表分析新增「年度统计」维度，含同比增长率
 * 3. 汇总报表：按月展示收入/支出/结余/环比
 * 所有图表样式、配色、居中配置零改动
 * ============================================================
 */
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from '../utils/chartRuntime'
import { getMonthStats, getStatistics, getCategoryStats, getYearStats, getMonthSummary } from '../api/bill'
import { formatAmount } from '../utils/format'
import { getCurrentWeekRange } from '../utils/businessDate'
import { subtractAmounts, sumAmounts } from '../utils/money'
import { buildMonthlyReport, summarizeMonthlyReport } from '../utils/monthlyReport'
import PageHeader from '../components/PageHeader.vue'
import MetricStrip from '../components/MetricStrip.vue'
import AppState from '../components/AppState.vue'

const { t, locale } = useI18n()

/* ---- 一级Tab ---- */
const activeTab = ref('chart')

/* ---- 维度切换 ---- */
const dimType = ref('month')
const yearVal = ref(new Date().getFullYear())
const yearOptions = computed(() => {
  const cur = new Date().getFullYear()
  return [cur, cur - 1, cur - 2, cur - 3, cur - 4]
})

/* ---- 月度筛选 ---- */
const quickType = ref('month')
const dateRange = ref(null)
const activeRange = reactive({ start: '', end: '' })

/* ---- 汇总报表 ---- */
const reportRange = ref(null)
const reportData = ref([])
const reportLoading = ref(false)
const reportError = ref('')

/* ---- 数据 ---- */
const summary = reactive({ totalExpense: 0, totalIncome: 0, netBalance: 0, dailyAvg: 0, yoyExpense: null, yoyIncome: null })

const metricItems = computed(() => [
  {
    key: 'expense',
    label: t('stats.totalExpense'),
    value: `¥${formatAmount(summary.totalExpense)}`,
    tone: 'danger',
    caption: dimType.value === 'year' && summary.yoyExpense != null
      ? `${t('stats.yoyGrowth')} ${summary.yoyExpense >= 0 ? '+' : ''}${summary.yoyExpense.toFixed(2)}%`
      : t('stats.periodExpenseCaption')
  },
  {
    key: 'income',
    label: t('stats.totalIncome'),
    value: `¥${formatAmount(summary.totalIncome)}`,
    tone: 'success',
    caption: dimType.value === 'year' && summary.yoyIncome != null
      ? `${t('stats.yoyGrowth')} ${summary.yoyIncome >= 0 ? '+' : ''}${summary.yoyIncome.toFixed(2)}%`
      : t('stats.periodIncomeCaption')
  },
  {
    key: 'balance',
    label: t('stats.netBalance'),
    value: `¥${formatAmount(summary.netBalance)}`,
    tone: summary.netBalance >= 0 ? 'success' : 'danger',
    caption: summary.netBalance >= 0 ? t('stats.balanceHealthy') : t('stats.balanceNegative')
  },
  {
    key: 'daily',
    label: t('stats.dailyAvg'),
    value: `¥${formatAmount(summary.dailyAvg)}`,
    tone: 'neutral',
    caption: t('stats.dailyAvgCaption')
  }
])
const expenseLineData = ref([])
const incomeLineData = ref([])
const expensePieData = ref([])
const incomePieData = ref([])
const expenseLineLastData = ref([])
const incomeLineLastData = ref([])
const chartLoading = ref(false)
const chartError = ref('')

/* ---- 图表ref ---- */
const expenseLineRef = ref()
const incomeLineRef = ref()
const expensePieRef = ref()
const incomePieRef = ref()

/* ---- 图表实例 ---- */
let expenseLineChart = null
let incomeLineChart = null
let expensePieChart = null
let incomePieChart = null
let resizeTimer = null

const CHART_PALETTES = {
  light: {
    expense: ['#ad6846', '#c27b55', '#d0926d', '#b58a70', '#d5aa86', '#9f7862'],
    income: ['#2d5a4a', '#467260', '#648b75', '#7ea18a', '#9ab4a0', '#b5c8b9']
  },
  dark: {
    expense: ['#c88964', '#b9795a', '#d29a78', '#a77965', '#d8ad8d', '#8f6d5d'],
    income: ['#789078', '#879c82', '#98a990', '#6e8872', '#a9b7a0', '#5f7967']
  },
  'eye-care': {
    expense: ['#a96849', '#ba7b59', '#c78f6a', '#a7836c', '#cfa783', '#927262'],
    income: ['#4a684e', '#5b7a5e', '#708b72', '#829b83', '#9aac98', '#afbdab']
  }
}

function currentTheme() {
  return document.documentElement.getAttribute('data-theme') || 'light'
}

function getColors(type) {
  return (CHART_PALETTES[currentTheme()] || CHART_PALETTES.light)[type]
}

function getLineColor(type) {
  return getColors(type)[0]
}

function readThemeToken(name, fallback) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

/** 月度日期范围（防御性兜底，永不生成 -31 非法日期） */
function calcDateRange(type) {
  const now = new Date(); const y = now.getFullYear(); const m = now.getMonth() + 1
  let s, e
  if (type === 'week') { const range = getCurrentWeekRange(now); s = range.start; e = range.end }
  else if (type === 'quarter') { const q = Math.ceil(m/3); const endM = q*3; s = y+'-'+String((q-1)*3+1).padStart(2,'0')+'-01'; const lastDay = new Date(y, endM, 0).getDate(); e = y+'-'+String(endM).padStart(2,'0')+'-'+String(lastDay).padStart(2,'0') }
  else { const lastDay = new Date(y, m, 0).getDate(); s = y+'-'+String(m).padStart(2,'0')+'-01'; e = y+'-'+String(m).padStart(2,'0')+'-'+String(lastDay).padStart(2,'0') }
  return { start: s, end: e }
}

/** 计算日均 */
function calcDailyAvg(total, s, e) {
  if (total === 0||!s||!e) return 0
  const days = Math.max(1, Math.floor((new Date(e)-new Date(s))/(86400000))+1)
  return total / days
}

// ============================================================
// 折线图渲染（含年度统计双线对比）
// ============================================================
function renderLine(el, data, lastData, type) {
  if (!el) return
  if (type === 'expense') { if (expenseLineChart) { expenseLineChart.dispose(); expenseLineChart=null } expenseLineChart = echarts.init(el) }
  else { if (incomeLineChart) { incomeLineChart.dispose(); incomeLineChart=null } incomeLineChart = echarts.init(el) }
  const chart = type === 'expense' ? expenseLineChart : incomeLineChart
  const lineColor = getLineColor(type)
  const hasData = data && data.length > 0
  const isYear = dimType.value === 'year'
  const valueKey = type === 'expense' ? 'expense' : 'income'
  
  const months = isYear ? Array.from({length:12}, (_,i)=>String(i+1).padStart(2,'0')) : (hasData ? data.map(d=>d.month) : [])
  const amounts = isYear ? months.map(m => { const f = data.find(d=>d.month?.endsWith('-'+m)); return f ? parseFloat(f[valueKey]||f.total_amount||0) : 0 }) : (hasData ? data.map(d=>parseFloat(d.total_amount||0)) : [])
  const lastAmounts = isYear && lastData ? months.map(m => { const f = lastData.find(d=>d.month?.endsWith('-'+m)); return f ? parseFloat(f[valueKey]||f.total_amount||0) : 0 }) : []
  const hasLast = isYear && lastAmounts.some(v=>v>0)

  const tooltipBg = readThemeToken('--chart-tooltip-bg', '#242821')
  const tooltipText = readThemeToken('--text-inverse', '#ffffff')
  const tooltipBorder = readThemeToken('--chart-tooltip-border', 'rgba(44,48,40,0.12)')
  const axisColor = readThemeToken('--chart-axis', '#74766e')
  const gridColor = readThemeToken('--chart-grid', 'rgba(44,48,40,0.06)')
  const emptyColor = readThemeToken('--chart-empty', '#d4d4d0')

  const option = {
    tooltip: {
      trigger: hasData ? 'axis' : 'none',
      backgroundColor: tooltipBg, borderRadius: 6, padding: [8, 12],
      borderColor: tooltipBorder,
      borderWidth: 1,
      textStyle: { fontSize: 13, color: tooltipText },
      extraCssText: 'box-shadow: 0 8px 24px rgba(0,0,0,0.14);',
      formatter: function(ps) {
        if (!ps||ps.length===0) return ''
        const idx = ps[0].dataIndex
        const m = months[idx]
        let html = m + '<br/>'
        ps.forEach(p => { html += p.marker + ' ' + p.seriesName + ': ¥' + formatAmount(p.value) + '<br/>' })
        if (hasLast && lastAmounts[idx] != null) {
          const cur = amounts[idx] || 0
          const prv = lastAmounts[idx] || 0
          const yoy = prv > 0 ? ((cur - prv) / prv * 100) : 0
          html += '同比: ' + (yoy >= 0 ? '+' : '') + yoy.toFixed(2) + '%'
        }
        return html
      }
    },
    grid: { left: 60, right: 20, top: 20, bottom: 40, containLabel: true },
    xAxis: { type:'category', data: months, axisLine:{show:false}, axisTick:{show:false}, axisLabel:{color:axisColor,fontSize:12} },
    yAxis: { type:'value', axisLabel:{color:axisColor,fontSize:12,formatter:v=>'¥'+formatAmount(v)}, splitLine:{lineStyle:{color:gridColor,type:'dashed',width:1}} },
    series: [
      hasData ? {
        name: t('stats.thisYearData'), data: amounts, type: 'line', smooth: true,
        lineStyle: { width: 3, color: lineColor },
        areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:lineColor+'40'},{offset:1,color:lineColor+'08'}]) },
        symbol: 'circle', symbolSize: 6,
        emphasis: { itemStyle: { scale: 1.5 } },
        itemStyle: { color: lineColor }
      } : null,
      hasLast ? {
        name: t('stats.lastYearData'), data: lastAmounts, type: 'line', smooth: true,
        lineStyle: { width: 2, color: lineColor, type: 'dashed', opacity: 0.5 },
        symbol: 'circle', symbolSize: 4,
        itemStyle: { color: lineColor, opacity: 0.5 }
      } : null
    ].filter(Boolean),
    title: hasData ? undefined : { text: t('stats.noData'), left:'center', top:'center', textStyle:{color:emptyColor,fontSize:14,fontWeight:400} }
  }
  chart.setOption(option)
  chart.resize()
}

// ============================================================
// 环形图渲染（复用首页标准配置）
// ============================================================
function renderPie(el, data, type, totalSum) {
  if (!el) return
  if (type === 'expense') { if (expensePieChart) { expensePieChart.dispose(); expensePieChart=null } expensePieChart = echarts.init(el) }
  else { if (incomePieChart) { incomePieChart.dispose(); incomePieChart=null } incomePieChart = echarts.init(el) }
  const chart = type === 'expense' ? expensePieChart : incomePieChart
  const colors = getColors(type)
  const hasData = data && data.length > 0
  const titleText = type === 'expense' ? t('stats.totalExpense') : t('stats.totalIncome')
  const emptyText = t('stats.noData')
  const tooltipBg = readThemeToken('--chart-tooltip-bg', '#242821')
  const tooltipText = readThemeToken('--text-inverse', '#ffffff')
  const legendColor = readThemeToken('--text-secondary', '#74766e')
  const borderColor = readThemeToken('--bg-card', '#fbfaf6')
  const tooltipBorder = readThemeToken('--chart-tooltip-border', 'rgba(44,48,40,0.12)')
  const emptyColor = readThemeToken('--chart-empty', '#d4d4d0')
  const centerAmountColor = readThemeToken('--text-primary', '#242821')
  const centerTitleColor = readThemeToken('--text-secondary', '#74766e')
  const compact = el.clientWidth < 520

  const seriesData = hasData ? data.map((item,idx)=>({
    name:item.category_name,
    value:parseFloat(item.total_amount),
    realValue:parseFloat(item.total_amount),
    itemStyle:{color:colors[idx%colors.length], borderWidth:2, borderColor:borderColor}
  })) : [{name:emptyText,value:1,itemStyle:{color:emptyColor,borderWidth:0}}]
  
  const option = {
    tooltip: { 
      show:true, trigger:'item', triggerOn:'mousemove', zlevel:9999, confine:true,
      backgroundColor:tooltipBg, borderColor:tooltipBorder, borderWidth:1,
      borderRadius:6, padding:[8,12], textStyle:{fontSize:13,color:tooltipText}, 
      formatter:p => `${p.name}  ¥${formatAmount(p.value)}  ${Number(p.percent || 0).toFixed(1)}%`
    },
    legend: {
      orient: compact ? 'horizontal' : 'vertical',
      left: compact ? 'center' : 'auto',
      right: compact ? 'auto' : '2%',
      top: compact ? 'auto' : 'center',
      bottom: compact ? 0 : 'auto',
      itemWidth:10,
      itemHeight:10,
      itemGap: compact ? 10 : 12,
      textStyle:{fontSize:12,color:legendColor,overflow:'truncate',width: compact ? 110 : 150},
      formatter(name) {
        const source = data.find(item => item.category_name === name)
        if (!source || !totalSum) return name
        const amount = parseFloat(source.total_amount || 0)
        return `${name}  ¥${formatAmount(amount)} · ${((amount / totalSum) * 100).toFixed(1)}%`
      },
      tooltip:{show:true},
      show:hasData
    },
    series: [{
      type:'pie',
      radius: hasData ? (compact ? ['38%','55%'] : ['43%','62%']) : ['0%','0%'],
      center: compact ? ['50%','42%'] : ['34%','50%'],
      avoidLabelOverlap:false,
      silent:false,
      hoverAnimation:true,
      padAngle:hasData?0.8:0,
      label: hasData ? {
        show: true,
        position: 'center',
        align: 'center',
        verticalAlign: 'middle',
        formatter: `{amount|¥${formatAmount(totalSum)}}\n{title|${titleText}}`,
        rich: {
          amount: { fontSize: 23, fontWeight: 700, color: centerAmountColor, align: 'center', lineHeight: 31 },
          title: { fontSize: 12, color: centerTitleColor, align: 'center', lineHeight: 18 }
        }
      } : { show: false },
      labelLine:{show:false},
      data:seriesData,
      emphasis:{scale:true,scaleSize:4}
    }],
    title: hasData ? undefined : { text:emptyText, left:'center', top:'center', textStyle:{color:emptyColor,fontSize:14,fontWeight:400} },
    graphic: []
  }
  chart.setOption(option); chart.resize()
}

/** 渲染全部图表 */
function renderAllCharts() {
  if (!expenseLineRef.value || !incomeLineRef.value || !expensePieRef.value || !incomePieRef.value) return
  if (dimType.value === 'year') {
    renderLine(expenseLineRef.value, expenseLineData.value, expenseLineLastData.value, 'expense')
    renderLine(incomeLineRef.value, incomeLineData.value, incomeLineLastData.value, 'income')
  } else {
    renderLine(expenseLineRef.value, expenseLineData.value, null, 'expense')
    renderLine(incomeLineRef.value, incomeLineData.value, null, 'income')
  }
  const te = expensePieData.value.reduce((s,i)=>s+parseFloat(i.total_amount||0),0)
  const ti = incomePieData.value.reduce((s,i)=>s+parseFloat(i.total_amount||0),0)
  renderPie(expensePieRef.value, expensePieData.value, 'expense', te)
  renderPie(incomePieRef.value, incomePieData.value, 'income', ti)
}

/** 加载月度统计 */
async function loadStatsData() {
  let start = activeRange.start, end = activeRange.end
  if (!start) { const r = calcDateRange(quickType.value); start=r.start; end=r.end; activeRange.start=start; activeRange.end=end }
  chartLoading.value = true
  chartError.value = ''
  try {
    const [resExp, resInc, statsRes, catExp, catInc] = await Promise.all([
      getMonthStats({ type:1, startDate:start, endDate:end }),
      getMonthStats({ type:2, startDate:start, endDate:end }),
      getStatistics({ startDate:start, endDate:end }),
      getCategoryStats({ type:1, startDate:start, endDate:end }),
      getCategoryStats({ type:2, startDate:start, endDate:end })
    ])
    expenseLineData.value = resExp.data||[]; incomeLineData.value = resInc.data||[]
    const s = statsRes.data||{}
    summary.totalExpense = parseFloat(s.expense||0); summary.totalIncome = parseFloat(s.income||0)
    summary.netBalance = parseFloat(s.balance||0); summary.dailyAvg = calcDailyAvg(summary.totalExpense, start, end)
    summary.yoyExpense = null; summary.yoyIncome = null
    expensePieData.value = catExp.data||[]; incomePieData.value = catInc.data||[]
    expenseLineLastData.value = []; incomeLineLastData.value = []
  } catch(e) {
    chartError.value = t('common.loadFailedDescription')
  } finally {
    chartLoading.value = false
    await nextTick()
    if (!chartError.value) renderAllCharts()
  }
}

/** 加载年度统计（含去年同比） */
async function loadYearStats() {
  const y = yearVal.value
  chartLoading.value = true
  chartError.value = ''
  try {
    const [curRes, lastRes] = await Promise.all([
      getYearStats({ year: y }),
      getYearStats({ year: y - 1 })
    ])
    expenseLineData.value = curRes.data||[]
    incomeLineData.value = curRes.data||[]
    expenseLineLastData.value = lastRes.data||[]
    incomeLineLastData.value = lastRes.data||[]

    // 计算汇总
    const curExp = sumAmounts(expenseLineData.value.map(item => item.expense))
    const curInc = sumAmounts(incomeLineData.value.map(item => item.income))
    const lastExp = sumAmounts(expenseLineLastData.value.map(item => item.expense))
    const lastInc = sumAmounts(incomeLineLastData.value.map(item => item.income))
    summary.totalExpense = curExp; summary.totalIncome = curInc
    summary.netBalance = subtractAmounts(curInc, curExp)
    summary.dailyAvg = calcDailyAvg(curExp, y+'-01-01', y+'-12-31')
    summary.yoyExpense = lastExp > 0 ? (curExp - lastExp) / lastExp * 100 : null
    summary.yoyIncome = lastInc > 0 ? (curInc - lastInc) / lastInc * 100 : null

    // 分类统计仍使用月度接口查全年
    const [catExp, catInc] = await Promise.all([
      getCategoryStats({ type:1, startDate:y+'-01-01', endDate:y+'-12-31' }),
      getCategoryStats({ type:2, startDate:y+'-01-01', endDate:y+'-12-31' })
    ])
    expensePieData.value = catExp.data||[]; incomePieData.value = catInc.data||[]
  } catch(e) {
    chartError.value = t('common.loadFailedDescription')
  } finally {
    chartLoading.value = false
    await nextTick()
    if (!chartError.value) renderAllCharts()
  }
}

/** 加载汇总报表 */
async function loadReportData() {
  let start, end
  if (reportRange.value) { start = reportRange.value[0]; end = reportRange.value[1] }
  else { const r = calcDateRange('month'); start=r.start; end=r.end; reportRange.value = [start, end] }
  reportLoading.value = true
  reportError.value = ''
  try {
    const res = await getMonthSummary({ startDate: start, endDate: end })
    reportData.value = buildMonthlyReport(res.data || [])
  } catch(e) {
    reportData.value = []
    reportError.value = t('common.loadFailedDescription')
  } finally {
    reportLoading.value = false
  }
}

/** 报表合计行 */
function reportSummary(param) {
  const { columns } = param
  const totals = summarizeMonthlyReport(reportData.value)
  return columns.map(col => {
    if (col.property === 'month') return t('common.summaryTotal')
    if (!(col.property in totals)) return ''
    return '¥' + formatAmount(totals[col.property])
  })
}

function onTabChange() { nextTick(() => { if (activeTab.value === 'table') loadReportData() }) }
function onDimChange(val) { if (val === 'year') loadYearStats(); else loadStatsData() }
function handleQuery() { if (dimType.value === 'year') loadYearStats(); else loadStatsData() }
function retryChartLoad() { handleQuery() }
function onQuickChange(val) { const r = calcDateRange(val); activeRange.start=r.start; activeRange.end=r.end; quickType.value=val; dateRange.value=null; loadStatsData() }
function onDateChange(val) { if (val) { activeRange.start=val[0]; activeRange.end=val[1]; quickType.value='' } }

let statsContainerObserver = null

function scheduleStatsRelayout() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    renderAllCharts()
  }, 120)
}

function onWindowResize() {
  scheduleStatsRelayout()
}

function setupStatsResizeObserver() {
  const observer = new ResizeObserver((entries) => {
    if (!entries.some(entry => entry.contentRect.width > 0)) return
    scheduleStatsRelayout()
  })
  ;[expenseLineRef.value, incomeLineRef.value, expensePieRef.value, incomePieRef.value]
    .filter(Boolean)
    .forEach(element => observer.observe(element))
  return observer
}

function disposeCharts() {
  if (resizeTimer) { clearTimeout(resizeTimer); resizeTimer=null }
  [expenseLineChart, incomeLineChart, expensePieChart, incomePieChart].forEach(c => { if (c) { c.dispose(); c=null } })
}

const stopStatsWatch = watch(locale, () => nextTick(() => renderAllCharts()))

let statsThemeObserver = null
function setupStatsThemeObserver() {
  const observer = new MutationObserver(() => {
    nextTick(() => renderAllCharts())
  })
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  })
  return observer
}

onMounted(async () => {
  const r = calcDateRange('month')
  activeRange.start=r.start; activeRange.end=r.end
  await nextTick(); await loadStatsData()
  statsContainerObserver = setupStatsResizeObserver()
  window.addEventListener('resize', onWindowResize)
  statsThemeObserver = setupStatsThemeObserver()
})

onBeforeUnmount(() => {
  if (resizeTimer) { clearTimeout(resizeTimer); resizeTimer = null }
  stopStatsWatch()
  if (statsThemeObserver) { statsThemeObserver.disconnect(); statsThemeObserver = null }
  if (statsContainerObserver) { statsContainerObserver.disconnect(); statsContainerObserver = null }
  window.removeEventListener('resize', onWindowResize)
  disposeCharts()
})
</script>

<style scoped>
/* ============================================================
   统计分析页样式
   ============================================================ */

/* 一级Tab */
.page-tabs { margin-bottom: 18px; }
.page-tabs :deep(.el-tabs__item) { font-size: 15px; font-weight: 600; height: 44px; line-height: 44px; }
.page-tabs :deep(.el-tabs__item.is-active) { font-weight: 700; }
.page-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }

/* 筛选卡片 */
.filter-card { margin-bottom: 22px; box-shadow: var(--shadow-soft) !important; }
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-bar { display: flex; align-items: center; }
.filter-controls { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.filter-controls :deep(.el-input__wrapper) { height: 36px; border-radius: 6px; }
.quick-select { width: 150px; }
.date-picker { width: 260px; }
.filter-controls .el-button { height: 36px; border-radius: 6px; }

/* 图表卡片 */
.chart-card { min-width: 0; padding: 0; overflow: hidden; box-shadow: var(--shadow-soft) !important; }
.chart-card:hover { transform: none !important; box-shadow: var(--shadow-soft) !important; }
.chart-card :deep(.el-card__header) { padding: 15px 18px; border-bottom-color: var(--border-color); }
.chart-card :deep(.el-card__body) { padding: 14px 16px 18px; }
.chart-title { font-size: 13px; font-weight: 600; color: var(--text-primary); letter-spacing: 0.025em; }
.chart-container { height: clamp(300px, 31vw, 360px); width: 100%; }

/* 表格卡片 */
.table-card { padding: 0; overflow: hidden; box-shadow: var(--shadow-soft) !important; }
.table-card :deep(.el-card__body) { padding: 12px 16px 14px; }
.report-table { width: 100%; }
.report-table :deep(.el-table__header th) {
  background-color: var(--bg-table-header) !important;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-regular) !important;
  border-bottom: 1px solid var(--border-color) !important;
}
.report-table :deep(.el-table__row) { height: 48px; transition: background-color 0.2s ease; }
.report-table :deep(.el-table__row:hover) td {
  background-color: var(--bg-table-hover) !important;
}

/* 空状态 */
.empty-hint { text-align: center; padding: 60px 0; color: var(--text-placeholder); font-size: 14px; }

@media (max-width: 1024px) {
  .stats-page :deep(.el-row) {
    row-gap: 20px;
  }

  .stats-page :deep(.el-col-12) {
    max-width: 100%;
    flex: 0 0 100%;
  }

  .chart-container {
    height: 330px;
  }
}

@media (max-width: 720px) {
  .filter-controls,
  .quick-select,
  .date-picker,
  .filter-controls .el-button {
    width: 100%;
  }

  .filter-controls {
    align-items: stretch;
  }

  .chart-card :deep(.el-card__body) {
    padding: 10px 10px 14px;
  }

  .chart-container {
    height: 300px;
  }

  .table-card :deep(.el-card__body) {
    padding: 10px 0 12px;
  }
}
</style>
