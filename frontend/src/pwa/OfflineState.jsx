import { WifiOff } from 'lucide-react'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'

export function OfflineState({ onRetry }) {
  return (
    <EmptyState
      icon={<WifiOff className="mx-auto size-6 text-ink-faint" aria-hidden="true" />}
      title="Sin conexión"
      description="No pudimos cargar esta pantalla porque no hay internet. Revisá tu conexión y reintentá."
      action={<Button onClick={onRetry}>Reintentar</Button>}
    />
  )
}
