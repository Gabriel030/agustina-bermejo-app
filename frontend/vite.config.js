import process from 'node:process'
import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import { manifest } from './pwa.manifest.js'
import { readFileSync } from 'node:fs'
import { extractInlineScripts, resolveApiOrigin } from './csp.js'

const cspInHeader = (env) => env.VITE_CSP_IN_HEADER === 'true'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  define: {
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(process.env.GITHUB_SHA || Date.now().toString()),
  },
  plugins: [
    {
      name: 'csp-api-origin',
      transformIndexHtml(html) {
        const env = loadEnv(mode, '.', 'VITE_')
        const withOrigin = html.replace('__API_ORIGIN__', resolveApiOrigin(env.VITE_API_BASE_URL))
        if (!cspInHeader(env)) return withOrigin
        return withOrigin.replace(/\s*<meta http-equiv="Content-Security-Policy"[^>]*>/, '')
      },
    },
    {
      name: 'theme-script-as-file',
      apply: 'build',
      buildStart() {
        const env = loadEnv(mode, '.', 'VITE_')
        if (!cspInHeader(env)) return
        const source = extractInlineScripts(readFileSync('index.html', 'utf8'))[0]
        this.emitFile({
          type: 'asset',
          fileName: 'theme-init.js',
          source: source.replace('%VITE_DEFAULT_DESIGN%', env.VITE_DEFAULT_DESIGN ?? ''),
        })
      },
      transformIndexHtml(html) {
        const env = loadEnv(mode, '.', 'VITE_')
        if (!cspInHeader(env)) return html
        return html.replace(/<script>[\s\S]*?<\/script>/, '<script src="/theme-init.js"></script>')
      },
    },
    react(),
    babel({ presets: [reactCompilerPreset()] }),
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
