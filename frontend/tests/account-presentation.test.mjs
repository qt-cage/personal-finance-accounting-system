import test from 'node:test'
import assert from 'node:assert/strict'
import { categoryTree, categoryLabel } from '../src/utils/categoryHierarchy.js'
import { billTypeKey, billSign, billTone } from '../src/utils/billPresentation.js'

test('transfer is displayed independently from income and has no income sign', () => {
  assert.equal(billTypeKey(3), 'finance.transfer')
  assert.equal(billSign(3), '')
  assert.equal(billTone(3), 'info')
  assert.equal(billSign(1), '-')
  assert.equal(billSign(2), '+')
})

test('child category keeps its parent context without changing the fetched list', () => {
  const rows = [{ id: 1, name: '餐饮', parentId: null }, { id: 2, name: '午餐', parentId: 1 }, { id: 3, name: '交通', parentId: null }]
  const original = structuredClone(rows)
  assert.equal(categoryLabel(rows[1], rows), '餐饮 / 午餐')
  const tree = categoryTree(rows)
  assert.deepEqual(tree.map(row => row.id), [1, 3])
  assert.deepEqual(tree[0].children.map(row => row.id), [2])
  assert.deepEqual(rows, original)
})
