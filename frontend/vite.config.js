import process from 'node:process'
import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import { manifest } from './pwa.manifest.js'
import { resolveApiOrigin } from './csp.js'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  define: {
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(process.env.GITHUB_SHA ?? Date.now().toString()),
  },
  plugins: [
    {
      name: 'csp-api-origin',
      transformIndexHtml(html) {
        const env = loadEnv(mode, '.', 'VITE_')
        return html.replace('__API_ORIGIN__', resolveApiOrigin(env.VITE_API_BASE_URL))
      },
    },
    react({
      babel: {
        plugins: [['babel-plugin-react-compiler']],
      },
    }),
    tailwindcss(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.js',
      registerType: 'prompt',
      injectRegister: false,
      manifest,
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
      },
      devOptions: { enabled: false },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    chunkSizeWarningLimit: 500,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          const chunkPackages = {
            recharts: ['recharts', 'victory-vendor', 'd3-', 'internmap', 'es-toolkit', '@reduxjs', 'react-redux', 'immer', 'reselect', 'decimal.js-light', 'eventemitter3'],
            'react-vendor': ['react', 'react-dom', 'react-router', 'react-router-dom', 'scheduler'],
            table: ['@tanstack/react-table', '@tanstack/table-core'],
            query: ['@tanstack'],
            'radix-base': ['@radix-ui/react-slot', '@radix-ui/react-compose-refs'],
            radix: ['radix-ui', '@radix-ui'],
            icons: ['lucide-react'],
            utils: ['clsx', 'tailwind-merge', 'class-variance-authority', 'tiny-invariant', 'use-sync-external-store'],
            forms: ['react-hook-form', 'zod', '@hookform'],
          }
          const path = id.split('node_modules/').pop().split('/')
          const packageName = path[0].startsWith('@') ? `${path[0]}/${path[1]}` : path[0]
          return Object.keys(chunkPackages).find((chunk) =>
            chunkPackages[chunk].some(
              (name) => packageName === name || packageName.startsWith(`${name}/`) || (name.endsWith('-') && packageName.startsWith(name)),
            ),
          )
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    environmentOptions: {
      jsdom: { url: 'http://localhost:5173' },
    },
    setupFiles: ['./src/test/setup.js'],
    globals: false,
    css: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: ['src/components/ui/**', 'src/test/**', '**/*.config.js'],
    },
  },
}))
