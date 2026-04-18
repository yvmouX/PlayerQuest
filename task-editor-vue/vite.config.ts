import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {resolve} from 'path'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:22222',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: resolve(__dirname, '../core/src/main/resources/web'),
    emptyOutDir: true
  }
})