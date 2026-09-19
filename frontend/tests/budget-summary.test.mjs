import assert from 'node:assert/strict'
import test from 'node:test'
import { buildBudgetSummary } from '../src/utils/budgetSummary.js'

test('no budget with spending does not manufacture a negative remaining amount', () => {
  assert.deepEqual(buildBudgetSummary([], '120.50'), {
    hasTotalBudget: false, hasCategoryBudgets: false, totalBudget: null, totalSpent: 120.5, remaining: null
  })
})
test('no budget and no spending is still unconfigured, not a zero budget', () => {
  assert.equal(buildBudgetSummary([], 0).remaining, null)
  assert.equal(buildBudgetSummary([], 0).hasTotalBudget, false)
})
test('configured budget keeps normal remaining amount', () => {
  assert.equal(buildBudgetSummary([{ categoryId: null, amount: '200.00' }], '120.50').remaining, 79.5)
})
test('configured budget reports genuine overspending', () => {
  assert.equal(buildBudgetSummary([{ categoryId: null, amount: '100.00' }], '120.50').remaining, -20.5)
})
test('exactly exhausted budget and decimal cents remain accurate', () => {
  assert.equal(buildBudgetSummary([{ categoryId: null, amount: '0.30' }], '0.30').remaining, 0)
  assert.equal(buildBudgetSummary([{ categoryId: null, amount: '0.30' }], '0.10').remaining, 0.2)
})
test('category-only budgets do not imply a global spending limit', () => {
  const result = buildBudgetSummary([{ categoryId: 7, amount: '100.00', spentAmount: '20.00' }], '120.50')
  assert.equal(result.hasCategoryBudgets, true)
  assert.equal(result.hasTotalBudget, false)
  assert.equal(result.remaining, null)
  assert.equal(result.totalSpent, 120.5)
})
test('total and category budgets are not counted twice', () => {
  const result = buildBudgetSummary([{ categoryId: null, amount: '200.00' }, { categoryId: 7, amount: '100.00' }], '120.50')
  assert.equal(result.totalBudget, 200)
  assert.equal(result.remaining, 79.5)
})
