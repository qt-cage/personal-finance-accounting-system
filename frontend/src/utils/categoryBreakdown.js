import { amountToCents } from './money.js'

export function buildCategoryBreakdown(rows) {
  const positiveRows = rows.map(row => ({
    categoryId: row.categoryId ?? row.category_name,
    categoryName: row.category_name ?? row.categoryName,
    cents: amountToCents(row.total_amount ?? row.total)
  })).filter(row => row.cents > 0)
  const total = positiveRows.reduce((sum, row) => sum + row.cents, 0)
  return positiveRows.sort((a, b) => b.cents - a.cents).map(row => ({
    ...row, total: row.cents / 100, percent: total > 0 ? row.cents / total * 100 : 0
  }))
}
