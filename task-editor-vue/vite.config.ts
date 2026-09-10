import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {resolve} from 'path'

export default defineConfig({
  // 相对路径：后端按 /assets/<file> 托管静态资源，相对引用同样解析到 /assets/
  base: './',
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