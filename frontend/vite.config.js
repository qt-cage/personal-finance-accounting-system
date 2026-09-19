import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { devServerOptions } from './src/utils/devServerOptions.js'

export default defineConfig(({ command }) => ({
  ...(command === 'serve' ? devServerOptions(process.argv) : { cacheDir: 'node_modules/.vite/build' }),
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
}))
