import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    // Inject VITE_API_URL so vanilla JS in /public can access via __API_URL__
    define: {
      __API_URL__: JSON.stringify(env.VITE_API_URL || '')
    },
    server: {
      proxy: {
        // In dev, proxy /api/* to local Spring Boot so BACKEND='' works
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
