import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { amountToCents, subtractAmounts, sumAmounts } from '../src/utils/money.js'
import { buildMonthlyReport, summarizeMonthlyReport } from '../src/utils/monthlyReport.js'
import { formatAmount } from '../src/utils/format.js'

test('decimal money is summed and subtracted in cents', () => {
  assert.equal(sumAmounts([0.1, 0.2]), 0.3)
  assert.equal(subtractAmounts('0.30', '0.20'), 0.1)
  assert.equal(sumAmounts(Array(100).fill('0.01')), 1)
  assert.equal(amountToCents('1.005'), 101)
  assert.equal(amountToCents('-1.005'), -101)
  assert.equal(amountToCents('99.999'), 10000)
  assert.throws(() => amountToCents('NaN'), TypeError)
  assert.throws(() => amountToCents('90071992547409.92'), RangeError)
})

test('multiple-month report keeps income, expense and balance totals exact', () => {
  const input = [
    { month: '2026-03', income: '1000.01', expense: '50.03' },
    { month: '2026-01', income: '0.10', expense: '0.01' },
    { month: '2026-02', income: '0.20', expense: '0.02' }
  ]
  const report = buildMonthlyReport(input)
  assert.deepEqual(report.map(row => row.month), ['2026-01', '2026-02', '2026-03'])
  assert.deepEqual(report.map(row => row.balance), [0.09, 0.18, 949.98])
  assert.deepEqual(summarizeMonthlyReport(report), { income: 1000.31, expense: 50.06, balance: 950.25 })
  assert.equal(sumAmounts(report.map(row => row.balance)), 950.25)
  assert.equal(input[0].month, '2026-03')
})

test('empty and expense-only reports have defined totals and negative balance', () => {
  assert.deepEqual(summarizeMonthlyReport([]), { income: 0, expense: 0, balance: 0 })
  const rows = buildMonthlyReport([{ month: '2026-09', income: null, expense: '1.23' }])
  assert.equal(rows[0].balance, -1.23)
  assert.deepEqual(summarizeMonthlyReport(rows), { income: 0, expense: 1.23, balance: -1.23 })
})

test('month-over-month comparison handles year boundaries, zero baselines and missing months', () => {
  const rows = buildMonthlyReport([
    { month: '2026-12', expense: '0.10' },
    { month: '2027-01', expense: '0.20' },
    { month: '2027-03', expense: '0.30' },
    { month: '2027-04', expense: '0.00' },
    { month: '2027-05', expense: '0.10' }
  ])
  assert.deepEqual(rows.map(row => row.momRatio), [null, 100, null, -100, null])
})

test('Stats actual summary callback and actual table columns display nonzero monetary totals', () => {
  const source = readFileSync(new URL('../src/views/Stats.vue', import.meta.url), 'utf8')
  const tableStart = source.indexOf('<el-table :data="reportData"')
  const table = source.slice(tableStart, source.indexOf('</el-table>', tableStart))
  const columns = [...table.matchAll(/<el-table-column\b([^>]*)>/g)]
    .map(match => ({ property: match[1].match(/\bprop="([^"]+)"/)?.[1] }))
  const callback = source.slice(source.indexOf('function reportSummary('), source.indexOf('function onTabChange('))
  const summary = new Function('reportData', 'summarizeMonthlyReport', 'formatAmount', 't', `${callback}; return reportSummary`)(
    { value: buildMonthlyReport([{ month: '2026-09', income: '100.30', expense: '30.20' }]) },
    summarizeMonthlyReport, formatAmount, key => key === 'common.summaryTotal' ? '合计' : key
  )
  assert.equal(columns.length, 5)
  assert.deepEqual(summary({ columns }), ['合计', '¥100.30', '¥30.20', '¥70.10', ''])
  assert.deepEqual(summary({ columns: [columns[2], columns[0], columns[3]] }), ['¥30.20', '合计', '¥70.10'])
})
