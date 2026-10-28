import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import test from 'node:test'
import zh from '../src/i18n/langs/zh-cn.js'
import en from '../src/i18n/langs/en.js'
import ja from '../src/i18n/langs/ja.js'

function keysOf(messages, prefix = '') {
  return Object.entries(messages).flatMap(([key, value]) => {
    const full = prefix ? `${prefix}.${key}` : key
    return typeof value === 'object' ? keysOf(value, full) : [full]
  })
}

test('all supported locales cover the same keys and page message references', () => {
  const keys = keysOf(zh).sort()
  assert.deepEqual(keysOf(en).sort(), keys)
  assert.deepEqual(keysOf(ja).sort(), keys)
  const src = fileURLToPath(new URL('../src/', import.meta.url))
  const files = readdirSync(src, { recursive: true }).filter(file =>
    /\.(js|vue)$/.test(file) && !file.replaceAll('\\', '/').startsWith('i18n/langs/'))
  const namespaces = Object.keys(zh).join('|')
  const reference = new RegExp(`['"]((?:${namespaces})\\.[\\w.]+)['"]`, 'g')
  for (const file of files) {
    const source = readFileSync(path.join(src, file), 'utf8')
    // 同时覆盖 t() 的字面量、条件分支及主题选项中的 labelKey。
    for (const match of source.matchAll(reference)) {
      assert.ok(keys.includes(match[1]), `${file}: missing ${match[1]}`)
    }
  }
})
