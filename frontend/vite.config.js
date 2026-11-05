import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: [['babel-plugin-react-compiler']],
      },
    }),
    tailwindcss(),
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
            query: ['@tanstack'],
            radix: ['radix-ui', '@radix-ui'],
            icons: ['lucide-react'],
            motion: ['motion', 'framer-motion', 'motion-dom', 'motion-utils'],
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
})
