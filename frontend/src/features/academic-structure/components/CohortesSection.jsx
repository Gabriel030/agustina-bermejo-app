import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Check, Plus } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { ProgressBar } from '@/components/ProgressBar';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Button } from '@/components/ui/button';
import { formatDate } from '@/lib/billing';
import {
  useCohortReadiness,
  useOpenCohort,
  useProgramCohorts,
} from '@/features/academic-structure/hooks/useCohorts';
import { NuevaCohortDialog } from './NuevaCohortDialog';
import { cohortStatus, cohortTitle, readinessChecklist } from '@/features/academic-structure/lib/cohorts';

const COMMISSIONS_PATH = '/director/oferta-academica';

function cohortDatesLine(cohort) {
  const admission =
    cohort.admissionStart && cohort.admissionEnd
      ? `Admisión ${formatDate(cohort.admissionStart)} al ${formatDate(cohort.admissionEnd)}`
      : null;
  const cursada = cohort.cursadaStart ? `Cursada desde ${formatDate(cohort.cursadaStart)}` : null;
  return [admission, cursada].filter(Boolean).join(' · ');
}

function ReadinessChecklist({ items, canManage }) {
  const fulfilledCount = items.filter((item) => item.fulfilled).length;

  return (
    <section aria-label="Para abrir la cohorte" className="mt-3 rounded-xl bg-warning-bg px-4 py-3">
      <div className="flex flex-wrap items-center gap-3 text-size-13 text-ink">
        <span className="font-bold">Para abrir</span>
        <span className="tabular-nums">
          {fulfilledCount} de {items.length}
        </span>
        <ProgressBar value={(100 * fulfilledCount) / items.length} showValue={false} tone="warning" className="w-24" />
      </div>
      <ul className="mt-2 grid gap-x-8 gap-y-1.5 md:grid-cols-2">
        {items.map((item) => (
          <li key={item.code} className="flex flex-wrap items-center gap-2 text-size-13 text-ink">
            {item.fulfilled ? (
              <Check className="h-4 w-4 text-success-ink" aria-label="Completo" />
            ) : (
              <AlertTriangle className="h-4 w-4 text-warning-ink" aria-label="Falta" />
            )}
            <span>
              {item.label}
              {item.code === 'SECTION_PRECEPTORS' && !item.fulfilled ? (item.total === null ? ` · faltan ${item.pendingCount}` : ` · ${item.withPreceptor} de ${item.total}`) : ''}
            </span>
            {!item.fulfilled && canManage && item.code === 'SECTION_PRECEPTORS' && (
              <Link to={COMMISSIONS_PATH} className="font-semibold text-brand underline underline-offset-2">
                Ir a comisiones
              </Link>
            )}
          </li>
        ))}
      </ul>
      <p className="mt-2 text-size-12 text-ink-soft">«Abrir» se habilita sola al completar todo.</p>
    </section>
  );
}

function CohortCard({ cohort, canManage }) {
  const readinessQuery = useCohortReadiness(cohort.id);
  const openCohort = useOpenCohort();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [openFailed, setOpenFailed] = useState(false);
  const title = cohortTitle(cohort);
  const datesLine = cohortDatesLine(cohort);
  const readiness = readinessQuery.data;
  const status = readiness ? cohortStatus(readiness) : null;
  const checklist = readiness ? readinessChecklist(readiness) : [];
  const responsible = checklist.find((item) => item.code === 'COHORT_RESPONSIBLE');
  const plan = checklist.find((item) => item.code === 'CURRENT_PLAN');
  const missing = checklist.some((item) => !item.fulfilled);

  async function handleOpen() {
    setOpenFailed(false);
    try {
      await openCohort.mutateAsync(cohort.id);
    } catch {
      setOpenFailed(true);
    } finally {
      setConfirmOpen(false);
    }
  }

  return (
    <GlassCard className="p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-size-14 font-semibold text-ink">{title}</h3>
            {status && <StatusPill tone={status.tone}>{status.label}</StatusPill>}
          </div>
          {datesLine && <p className="mt-0.5 text-size-12 text-ink-faint">{datesLine}</p>}
        </div>
        {canManage && readiness && !readiness.open && (
          <Button variant="primary" size="sm" disabled={missing} onClick={() => setConfirmOpen(true)}>
            Abrir
          </Button>
        )}
      </div>

      {readinessQuery.isLoading && <Skeleton className="mt-3 h-5 w-48" />}
      {readinessQuery.isError && (
        <Notice tone="warning" role="alert" className="mt-3">
          No pudimos cargar el estado de la cohorte.{' '}
          <Button type="button" variant="link" size="inline" className="font-semibold" onClick={() => readinessQuery.refetch()}>
            Reintentar
          </Button>
        </Notice>
      )}
      {readiness && (
        <p className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-size-13 text-ink">
          <span>
            <b className="font-semibold text-ink-soft">Responsable</b> {responsible?.fulfilled ? 'Asignado' : '—'}
          </span>
          <span>
            <b className="font-semibold text-ink-soft">Plan</b> {plan?.fulfilled ? 'Vigente' : '—'}
          </span>
        </p>
      )}

      {openFailed && (
        <Notice tone="danger" role="alert" title="No pudimos abrir la cohorte." className="mt-3">
          No se hizo ningún cambio. Probá de nuevo en un momento.
        </Notice>
      )}
      {readiness && !readiness.open && missing && <ReadinessChecklist items={checklist} canManage={canManage} />}

      <ConfirmActionDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={`Abrir «${title}»`}
        description="Se habilita la inscripción de ingresantes desde la fecha de admisión."
        confirmLabel="Abrir cohorte"
        confirmVariant="primary"
        pending={openCohort.isPending}
        onConfirm={handleOpen}
      />
    </GlassCard>
  );
}

export function CohortesSection({ programId, canManage = true }) {
  const { data: cohorts, isLoading, isError, error, refetch } = useProgramCohorts(programId);
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-size-11 font-bold uppercase tracking-widest text-ink-faint">Cohortes de ingreso</h2>
        {canManage && (
          <Button variant="primary" size="sm" disabled={isError} onClick={() => setDialogOpen(true)}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            Nueva cohorte
          </Button>
        )}
      </div>

      {isLoading ? (
        <Skeleton rows={2} />
      ) : isError ? (
        <LoadErrorState error={error} onRetry={refetch} resource="las cohortes" bare />
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
            <CohortCard key={c.id} cohort={c} canManage={canManage} />
          ))}
        </div>
      )}

      {canManage && (
        <NuevaCohortDialog open={dialogOpen} onClose={() => setDialogOpen(false)} programId={programId} />
      )}
    </div>
  );
}
