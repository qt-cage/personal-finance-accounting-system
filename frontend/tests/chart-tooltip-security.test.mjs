import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { escapeHtml } from '../src/utils/html.js'
import { formatAmount } from '../src/utils/format.js'

test('the actual category tooltip escapes markup and preserves currency formatting', () => {
  const source = readFileSync(new URL('../src/views/Stats.vue', import.meta.url), 'utf8')
  const expression = source.match(/formatter:p => `[^\r\n]+`/)[0].replace('formatter:', '')
  const formatter = new Function('escapeHtml', 'formatAmount', `return (${expression})`)(escapeHtml, formatAmount)
  const html = formatter({ name: '<img src=x onerror="alert(1)"> & \'', value: 12.34, percent: 25 })
  assert.equal(html, '&lt;img src=x onerror=&quot;alert(1)&quot;&gt; &amp; &#39;  ¥12.34  25.0%')
  assert.equal(escapeHtml(null), '')
})
