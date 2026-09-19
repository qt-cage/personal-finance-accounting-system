import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = relativePath => readFileSync(new URL(`../${relativePath}`, import.meta.url), 'utf8')
const appState = read('src/components/AppState.vue')
const dashboard = read('src/views/Dashboard.vue')
const bill = read('src/views/Bill.vue')
const category = read('src/views/Category.vue')
const budget = read('src/views/Budget.vue')
const stats = read('src/views/Stats.vue')
const profile = read('src/views/Profile.vue')
const request = read('src/utils/request.js')
const app = read('src/App.vue')

test('shared state surface is accessible and supports loading, empty, and error modes', () => {
  assert.match(appState, /\['loading', 'empty', 'error'\]/)
  assert.match(appState, /aria-live="polite"/)
  assert.match(appState, /:aria-busy="type === 'loading'"/)
  assert.match(appState, /@click="\$emit\('retry'\)"/)
  assert.match(appState, /<el-skeleton animated/)
})

test('data pages expose recoverable loading and error states', () => {
  for (const page of [dashboard, bill, category, budget, stats]) {
    assert.match(page, /import AppState from/)
    assert.match(page, /type="loading"/)
    assert.match(page, /type="error"/)
    assert.match(page, /common\.retry/)
    assert.doesNotMatch(page, /catch\s*\([^)]*\)\s*\{\s*\/\*\s*ignore\s*\*\//)
  }

  assert.match(profile, /loading && !loaded/)
  assert.match(profile, /loadError && !loaded/)
  assert.match(profile, /@click="retryLoad"/)
})

test('list and report pages keep explicit empty-state copy', () => {
  assert.match(bill, /:empty-text="t\('bill\.noData'\)"/)
  assert.match(category, /:empty-text="\$t\('common\.noData'\)"/)
  assert.match(budget, /:empty-text="\$t\('common\.noData'\)"/)
  assert.match(stats, /type="empty"/)
  assert.match(stats, /common\.noDataDescription/)
})

test('401 handling distinguishes session expiry from public authentication failures', () => {
  assert.match(request, /function isPublicAuthRequest/)
  assert.match(request, /function handleUnauthorized/)
  assert.match(request, /common\.sessionExpired/)
  assert.match(request, /router\.replace\('\/login'\)/)
  assert.match(request, /res\.code === 401 && !isPublicAuthRequest\(response\.config\)/)
  assert.match(request, /error\.response\?\.status === 401 && !isPublicAuthRequest\(error\.config\)/)
})

test('document language follows the active locale for assistive technology', () => {
  assert.match(app, /const documentLangMap/)
  assert.match(app, /watch\(locale/)
  assert.match(app, /document\.documentElement\.lang/)
  assert.match(app, /\{ immediate: true \}/)
})
