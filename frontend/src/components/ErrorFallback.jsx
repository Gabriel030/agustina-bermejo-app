import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { OfflineState } from '@/pwa/OfflineState';
import { useOnlineStatus } from '@/pwa/useOnlineStatus';

export function ErrorFallback({ reset, fullScreen = false }) {
  const online = useOnlineStatus();
  const content = (
    <GlassCard className="w-full max-w-md p-6">
      {!online ? (
        <OfflineState onRetry={reset} />
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
