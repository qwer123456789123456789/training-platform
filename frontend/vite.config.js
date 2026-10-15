// vite.config.js —— @ 指向 src；/api 代理到后端，解决跨域
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 后端 SpringBoot 端口，以许益铖实际端口为准
        changeOrigin: true
      }
    }
  }
})
