import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { OfflineState } from '@/pwa/OfflineState';
import { useOnlineStatus } from '@/pwa/useOnlineStatus';
import { isChunkLoadError, reloadOnceForChunkError } from '@/lib/chunkLoadError';

function StaleVersionState() {
  const [reloadBlocked, setReloadBlocked] = useState(false);
  return (
    <EmptyState
      icon="🔄"
      title="Hay una versión nueva"
      description={
        reloadBlocked
          ? 'Ya intentamos recargar y la pantalla sigue sin abrir. Cerrá la pestaña y volvé a entrar en unos minutos.'
          : 'La aplicación se actualizó mientras la usabas. Recargá la página para seguir.'
      }
      action={
        !reloadBlocked && (
          <Button onClick={() => setReloadBlocked(!reloadOnceForChunkError())}>Recargar</Button>
        )
      }
    />
  );
}

export function ErrorFallback({ error, reset, fullScreen = false }) {
  const online = useOnlineStatus();
  const content = (
    <GlassCard className="w-full max-w-md p-6">
      {!online ? (
        <OfflineState onRetry={reset} />
      ) : isChunkLoadError(error) ? (
        <StaleVersionState />
      ) : (
      <EmptyState
        icon="⚠️"
        title="Algo salió mal"
        description="Tuvimos un problema al mostrar esta pantalla. Podés reintentar o volver al inicio."
        action={
          <div className="flex flex-wrap justify-center gap-2">
            <Button onClick={reset}>Reintentar</Button>
            <Button variant="outline" asChild>
              <a href="/">Volver al inicio</a>
            </Button>
          </div>
        }
      />
      )}
    </GlassCard>
  );
  if (!fullScreen) return <div className="flex flex-1 items-center justify-center p-4">{content}</div>;
  return <div className="flex min-h-screen items-center justify-center p-4">{content}</div>;
}
