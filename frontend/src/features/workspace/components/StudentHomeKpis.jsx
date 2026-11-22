import { Clock, Star } from 'lucide-react';
import { ProgressBar } from '@/components/ProgressBar';
import { Skeleton } from '@/components/Skeleton';
import { StatCard } from '@/components/StatCard';
import { creditsPercent } from '@/features/workspace/lib/studentHome';

function StatLabel({ icon: Icon, children }) {
  return (
    <span className="flex items-center gap-1.5">
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {children}
    </span>
  );
}

export function StudentHomeKpis({ activeCount, progress, isLoading }) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2" role="status" aria-busy="true">
        <span className="sr-only">Cargando tu avance</span>
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <StatCard label={<StatLabel icon={Clock}>Materias en curso</StatLabel>}>
        <div className="font-heading text-2xl font-bold text-ink">{activeCount}</div>
      </StatCard>
      <StatCard label={<StatLabel icon={Star}>Créditos</StatLabel>}>
        <div className="font-heading text-2xl font-bold text-ink">
          {progress?.creditosAcumulados ?? '—'}
          {progress?.creditosRequeridos != null && (
            <span className="text-base font-semibold text-ink-faint"> / {progress.creditosRequeridos}</span>
          )}
        </div>
        <ProgressBar value={creditsPercent(progress)} label="Créditos acumulados" showValue={false} className="mt-2" />
      </StatCard>
    </div>
  );
}
