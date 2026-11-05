import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';

export function LoadErrorState({ error, onRetry, resource = 'los datos', backTo, backLabel, bare = false }) {
  const notFound = error?.status === 404;
  const action = (
    <div className="flex flex-wrap justify-center gap-2">
      {!notFound && onRetry && <Button onClick={onRetry}>Reintentar</Button>}
      {backTo && (
        <Button asChild variant="outline">
          <Link to={backTo}>Volver a {backLabel}</Link>
        </Button>
      )}
    </div>
  );
  const content = (
    <EmptyState
      icon="⚠️"
      title={notFound ? 'No encontramos lo que buscabas' : `No pudimos cargar ${resource}`}
      description={
        notFound
          ? 'Puede que ya no exista o que el enlace sea incorrecto.'
          : 'Es un problema de conexión o del servidor. Podés reintentar en un momento.'
      }
      action={action}
    />
  );
  if (bare) return <div role="alert">{content}</div>;
  return (
    <GlassCard className="flex flex-1 flex-col p-12" role="alert">
      {content}
    </GlassCard>
  );
}
