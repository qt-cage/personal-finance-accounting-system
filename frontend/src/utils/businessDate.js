function validDate(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
    throw new TypeError('A valid local date is required')
  }
  return date
}

export function formatLocalMonth(date = new Date()) {
  validDate(date)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

// 账单记录的是本地日历日期，不能先转换成 UTC 再截取日期。
export function formatLocalDate(date = new Date()) {
  return `${formatLocalMonth(date)}-${String(date.getDate()).padStart(2, '0')}`
}

export function getCurrentWeekRange(date = new Date()) {
  validDate(date)
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
  return { start: formatLocalDate(monday), end: formatLocalDate(date) }
}

export function getMonthDateRange(month = formatLocalMonth()) {
  const match = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(month)
  if (!match) throw new TypeError('Month must use YYYY-MM format')
  const lastDay = new Date(Number(match[1]), Number(match[2]), 0).getDate()
  return { startDate: `${month}-01`, endDate: `${month}-${lastDay}` }
}

// 快捷筛选共用同一日历边界；本周截至今天，月/季度截至期末。
export function getQuickDateRange(type, date = new Date()) {
  validDate(date)
  if (type === 'week') return getCurrentWeekRange(date)
  const year = date.getFullYear()
  const firstMonth = type === 'quarter' ? Math.floor(date.getMonth() / 3) * 3 : date.getMonth()
  const months = type === 'quarter' ? 3 : 1
  return {
    start: formatLocalDate(new Date(year, firstMonth, 1)),
    end: formatLocalDate(new Date(year, firstMonth + months, 0))
  }
}
