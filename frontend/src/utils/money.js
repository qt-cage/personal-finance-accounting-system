// 金额始终以精确整数分计算；大额结果保留十进制字符串，普通金额仍返回Number。
export function exactCents(value) {
  if (value === null || value === undefined || value === '') return 0n
  const text = String(value).trim()
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(text)
  if (!match) throw new TypeError('Amount must be a finite decimal number')
  const fraction = match[3] || ''
  const exponent = Number(match[4] || 0)
  if (text.length > 1024 || !Number.isInteger(exponent) || Math.abs(exponent) > 1000) throw new RangeError('Decimal magnitude is unsupported')
  const digits = BigInt(match[2] + fraction)
  const shift = 2 + exponent - fraction.length
  const divisor = shift < 0 ? 10n ** BigInt(-shift) : 1n
  const cents = shift < 0 ? (digits + divisor / 2n) / divisor : digits * 10n ** BigInt(shift)
  return match[1] === '-' ? -cents : cents
}

export function centsDecimal(cents) {
  const absolute = cents < 0n ? -cents : cents
  return `${cents < 0n ? '-' : ''}${absolute / 100n}.${String(absolute % 100n).padStart(2, '0')}`
}

export function fromCents(cents) {
  const integer = Number(cents)
  const amount = integer / 100
  // 安全整数分除以100后也可能丢掉一分，因此还要验证往返值。
  return Number.isSafeInteger(integer) && exactCents(amount) === cents ? amount : centsDecimal(cents)
}

export function normalizeAmount(value) {
  return fromCents(exactCents(value))
}

// 保留已有要求Number结果的调用契约；无界汇总使用exactCents。
export function amountToCents(value) {
  const cents = Number(exactCents(value))
  if (!Number.isSafeInteger(cents)) throw new RangeError('Amount exceeds the supported precision')
  return cents
}

export function sumAmounts(values) {
  return fromCents(values.reduce((total, value) => total + exactCents(value), 0n))
}

export function subtractAmounts(left, right) {
  return fromCents(exactCents(left) - exactCents(right))
}

export function divideAmount(value, divisor) {
  if (!Number.isSafeInteger(divisor) || divisor <= 0) throw new RangeError('Divisor must be a positive integer')
  const cents = exactCents(value), d = BigInt(divisor), absolute = cents < 0n ? -cents : cents
  const rounded = (absolute + d / 2n) / d
  return fromCents(cents < 0n ? -rounded : rounded)
}
