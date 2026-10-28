import { exactCents, centsDecimal } from './money.js'

/**
 * 金额格式化工具函数
 */
export function formatAmount(value) {
  const [integer, fraction] = centsDecimal(exactCents(value)).split('.')
  return `${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}`
}
