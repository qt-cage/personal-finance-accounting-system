export function billTypeKey(type) {
  return type === 1 ? 'bill.expense' : type === 2 ? 'bill.income' : 'finance.transfer'
}
export function billTone(type) {
  return type === 1 ? 'danger' : type === 2 ? 'success' : 'info'
}
export function billSign(type) {
  return type === 1 ? '-' : type === 2 ? '+' : ''
}
