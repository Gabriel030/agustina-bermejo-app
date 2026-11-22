import { Link } from 'react-router-dom';
import { ArrowRight, CheckCircle2, TriangleAlert } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';

export function StudentBalanceNotice({ standing, isLoading, isError, onRetry }) {
  if (isLoading) {
    return (
      <div role="status" aria-busy="true">
        <span className="sr-only">Cargando tu estado de cuenta</span>
        <Skeleton className="h-14 w-full" />
      </div>
    );
  }

  if (isError) {
    return (
      <GlassCard role="alert" className="flex flex-wrap items-center justify-between gap-3 border-danger p-4">
        <span className="flex items-center gap-2 text-size-13-5 font-semibold text-danger-ink">
          <TriangleAlert className="h-[18px] w-[18px]" aria-hidden="true" />
          No pudimos verificar tu saldo
        </span>
        <Button type="button" variant="outline" className="h-11" onClick={onRetry}>
          Reintentar
        </Button>
      </GlassCard>
    );
  }

  if (standing === 'MOROSO') {
    return (
      <GlassCard className="flex flex-wrap items-center justify-between gap-3 border-warning p-4">
        <span className="flex items-center gap-2 text-size-13-5 font-semibold text-warning-ink">
          <TriangleAlert className="h-[18px] w-[18px]" aria-hidden="true" />
          Saldo pendiente
        </span>
        <Link to="/alumno/pagos" className="inline-flex min-h-11 items-center gap-1 text-xs font-bold text-primary hover:underline">
          Ver pagos
          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
        </Link>
      </GlassCard>
    );
  }

  if (standing === 'AL_DIA') {
    return (
      <div className="flex">
        <StatusPill tone="success">
          <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
          Pagos al día
        </StatusPill>
      </div>
    );
  }

  return null;
}
