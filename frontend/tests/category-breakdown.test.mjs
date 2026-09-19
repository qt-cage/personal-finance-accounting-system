import assert from 'node:assert/strict'
import test from 'node:test'
import { buildCategoryBreakdown } from '../src/utils/categoryBreakdown.js'

test('category breakdown preserves amounts and derives proportions from cents', () => {
  const source = [{ category_name: 'Food', total_amount: '0.10' }, { category_name: 'Books', total_amount: '0.30' }]
  const result = buildCategoryBreakdown(source)
  assert.equal(result[0].categoryName, 'Books')
  assert.equal(result[0].percent, 75)
  assert.equal(result[1].percent, 25)
  assert.equal(source[0].category_name, 'Food')
})
test('empty and single category have meaningful results', () => {
  assert.deepEqual(buildCategoryBreakdown([]), [])
  assert.deepEqual(buildCategoryBreakdown([{ total: 0 }]), [])
  assert.equal(buildCategoryBreakdown([{ total: '123.45' }])[0].percent, 100)
})
