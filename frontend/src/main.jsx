import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './App.jsx'
import { reportClientError } from '@/lib/reportClientError'
import { registerServiceWorker } from '@/pwa/registerServiceWorker'
import { captureInstallPrompt } from '@/pwa/installPromptStore'
import { preloadPageForPath } from '@/routes/preloadPage'

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
