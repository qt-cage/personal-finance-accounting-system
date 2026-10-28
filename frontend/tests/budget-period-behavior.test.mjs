import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/Budget.vue', import.meta.url), 'utf8')
function editor() {
  const code = source.slice(source.indexOf('function openDialog(row)'), source.indexOf('async function handleDelete(id)'))
  const state = { form: {}, month: { value: '2026-02' }, loadedMonth: { value: '2026-01' }, isEdit: {}, dialogVisible: {}, submitting: {}, calls: [] }
  const api = new Function('form', 'month', 'loadedMonth', 'isEdit', 'dialogVisible', 'submitting', 'formRef', 'saveBudget', 'ElMessage', 't', 'loadBudgets', `${code};return {openDialog,handleSubmit}`)(state.form, state.month, state.loadedMonth, state.isEdit, state.dialogVisible, state.submitting, { value: { validate: async () => true } }, async data => state.calls.push(data), { success() {} }, x => x, () => {})
  return { ...state, ...api }
}
test('editing an old row after changing the month keeps the original budget period', async () => {
  const e = editor()
  e.openDialog({ month: '2026-01', categoryId: null, amount: 100 })
  e.month.value = '2026-03'
  await e.handleSubmit()
  assert.deepEqual(e.calls, [{ categoryId: null, amount: 100, month: '2026-01' }])
})
test('new budget captures selected period when opened; later picker changes do not retarget it', async () => {
  const e = editor(); e.openDialog(null); e.form.amount = 25; e.month.value = '2026-03'
  await Promise.all([e.handleSubmit(), e.handleSubmit()])
  assert.deepEqual(e.calls, [{ categoryId: null, amount: 25, month: '2026-02' }])
})
test('late budget response cannot replace a more recent loaded month', async () => {
  const code = source.slice(source.indexOf('async function loadBudgets()'), source.indexOf('async function loadCategories()'))
  const month = { value: '2026-01' }, loadedMonth = {}, budgetList = {}, monthTotalSpent = {}, requests = []
  const loading = {}, loadError = {}
  const load = new Function('month', 'loadedMonth', 'budgetList', 'monthTotalSpent', 'loading', 'loadError', 'getMonthDateRange', 'getBudgetList', 'getStatistics', 't', `let budgetRequest=0;${code};return loadBudgets`)(month, loadedMonth, budgetList, monthTotalSpent, loading, loadError, m => ({ startDate: m+'-01', endDate: m+'-28' }), () => new Promise(resolve => requests.push(resolve)), async () => ({ data: { expense: 10 } }), x => x)
  const first = load(); month.value = '2026-02'; const second = load()
  requests[1]({ data: [{ month: '2026-02' }] }); await second
  requests[0]({ data: [{ month: '2026-01' }] }); await first
  assert.equal(loadedMonth.value, '2026-02'); assert.equal(budgetList.value[0].month, '2026-02')
})
