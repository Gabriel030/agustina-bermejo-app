import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './App.jsx'
import { reportClientError } from '@/lib/reportClientError'

window.addEventListener('unhandledrejection', (event) => reportClientError(event.reason, { scope: 'unhandledrejection' }))

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
