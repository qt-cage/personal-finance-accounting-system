import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import test from 'node:test'
import { formatLocalDate, getCurrentWeekRange, getMonthDateRange } from '../src/utils/businessDate.js'

test('local business dates stay correct before 08:00 at month and year boundaries in Shanghai', () => {
  const moduleUrl = new URL('../src/utils/businessDate.js', import.meta.url).href
  const script = `
    import { formatLocalDate, formatLocalMonth, getCurrentWeekRange } from ${JSON.stringify(moduleUrl)};
    const values = ['2026-09-01T00:30:00+08:00', '2027-01-01T00:01:00+08:00', '2028-02-29T07:59:00+08:00'];
    console.log(JSON.stringify(values.map(value => {
      const date = new Date(value);
      return { date: formatLocalDate(date), month: formatLocalMonth(date), week: getCurrentWeekRange(date) };
    })));
  `
  const result = spawnSync(process.execPath, ['--input-type=module', '-e', script], {
    env: { ...process.env, TZ: 'Asia/Shanghai' }, encoding: 'utf8'
  })
  assert.equal(result.status, 0, result.stderr)
  assert.deepEqual(JSON.parse(result.stdout), [
    { date: '2026-09-01', month: '2026-09', week: { start: '2026-08-31', end: '2026-09-01' } },
    { date: '2027-01-01', month: '2027-01', week: { start: '2026-12-28', end: '2027-01-01' } },
    { date: '2028-02-29', month: '2028-02', week: { start: '2028-02-28', end: '2028-02-29' } }
  ])
})

test('week shortcuts run from Monday to the local selected day without mutating input', () => {
  const sunday = new Date(2026, 8, 6, 0, 1)
  const original = sunday.getTime()
  assert.deepEqual(getCurrentWeekRange(sunday), { start: '2026-08-31', end: '2026-09-06' })
  assert.equal(sunday.getTime(), original)
  assert.deepEqual(getCurrentWeekRange(new Date(2026, 8, 7, 0, 1)), { start: '2026-09-07', end: '2026-09-07' })
})

test('monthly ranges include leap days and reject invalid month input', () => {
  assert.deepEqual(getMonthDateRange('2028-02'), { startDate: '2028-02-01', endDate: '2028-02-29' })
  assert.deepEqual(getMonthDateRange('2027-02'), { startDate: '2027-02-01', endDate: '2027-02-28' })
  assert.deepEqual(getMonthDateRange('2026-12'), { startDate: '2026-12-01', endDate: '2026-12-31' })
  for (const month of ['2026-00', '2026-13', '2026-2', '', null]) {
    assert.throws(() => getMonthDateRange(month), TypeError)
  }
  assert.throws(() => formatLocalDate(new Date('invalid')), TypeError)
})
