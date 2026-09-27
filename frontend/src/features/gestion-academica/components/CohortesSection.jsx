import { useState } from 'react';
import { Plus } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { useProgramCohorts } from '@/features/gestion-academica/hooks/useCohorts';
import { NuevaCohortDialog } from './NuevaCohortDialog';

const INTAKE_LABELS = {
  FIRST: '1er semestre',
  SECOND: '2do semestre',
};

function cohortTitle(cohort) {
  const intakeLabel = INTAKE_LABELS[cohort.intake] ?? cohort.intake;
  const year = cohort.admissionStart ? new Date(cohort.admissionStart).getFullYear() : '';
  return `Ingreso ${intakeLabel} ${year}`;
}

function admissionStatus(cohort) {
  if (!cohort.admissionEnd) return { label: 'Sin fecha', tone: 'neutral' };
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const end = new Date(cohort.admissionEnd);
  return end >= today
    ? { label: 'Admisión abierta', tone: 'success' }
    : { label: 'Admisión cerrada', tone: 'neutral' };
}

function CohortCard({ cohort }) {
  const status = admissionStatus(cohort);

  const dateRange = [
    cohort.admissionStart ? `Admisión: ${cohort.admissionStart}` : null,
    cohort.admissionEnd ? `hasta ${cohort.admissionEnd}` : null,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <GlassCard className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-[14px] font-semibold text-ink">{cohortTitle(cohort)}</span>
            <StatusPill tone={status.tone}>{status.label}</StatusPill>
          </div>
          {dateRange && <p className="mt-0.5 text-[12px] text-ink-faint">{dateRange}</p>}
          {cohort.cursadaStart && (
            <p className="text-[12px] text-ink-faint">Inicio cursada: {cohort.cursadaStart}</p>
          )}
        </div>
        <div className="shrink-0 text-right">
          <p className="text-[12px] text-ink-faint">Inscriptos</p>
          <p className="text-[14px] font-semibold text-ink">—</p>
        </div>
      </div>
      {cohort.createdByName && (
        <p className="mt-2 text-[11px] text-ink-faint">Creado por {cohort.createdByName}</p>
      )}
    </GlassCard>
  );
}

export function CohortesSection({ programId }) {
  const { data: cohorts, isLoading } = useProgramCohorts(programId);
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-[11px] font-bold uppercase tracking-widest text-ink-faint">Cohortes de ingreso</h2>
        <Button variant="gradient" size="sm" onClick={() => setDialogOpen(true)}>
          <Plus className="mr-1.5 h-3.5 w-3.5" />
          Nueva cohorte
        </Button>
      </div>

      {isLoading ? (
        <Skeleton rows={2} />
      ) : !cohorts?.length ? (
        <GlassCard className="p-6">
          <EmptyState
            title="Sin cohortes"
            description="Creá una cohorte de ingreso para empezar a recibir inscripciones."
          />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-3">
          {cohorts.map((c) => (
            <CohortCard key={c.id} cohort={c} />
          ))}
        </div>
      )}

      <NuevaCohortDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        programId={programId}
      />
    </div>
  );
}
