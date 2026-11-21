import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';

export function CalendarSkeleton() {
  return (
    <GlassCard className="flex flex-col gap-2 p-4" aria-busy="true" aria-label="Cargando calendario" role="group">
      <Skeleton className="h-6 w-40 motion-reduce:animate-none" />
      <div className="grid grid-cols-7 gap-1">
        {Array.from({ length: 35 }, (_, index) => (
          <Skeleton key={index} className="h-16 motion-reduce:animate-none sm:h-24" />
        ))}
      </div>
    </GlassCard>
  );
}

export function CalendarLoadError({ onRetry }) {
  return (
    <GlassCard className="flex flex-1 flex-col p-8" role="alert">
      <EmptyState
        icon="⚠️"
        title="No pudimos cargar tu calendario"
        description="Revisá tu conexión e intentá de nuevo. Si sigue fallando, avisale a Secretaría."
        action={<Button onClick={onRetry}>Reintentar</Button>}
      />
    </GlassCard>
  );
}

export function CalendarNoEnrollmentsBanner() {
  return (
    <GlassCard className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between" role="status">
      <div className="flex flex-col gap-0.5">
        <p className="font-heading text-size-15 font-semibold text-ink">Todavía no estás inscripto a ninguna materia</p>
        <p className="text-sm text-ink-soft">
          Por ahora ves solo los feriados. Cuando te inscribas, vas a ver acá tus clases y mesas de examen.
        </p>
      </div>
      <Button asChild variant="outline">
        <Link to="/catalogo">Ver oferta académica</Link>
      </Button>
    </GlassCard>
  );
}
