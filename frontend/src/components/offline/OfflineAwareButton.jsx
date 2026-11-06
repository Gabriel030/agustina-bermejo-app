import { useId } from 'react'
import { Button } from '@/components/ui/button'
import { useOnlineStatus } from '@/pwa/useOnlineStatus'

export function OfflineAwareButton({ disabled, children, ...props }) {
  const online = useOnlineStatus()
  const noteId = useId()
  return (
    <>
      <Button {...props} disabled={disabled || !online} aria-describedby={online ? undefined : noteId}>
        {children}
      </Button>
      {!online && (
        <p id={noteId} className="text-xs text-ink-faint">
          Necesita conexión. Probá de nuevo cuando vuelvas a tener red.
        </p>
      )}
    </>
  )
}
