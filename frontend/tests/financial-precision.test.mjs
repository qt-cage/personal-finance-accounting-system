import test from 'node:test'
import assert from 'node:assert/strict'
import { parseFinancialJson } from '../src/utils/financialJson.js'
import { exactCents, sumAmounts, subtractAmounts, divideAmount, normalizeAmount } from '../src/utils/money.js'
import { formatAmount } from '../src/utils/format.js'
import { buildMonthlyReport, summarizeMonthlyReport } from '../src/utils/monthlyReport.js'
import { buildBudgetSummary } from '../src/utils/budgetSummary.js'
import { buildCategoryBreakdown } from '../src/utils/categoryBreakdown.js'
import { readFileSync } from 'node:fs'
import { escapeHtml } from '../src/utils/html.js'

test('JSON retains large monetary tokens without changing normal API fields or quoted content', () => {
  const response=parseFinancialJson(String.raw`{"code":200,"data":{"amount":90071992547409.91,"negative":-90071992547409.91,"small":12.34,"id":7,"text":"90071992547409.91 and \"quoted\"","nil":null,"ok":true}}`)
  assert.equal(response.code,200);assert.equal(response.data.amount,'90071992547409.91')
  assert.equal(response.data.negative,'-90071992547409.91');assert.equal(response.data.small,12.34);assert.equal(response.data.id,7)
  assert.equal(response.data.text,'90071992547409.91 and "quoted"');assert.equal(response.data.nil,null);assert.equal(response.data.ok,true)
  assert.equal(parseFinancialJson('{"x":9.007199254740991e13}').x,'9.007199254740991e13')
  assert.throws(()=>parseFinancialJson('{"x":01}'),SyntaxError)
})
test('large sums differences rounding and formatting preserve individual cents', () => {
  assert.equal(sumAmounts(['90071992547409.91','0.01']),'90071992547409.92')
  assert.equal(subtractAmounts('90071992547409.92','90071992547409.91'),0.01)
  assert.equal(formatAmount('90071992547409.91'),'90,071,992,547,409.91')
  assert.equal(formatAmount('-90071992547409.915'),'-90,071,992,547,409.92')
  assert.equal(formatAmount(1e-7),'0.00');assert.equal(exactCents('1.005e2'),10050n)
  assert.equal(divideAmount('180143985094819.82',2),'90071992547409.91')
  assert.equal(divideAmount('-0.03',2),-0.02)
})
test('safe integer IDs preserve numeric identity while unsafe integer tokens remain exact', () => {
  const data=parseFinancialJson('{"id":100000000000000,"unsafeId":9007199254740993,"income":90071992547409.91}')
  assert.equal(data.id,Number('100000000000000'))
  assert.equal(typeof data.id,'number')
  assert.equal(data.unsafeId,'9007199254740993')
  assert.equal(data.income,'90071992547409.91')
})
test('safe cent integers that cannot survive conversion to decimal Number remain exact', () => {
  assert.equal(normalizeAmount('90071992547409.91'),'90071992547409.91')
  assert.equal(normalizeAmount('12.34'),12.34)
  for(let i=0n;i<200n;i++){
    const cents=9007199254740800n+i
    const input=`${cents/100n}.${String(cents%100n).padStart(2,'0')}`
    assert.equal(exactCents(normalizeAmount(input)),cents)
  }
})
test('large JSON amounts stay exact through monthly budget and category summaries', () => {
  const rows=parseFinancialJson('[{"month":"2026-01","income":90071992547409.91,"expense":90071992547409.90},{"month":"2026-02","income":0.01,"expense":0.02}]')
  const report=buildMonthlyReport(rows), totals=summarizeMonthlyReport(report)
  assert.equal(totals.income,'90071992547409.92');assert.equal(totals.expense,'90071992547409.92');assert.equal(totals.balance,0)
  assert.equal(report[0].balance,0.01)
  const budget=buildBudgetSummary([{categoryId:null,amount:'100.00'}],'90071992547409.91')
  assert.equal(formatAmount(budget.remaining),'-90,071,992,547,309.91')
  const categories=buildCategoryBreakdown([{categoryId:1,category_name:'Large',total_amount:'90071992547409.91'},{categoryId:2,category_name:'Small',total_amount:'0.01'}])
  assert.equal(formatAmount(categories[0].total),'90,071,992,547,409.91')
})
test('pie tooltip displays exact raw amount while chart coordinates remain numeric', () => {
  const source=readFileSync(new URL('../src/views/Stats.vue',import.meta.url),'utf8')
  const expression=source.match(/formatter:p => `[^\r\n]+`/)[0].replace('formatter:','')
  const format=new Function('escapeHtml','formatAmount',`return (${expression})`)(escapeHtml,formatAmount)
  assert.equal(format({name:'<b>test</b>',value:90071992547409.9,data:{realValue:'90071992547409.91'},percent:100}),'&lt;b&gt;test&lt;/b&gt;  ¥90,071,992,547,409.91  100.0%')
})
