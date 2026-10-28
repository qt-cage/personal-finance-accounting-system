import { exactCents } from './money.js'

// 只改写JSON字符串外的数值token；保留后端JSON契约，不让大额数值先经过IEEE-754取整。
export function parseFinancialJson(data) {
  if (typeof data !== 'string' || !data) return data
  const protectedJson = data.replace(/"(?:\\.|[^"\\])*"|(-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)/g, (token, number) => {
    if (number === undefined) return token
    const value = Number(number)
    const cents = exactCents(number)
    return !Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value)) || exactCents(value) !== cents
      ? JSON.stringify(number) : token
  })
  return JSON.parse(protectedJson)
}
