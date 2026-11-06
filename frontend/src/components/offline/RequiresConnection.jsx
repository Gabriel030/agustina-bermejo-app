import { WifiOff } from 'lucide-react'
import { EmptyState } from '@/components/EmptyState'
import { useOnlineStatus } from '@/pwa/useOnlineStatus'

export function RequiresConnection({ children }) {
  const online = useOnlineStatus()
  if (online) return children
  return (
    <EmptyState
      icon={<WifiOff aria-hidden="true" className="mx-auto h-6 w-6 text-ink-faint" />}
      title="Necesita conexión"
      description="Esta sección no se guarda en el dispositivo. Volvé a intentarlo cuando tengas red."
    />
  )
}
