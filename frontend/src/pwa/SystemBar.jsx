import { WifiOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { applyServiceWorkerUpdate } from './registerServiceWorker'
import { useOnlineStatus } from './useOnlineStatus'
import { useServiceWorkerState } from './useServiceWorkerState'

export function SystemBar() {
  const online = useOnlineStatus()
  const { updateAvailable } = useServiceWorkerState()

  return (
    <div role="status" aria-live="polite" className="mx-auto w-full max-w-[1200px] shrink-0 empty:hidden">
      {!online && (
        <div className="flex items-start gap-2 rounded-xl bg-warning-bg px-4 py-2.5 text-size-13 font-semibold text-warning-ink">
          <WifiOff className="mt-0.5 size-4" aria-hidden="true" />
          <div>
            Sin conexión
            <small className="block text-xs font-medium">Podés ver lo último que cargaste. Los cambios necesitan internet.</small>
          </div>
        </div>
      )}
      {online && updateAvailable && (
        <div className="flex items-center gap-3 rounded-xl bg-accent-soft px-4 py-2 text-size-13 font-semibold text-ink">
          <div className="flex-1">
            Hay una versión nueva
            <small className="block text-xs font-medium text-ink-soft">Se actualiza al recargar. Guardá tu trabajo antes.</small>
          </div>
          <Button size="lg" onClick={applyServiceWorkerUpdate}>
            Actualizar
          </Button>
        </div>
      )}
    </div>
  )
}
