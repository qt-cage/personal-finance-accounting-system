function safeCents(value) {
  if (!Number.isSafeInteger(value)) throw new RangeError('Amount exceeds the supported precision')
  return value
}

// 先按十进制拆出整数分，避免 0.1 + 0.2 等浮点误差进入财务汇总。
export function amountToCents(value) {
  if (value === null || value === undefined || value === '') return 0
  const match = /^([+-]?)(\d+)(?:\.(\d*))?$/.exec(String(value).trim())
  if (!match) throw new TypeError('Amount must be a finite decimal number')
  const fraction = match[3] || ''
  const cents = BigInt(match[2]) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2))
    + (Number(fraction[2] || 0) >= 5 ? 1n : 0n)
  return safeCents(Number(match[1] === '-' ? -cents : cents))
}

export function sumAmounts(values) {
  const cents = values.reduce((total, value) => safeCents(total + amountToCents(value)), 0)
  return cents / 100
}

export function subtractAmounts(left, right) {
  return safeCents(amountToCents(left) - amountToCents(right)) / 100
}
