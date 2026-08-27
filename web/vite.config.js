import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Единый источник правды о версии сборки — web/package.json (релиз 0.6, тикет 08).
// Читаем на этапе конфигурации и подставляем как compile-time константу,
// чтобы UI не делал рантайм-запрос за номером версии.
const pkg = JSON.parse(
  readFileSync(fileURLToPath(new URL('./package.json', import.meta.url)), 'utf-8')
)

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_PROXY || 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
})
