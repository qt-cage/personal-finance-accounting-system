import assert from 'node:assert/strict'
import { readFileSync, statSync } from 'node:fs'
import test from 'node:test'

const componentUrl = new URL('../src/components/InteractiveLedgerIllustration.vue', import.meta.url)
const componentSource = readFileSync(componentUrl, 'utf8')
const assetNames = [
  'login-illustration-interior-v1.webp',
  'login-illustration-collage-v1.webp',
  'login-illustration-sealed-ledger-v1.webp',
  'login-illustration-gouache-ledger-v1.webp',
  'login-illustration-print-collage-v1.webp'
]

test('login carousel contains five optimized square illustration assets', () => {
  for (const assetName of assetNames) {
    assert.match(componentSource, new RegExp(assetName.replaceAll('.', '\\.')))
    const asset = statSync(new URL(`../src/assets/${assetName}`, import.meta.url))
    assert.ok(asset.size > 100_000, `${assetName} is unexpectedly small`)
    assert.ok(asset.size < 800_000, `${assetName} should stay below 800 KB`)
  }

  assert.match(componentSource, /width="1254"/)
  assert.match(componentSource, /height="1254"/)
  assert.match(componentSource, /aspect-ratio:\s*1 \/ 1/)
  assert.match(componentSource, /loading="eager"/)
})

test('login carousel pauses ambient motion and honors reduced motion', () => {
  assert.match(componentSource, /const FIRST_SLIDE_DELAY = 14000/)
  assert.match(componentSource, /const NEXT_SLIDE_DELAY = 9000/)
  assert.match(componentSource, /formHasFocus\(\)/)
  assert.match(componentSource, /prefersReducedMotion\(\)/)
  assert.match(componentSource, /@media \(prefers-reduced-motion: reduce\)/)
})

test('login carousel uses direct click, swipe, and keyboard navigation without visible rails', () => {
  assert.doesNotMatch(componentSource, /class="carousel-controls"/)
  assert.match(componentSource, /@pointerdown="handlePointerDown"/)
  assert.match(componentSource, /@pointermove="handlePointerMove"/)
  assert.match(componentSource, /@pointerup="handlePointerUp"/)
  assert.match(componentSource, /const DRAG_THRESHOLD = 52/)
  assert.match(componentSource, /@keydown\.left\.prevent="showPreviousSlide"/)
  assert.match(componentSource, /@keydown\.right\.prevent="showNextSlide"/)
})
