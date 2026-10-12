import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

const projectRoot = fileURLToPath(new URL('.', import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools(), tailwindcss()],
  root: projectRoot,
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 所有 /api 请求代理到后端，解决开发期跨域问题
      // 端口对齐 application-local.yml 的 server.port（8082）
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/__tests__/**/*.spec.ts', 'src/**/__tests__/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      // C-013 核心交付范围：认证模块 + 主流程关键路径（不含未改动的旧组件）
      include: [
        'src/models/auth.ts',
        'src/models/base.ts',
        'src/services/auth.ts',
        'src/services/trip-plan.ts',
        'src/stores/auth.ts',
        'src/stores/trip-plan.ts',
        'src/utils/request.ts',
        'src/views/pages/LoginView.vue',
        'src/views/pages/RegisterView.vue',
        'src/components/hitl/HitlSheet.vue',
        'src/components/intent/IntentForm.vue',
      ],
      thresholds: { lines: 80, branches: 75, functions: 80, statements: 80 },
    },
  },
})
