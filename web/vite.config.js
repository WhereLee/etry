import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Dev proxy: /api goes to the backend so the browser never hits CORS.
// In production Nginx does the same rewrite.
export default defineConfig({
  plugins: [vue()],
  server: {
    // 固定监听 IPv4：只写 port 时 vite 默认绑到 ::1，
    // 用 127.0.0.1 访问会连不上，容易让人以为是服务没起
    host: '127.0.0.1',
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      }
    }
  }
})
