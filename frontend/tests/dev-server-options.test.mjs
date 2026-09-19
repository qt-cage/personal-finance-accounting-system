import assert from 'node:assert/strict'
import test from 'node:test'
import { devServerOptions } from '../src/utils/devServerOptions.js'

test('development instances use isolated caches and cannot silently change ports', () => {
  const main = devServerOptions([])
  const testInstance = devServerOptions(['--port', '5174'])
  assert.equal(main.server.port, 5173)
  assert.equal(main.server.strictPort, true)
  assert.notEqual(main.cacheDir, testInstance.cacheDir)
  assert.deepEqual(testInstance, devServerOptions(['--port=5174']))
})
test('chart submodules are prebundled before the lazy statistics route is opened', () => {
  assert.deepEqual(devServerOptions([]).optimizeDeps.include,
    ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'])
})
test('invalid ports cannot form arbitrary cache paths', () => {
  for (const port of ['../other', '0', '65536', 'abc']) {
    assert.throws(() => devServerOptions(['--port', port]))
  }
})
