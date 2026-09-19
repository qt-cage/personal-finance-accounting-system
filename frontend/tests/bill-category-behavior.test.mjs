import assert from 'node:assert/strict'
import test from 'node:test'
import { createBillCategoryLoader } from '../src/utils/billCategoryLoader.js'

function harness() {
  const form = { id: 1, type: 1, categoryId: 7 }
  const state = { options: [], loading: false, error: null }
  const requests = []
  const loader = createBillCategoryLoader({
    form,
    fetchCategories: params => new Promise((resolve, reject) => requests.push({ params, resolve, reject })),
    onOptions: value => { state.options = value },
    onLoading: value => { state.loading = value },
    onError: value => { state.error = value }
  })
  return { form, state, requests, loader }
}

test('editing a bill keeps its saved category while options load and after loading', async () => {
  const { form, state, requests, loader } = harness()
  const pending = loader.load()
  assert.equal(form.categoryId, 7)
  assert.equal(state.loading, true)
  requests[0].resolve({ data: [{ id: 7, name: '餐饮' }] })
  assert.equal(await pending, true)
  assert.equal(form.categoryId, 7)
  assert.deepEqual(state.options, [{ id: 7, name: '餐饮' }])
  assert.equal(state.loading, false)
})

test('changing bill type clears selection immediately and ignores an older late response', async () => {
  const { form, state, requests, loader } = harness()
  const first = loader.load()
  form.type = 2
  const latest = loader.load({ resetCategory: true })
  assert.equal(form.categoryId, null)
  assert.deepEqual(requests.map(request => request.params), [{ type: 1 }, { type: 2 }])
  requests[1].resolve({ data: [{ id: 8, name: '工资' }] })
  await latest
  form.categoryId = 8
  requests[0].resolve({ data: [{ id: 7, name: '餐饮' }] })
  assert.equal(await first, false)
  assert.equal(form.categoryId, 8)
  assert.deepEqual(state.options, [{ id: 8, name: '工资' }])
  assert.equal(state.error, null)
})

test('an older response cannot dismiss the latest request loading state', async () => {
  const { form, state, requests, loader } = harness()
  const first = loader.load()
  form.type = 2
  const latest = loader.load({ resetCategory: true })
  requests[0].resolve({ data: [{ id: 7 }] })
  await first
  assert.equal(state.loading, true)
  assert.deepEqual(state.options, [])
  requests[1].resolve({ data: [{ id: 8 }] })
  await latest
  assert.equal(state.loading, false)
})

test('reopening a different bill of the same type keeps the new bill category', async () => {
  const { form, state, requests, loader } = harness()
  const first = loader.load()
  Object.assign(form, { id: 2, categoryId: 9 })
  const latest = loader.load()
  requests[1].resolve({ data: [{ id: 9, name: '交通' }] })
  await latest
  requests[0].resolve({ data: [{ id: 7, name: '餐饮' }] })
  await first
  assert.equal(form.categoryId, 9)
  assert.deepEqual(state.options, [{ id: 9, name: '交通' }])
})

test('failed loading can be retried without clearing the saved category', async () => {
  const { form, state, requests, loader } = harness()
  const failed = loader.load()
  const failure = new Error('offline')
  requests[0].reject(failure)
  assert.equal(await failed, false)
  assert.equal(state.error, failure)
  assert.equal(form.categoryId, 7)
  const retry = loader.load()
  assert.equal(state.error, null)
  requests[1].resolve({ data: [{ id: 7 }] })
  assert.equal(await retry, true)
  assert.equal(form.categoryId, 7)
})

test('stale request failures and closed-dialog responses cannot change current options', async () => {
  const { state, requests, loader } = harness()
  const first = loader.load()
  const latest = loader.load()
  requests[1].resolve({ data: [{ id: 9 }] })
  await latest
  requests[0].reject(new Error('old request failed'))
  await first
  assert.equal(state.error, null)
  assert.deepEqual(state.options, [{ id: 9 }])
  const closing = loader.load()
  loader.invalidate()
  requests[2].resolve({ data: [{ id: 7 }] })
  assert.equal(await closing, false)
  assert.deepEqual(state.options, [])
  assert.equal(state.loading, false)
})
