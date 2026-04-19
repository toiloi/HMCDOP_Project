import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Trong Docker, backend là service `backend:8080`, không phải localhost trong container frontend.
const proxyTarget = process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react()],
  preview: {
    allowedHosts: ['minipaas.uth.asia'],
    port: 3000,
    host: '0.0.0.0'
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: proxyTarget,
        changeOrigin: true
      }
    }
  }
})
