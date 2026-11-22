import { CheckCircle2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';

function initialsOf(firstName, lastName) {
  return `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.toUpperCase();
}

export function StudentHomeIdentity({ user, program, regularityView, isLoading }) {
  if (isLoading) {
    return (
      <GlassCard className="p-5" role="status" aria-busy="true">
        <span className="sr-only">Cargando tus datos</span>
        <Skeleton className="h-12 w-3/5" />
      </GlassCard>
    );
  }

  const details = [program?.studentId && `Legajo N° ${program.studentId}`, program?.programName].filter(Boolean).join(' · ');

  return (
    <GlassCard className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 items-center gap-3.5">
        <div
          aria-hidden="true"
          className="flex h-12 w-12 shrink-0 items-center justify-center rounded-control bg-brand font-heading text-base font-bold text-on-accent"
        >
          {initialsOf(user?.nombre, user?.apellido)}
        </div>
        <div className="min-w-0">
          <h1 className="font-heading text-xl font-bold tracking-tight text-ink">
            {user?.nombre} {user?.apellido}
          </h1>
          {details && <div className="text-size-13 text-ink-faint">{details}</div>}
        </div>
      </div>
      {regularityView && (
        <StatusPill tone={regularityView.tone}>
          {regularityView.tone === 'success' && <CheckCircle2 className="h-3 w-3" aria-hidden="true" />}
          {regularityView.label}
        </StatusPill>
      )}
    </GlassCard>
  );
}
