export function devServerOptions(argv = []) {
  const inline = argv.find(arg => arg.startsWith('--port='))
  const index = argv.indexOf('--port')
  const raw = inline ? inline.slice('--port='.length) : index >= 0 ? argv[index + 1] : '5173'
  const port = Number(raw)
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid development server port')
  return {
    server: { port, strictPort: true },
    cacheDir: `node_modules/.vite/serve-${port}`,
    optimizeDeps: { include: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'] }
  }
}
