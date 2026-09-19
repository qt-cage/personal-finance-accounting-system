import { amountToCents, subtractAmounts } from './money.js'

export function buildBudgetSummary(budgets, monthlySpent) {
  const totalRow = budgets.find(row => row.categoryId == null)
  const totalSpent = amountToCents(monthlySpent) / 100
  // 分类预算只覆盖部分支出，不能作为全月总预算；未设置与额度为零也不能混同。
  const hasTotalBudget = totalRow != null
  const totalBudget = hasTotalBudget ? amountToCents(totalRow.amount) / 100 : null
  return {
    hasTotalBudget,
    hasCategoryBudgets: budgets.some(row => row.categoryId != null),
    totalBudget,
    totalSpent,
    remaining: hasTotalBudget ? subtractAmounts(totalBudget, totalSpent) : null
  }
}
