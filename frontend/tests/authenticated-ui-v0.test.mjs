import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const read = (relativePath) => readFileSync(new URL(`../${relativePath}`, import.meta.url), 'utf8')
const layout = read('src/layout/MainLayout.vue')
const dashboard = read('src/views/Dashboard.vue')
const bill = read('src/views/Bill.vue')
const category = read('src/views/Category.vue')
const budget = read('src/views/Budget.vue')
const stats = read('src/views/Stats.vue')
const profile = read('src/views/Profile.vue')
const navigation = read('src/components/AppNavigation.vue')
const themes = read('src/assets/css/themes.css')

test('authenticated shell keeps one reusable responsive navigation', () => {
  assert.match(layout, /<AppNavigation \/>/)
  assert.match(layout, /<el-drawer[\s\S]*?<AppNavigation @navigate=/)
  assert.match(layout, /@media \(min-width: 761px\) and \(max-width: 1100px\)/)
  assert.match(layout, /@media \(max-width: 760px\)/)
  assert.match(layout, /new Intl\.DateTimeFormat\(locale\.value/)
  assert.match(navigation, /el-menu-item\.is-active::after/)
})

test('dashboard uses shared hierarchy and compact category surfaces', () => {
  assert.match(dashboard, /<PageHeader/)
  assert.match(dashboard, /<MetricStrip :items="metricItems"/)
  assert.match(dashboard, /<CategoryBreakdown/)
  assert.doesNotMatch(dashboard, /class="stat-row"/)
  assert.doesNotMatch(dashboard, /#ff6b6b|#63e6be|shadowBlur:\s*16/)
})

test('shared themes avoid default card lift and expose paper-fiber tokens', () => {
  assert.match(themes, /--workspace-fiber-dark:/)
  assert.match(themes, /--workspace-fiber-light:/)
  const cardHover = themes.match(/\.el-card:hover\s*\{([\s\S]*?)\}/)
  assert.ok(cardHover)
  assert.match(cardHover[1], /transform:\s*none/)
  assert.doesNotMatch(cardHover[1], /translateY/)
})

test('all authenticated pages share the approved page hierarchy', () => {
  for (const page of [dashboard, bill, category, budget, stats, profile]) {
    assert.match(page, /<PageHeader/)
    assert.match(page, /import PageHeader from/)
  }

  assert.match(budget, /<MetricStrip :items="metricItems"/)
  assert.match(stats, /<MetricStrip :items="metricItems"/)
  assert.doesNotMatch(stats, /#ff6b6b|#63e6be|#cc5de8|#4dabf7/)
})

test('management and analysis pages include narrow-screen fallbacks', () => {
  assert.match(bill, /@media \(max-width: 720px\)/)
  assert.match(category, /@media \(max-width: 720px\)/)
  assert.match(budget, /@media \(max-width: 720px\)/)
  assert.match(stats, /@media \(max-width: 1024px\)/)
  assert.match(stats, /@media \(max-width: 720px\)/)
})

test('chart layouts react to their real containers instead of only window width', () => {
  assert.match(stats, /new ResizeObserver/)
  assert.match(stats, /expenseLineRef\.value/)
  assert.match(stats, /incomePieRef\.value/)
})
