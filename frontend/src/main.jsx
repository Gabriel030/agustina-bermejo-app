import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './App.jsx'
import { reportClientError } from '@/lib/reportClientError'
import { registerServiceWorker } from '@/pwa/registerServiceWorker'
import { captureInstallPrompt } from '@/pwa/installPromptStore'
import { preloadPageForPath } from '@/routes/preloadPage'
import { API_BASE_URL } from '@/api/client'
import { buildTunnelUrl } from '@/lib/monitoring/buildTunnelUrl'
import { startClientErrorMonitoring } from '@/lib/monitoring/startClientErrorMonitoring'

if (import.meta.env.VITE_SENTRY_DSN) {
  startClientErrorMonitoring({
    dsn: import.meta.env.VITE_SENTRY_DSN,
    environment: import.meta.env.MODE,
    release: import.meta.env.VITE_APP_VERSION,
    tunnel: buildTunnelUrl(API_BASE_URL),
  }).catch((error) => reportClientError(error, { scope: 'monitoring-start' }))
  window.addEventListener('error', (event) => reportClientError(event.error ?? new Error(event.message), { scope: 'window-error' }))
}

window.addEventListener('unhandledrejection', (event) => reportClientError(event.reason, { scope: 'unhandledrejection' }))

captureInstallPrompt()

preloadPageForPath(window.location.pathname)

if (import.meta.env.PROD) {
  window.addEventListener('load', () => registerServiceWorker().catch((error) => reportClientError(error, { scope: 'service-worker' })))
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
