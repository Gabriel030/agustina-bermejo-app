import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';

export function LoadErrorState({ error, onRetry, resource = 'los datos', backTo, backLabel, bare = false, distinguishForbidden = false }) {
  const notFound = error?.status === 404;
  const forbidden = distinguishForbidden && error?.status === 403;
  const action = (
    <div className="flex flex-wrap justify-center gap-2">
      {!notFound && !forbidden && onRetry && <Button onClick={onRetry}>Reintentar</Button>}
      {backTo && (
        <Button asChild variant="outline">
          <Link to={backTo}>{backLabel?.startsWith('el ') ? `Volver al ${backLabel.slice(3)}` : `Volver a ${backLabel}`}</Link>
        </Button>
      )}
    </div>
  );
  const content = (
    <EmptyState
      icon={forbidden ? '🔒' : '⚠️'}
      title={forbidden ? 'No tenés acceso a esta pantalla' : notFound ? 'No encontramos lo que buscabas' : `No pudimos cargar ${resource}`}
      description={
        forbidden
          ? 'Consultá con Administración si necesitás acceso.'
          : notFound
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
