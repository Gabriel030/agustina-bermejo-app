import { tiempoRelativo } from '@/lib/notificaciones'
import { useOnlineStatus } from '@/pwa/useOnlineStatus'

const REFRESHING_AFTER_MS = 60_000

export function StaleStamp({ dataUpdatedAt, isFetching = false, isError = false, hasData = true }) {
  const online = useOnlineStatus()
  if (!hasData || !(dataUpdatedAt > 0)) return null
  if (!online || isError) {
    return (
      <span className="rounded-full bg-warning-bg px-2 py-px text-[11px] font-semibold text-warning-ink">
        Actualizado {tiempoRelativo(new Date(dataUpdatedAt).toISOString())}
      </span>
    )
  }
  if (isFetching && Date.now() - dataUpdatedAt > REFRESHING_AFTER_MS) {
    return (
      <span role="status" className="rounded-full bg-neutral-bg px-2 py-px text-[11px] font-semibold text-ink-faint">
        Actualizando…
      </span>
    )
  }
  return null
}
