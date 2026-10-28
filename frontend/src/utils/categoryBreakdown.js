import { exactCents, fromCents } from './money.js'

export function buildCategoryBreakdown(rows) {
  const positiveRows = rows.map(row => ({
    categoryId: row.categoryId ?? row.category_name,
    categoryName: row.category_name ?? row.categoryName,
    cents: exactCents(row.total_amount ?? row.total)
  })).filter(row => row.cents > 0n)
  const total = positiveRows.reduce((sum, row) => sum + row.cents, 0n)
  return positiveRows.sort((a, b) => a.cents === b.cents ? 0 : a.cents > b.cents ? -1 : 1).map(row => ({
    ...row, cents: Number.isSafeInteger(Number(row.cents)) ? Number(row.cents) : row.cents.toString(),
    total: fromCents(row.cents), percent: total > 0n ? Number(row.cents) / Number(total) * 100 : 0
  }))
}
