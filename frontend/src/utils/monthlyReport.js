import { amountToCents, subtractAmounts, sumAmounts } from './money.js'

function monthIndex(month) {
  const [year, number] = month.split('-').map(Number)
  return year * 12 + number
}

export function buildMonthlyReport(rows) {
  const sorted = [...rows].sort((left, right) => left.month.localeCompare(right.month))
  return sorted.map((row, index) => {
    const income = amountToCents(row.income) / 100
    const expense = amountToCents(row.expense) / 100
    const previous = sorted[index - 1]
    const previousExpense = previous ? amountToCents(previous.expense) : 0
    const isPreviousMonth = previous && monthIndex(row.month) - monthIndex(previous.month) === 1
    return {
      month: row.month,
      income,
      expense,
      balance: subtractAmounts(income, expense),
      // 缺月或上月为零时没有可计算的环比，不把跨月比较显示为月环比。
      momRatio: isPreviousMonth && previousExpense > 0
        ? (amountToCents(expense) - previousExpense) / previousExpense * 100
        : null
    }
  })
}

export function summarizeMonthlyReport(rows) {
  const income = sumAmounts(rows.map(row => row.income))
  const expense = sumAmounts(rows.map(row => row.expense))
  return { income, expense, balance: subtractAmounts(income, expense) }
}
