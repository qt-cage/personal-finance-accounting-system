import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const loginSource = readFileSync(new URL('../src/views/Login.vue', import.meta.url), 'utf8')

function ruleBody(selector) {
  const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = loginSource.match(new RegExp(`${escapedSelector}\\s*\\{([\\s\\S]*?)\\}`))
  assert.ok(match, `Missing CSS rule: ${selector}`)
  return match[1]
}

test('login input borders stay above browser autofill paint', () => {
  const baseRule = ruleBody('.login-form :deep(.el-input__wrapper)')
  const hoverRule = ruleBody('.login-form :deep(.el-input__wrapper:hover)')
  const focusRule = ruleBody('.login-form :deep(.el-input__wrapper.is-focus)')

  assert.match(baseRule, /border:\s*1px solid var\(--login-rule\)/)
  assert.match(baseRule, /box-shadow:\s*none !important/)
  assert.match(baseRule, /overflow:\s*hidden/)
  assert.match(hoverRule, /border-color:\s*var\(--login-rule-strong\)/)
  assert.match(focusRule, /border:\s*2px solid var\(--color-primary\)/)
  assert.match(focusRule, /box-shadow:\s*none !important/)
  assert.doesNotMatch(focusRule, /inset/)

  const innerRule = ruleBody('.login-form :deep(.el-input__inner)')
  assert.match(innerRule, /height:\s*100%/)
})

test('login panel keeps a restrained paper texture across all themes', () => {
  assert.match(loginSource, /\.form-panel::before\s*\{[\s\S]*?repeating-linear-gradient/)
  assert.match(loginSource, /\.form-panel::after\s*\{[\s\S]*?radial-gradient/)
  assert.match(loginSource, /\[data-theme="eye-care"\] \.form-panel\s*\{[\s\S]*?--panel-grain-opacity:\s*0\.50/)
  assert.match(loginSource, /\[data-theme="dark"\] \.form-panel\s*\{[\s\S]*?--panel-grain-opacity:\s*0\.32/)
  assert.match(loginSource, /\[data-theme="dark"\] \.form-panel\s*\{[\s\S]*?--color-primary:\s*#789078/)
})
