import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { StickyActionBar } from '@/components/StickyActionBar';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/table/DataTable';
import { UnavailableRequestState } from '@/components/director/UnavailableRequestState';
import { formatearFecha } from '@/lib/comisiones';
import { ESTADO_TRAMITE_LABEL, MIN_REJECTION_REASON_LENGTH, decisionErrorMessage, isAlreadyDecided } from '@/lib/aprobaciones';
import { useCycle, useCicloSecciones } from '@/features/academic-structure/hooks/useCiclos';
import { useApproveCycle, useCycleConflicts, useRejectCycle } from '@/features/academic-structure/hooks/useOfferApproval';
import {
  OFFER_APPROVALS_PATH,
  approvalSummary,
  conflictDetail,
  conflictTitle,
  conflictedSectionIds,
  cycleTargetKind,
  cycleTargetName,
  leadInstructorName,
  slotLabel,
  sortedSlots,
} from '@/features/academic-structure/lib/offerApproval';
import { shiftLabel } from '@/features/academic-structure/lib/shifts';

function buildSectionColumns(conflicted) {
  return [
    {
      id: 'subject',
      header: 'Materia',
      accessorFn: (section) => section.subjectName,
      meta: { filterable: true, filterTitle: 'Materia', cardTitle: true },
      cell: ({ row }) => (
        <div className="flex min-w-0 flex-col gap-1">
          <span className="font-heading text-size-14 font-semibold text-ink">{row.original.subjectName}</span>
          <span className="text-xs text-ink-faint">Comisión {row.original.code ?? row.original.name}</span>
          {conflicted.has(row.original.id) && <StatusPill tone="danger">Cruce</StatusPill>}
        </div>
      ),
    },
    {
      id: 'shift',
      header: 'Turno',
      accessorFn: (section) => shiftLabel(section.shift),
      meta: { filterable: true, filterTitle: 'Turno' },
      cell: ({ row }) => <span className="text-ink">{shiftLabel(row.original.shift)}</span>,
    },
    {
      id: 'slots',
      header: 'Horarios y aulas',
      enableSorting: false,
      meta: { cardBlock: true },
      accessorFn: (section) => sortedSlots(section.slots).map(slotLabel).join(' | '),
      cell: ({ row }) => (
        <ul className="flex flex-col gap-0.5">
          {sortedSlots(row.original.slots).map((slot) => (
            <li key={`${slot.dayOfWeek}-${slot.startTime}-${slot.classroomId}`} className="whitespace-normal text-size-13 text-ink">
              {slotLabel(slot)}
            </li>
          ))}
        </ul>
      ),
    },
    {
      id: 'instructor',
      header: 'Docente',
      accessorFn: leadInstructorName,
      meta: { filterable: true, filterTitle: 'Docente', cardBlock: true },
      cell: ({ row }) => (
        <ul className="flex flex-col gap-0.5">
          {(row.original.instructors ?? []).map((instructor) => (
            <li key={`${instructor.name}-${instructor.role}`} className="whitespace-normal text-size-13 text-ink">
              {instructor.name}
            </li>
          ))}
          {!row.original.instructors?.length && <li className="text-size-13 text-ink-faint">Sin docente</li>}
        </ul>
      ),
    },
    {
      id: 'capacity',
      header: 'Cupo',
      accessorFn: (section) => section.capacity ?? 0,
      meta: { align: 'right' },
      cell: ({ row }) => <span className="tabular-nums text-ink">{row.original.capacity ?? '—'}</span>,
    },
  ];
}

function ConflictsNotice({ conflicts }) {
  return (
    <div role="alert" className="flex flex-col gap-2 rounded-2xl border border-danger/30 bg-danger-bg px-4 py-3 text-danger">
      <div className="font-heading text-size-14 font-semibold">
        {conflicts.length === 1 ? 'Hay 1 cruce para revisar antes de aprobar' : `Hay ${conflicts.length} cruces para revisar antes de aprobar`}
      </div>
      <ul className="flex flex-col gap-2">
        {conflicts.map((conflict, index) => (
          <li key={`${conflict.type}-${conflict.ownSection.sectionId}-${conflict.otherSection.sectionId}-${index}`} className="text-size-13 text-ink">
            <b className="font-semibold">{conflictTitle(conflict)}</b>
            <span className="block">{conflictDetail(conflict)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

const CYCLE_STATUS_LABEL = {
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  CLOSED: { label: 'Cerrado', tone: 'neutral' },
};

function DecidedNotice({ cycle, alreadyDecided }) {
  const pill = CYCLE_STATUS_LABEL[cycle.status] ?? ESTADO_TRAMITE_LABEL[cycle.status] ?? { label: 'Sin estado', tone: 'neutral' };
  return (
    <GlassCard className="flex flex-col gap-2 p-4">
      <div className="font-heading text-size-15 font-semibold text-ink">
        {alreadyDecided ? 'Otra persona de dirección ya decidió este trámite' : 'Este ciclo ya no está pendiente'}
      </div>
      <div className="flex flex-wrap items-center gap-2 text-size-13 text-ink-soft">
        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
        {cycle.decidedByName && <span>Decidió {cycle.decidedByName} · {formatearFecha(cycle.decidedAt) ?? '—'}</span>}
      </div>
      {cycle.rejectionReason && <p className="text-size-13 text-ink">Motivo: &ldquo;{cycle.rejectionReason}&rdquo;</p>}
    </GlassCard>
  );
}

export function DirectorOfertaCicloDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const cycleQuery = useCycle(id);
  const sectionsQuery = useCicloSecciones(id);
  const conflictsQuery = useCycleConflicts(id);
  const approve = useApproveCycle(id);
  const reject = useRejectCycle(id);
  const [confirming, setConfirming] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [alreadyDecided, setAlreadyDecided] = useState(false);

  if (cycleQuery.isError) {
    return <UnavailableRequestState error={cycleQuery.error} backTo={OFFER_APPROVALS_PATH} backLabel="Oferta por aprobar" onRetry={cycleQuery.refetch} />;
  }
  const cycle = cycleQuery.data;
  if (cycleQuery.isLoading || !cycle) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  const sections = sectionsQuery.data ?? [];
  const conflicts = conflictsQuery.data?.conflicts ?? [];
  const pending = cycle.status === 'PENDING_APPROVAL' && !alreadyDecided;
  const deciding = approve.isPending || reject.isPending;

  function handleFailure(failure) {
    toast.error(decisionErrorMessage(failure));
    if (isAlreadyDecided(failure)) {
      setAlreadyDecided(true);
      setConfirming(false);
      setRejecting(false);
    }
  }

  async function confirmApprove() {
    try {
      await approve.mutateAsync();
      toast.success('Ciclo aprobado');
      navigate(OFFER_APPROVALS_PATH);
    } catch (failure) {
      handleFailure(failure);
    }
  }

  async function confirmReject(reason) {
    try {
      await reject.mutateAsync(reason);
      setRejecting(false);
      toast.success('Ciclo rechazado');
      navigate(OFFER_APPROVALS_PATH);
      return true;
    } catch (failure) {
      handleFailure(failure);
      return false;
    }
  }

  return (
    <PageContainer>
      <Link to={OFFER_APPROVALS_PATH} className="flex w-fit items-center gap-1.5 text-size-13 font-semibold text-ink-soft hover:text-ink">
        ← Volver a Oferta por aprobar
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="font-heading text-2xl font-bold text-ink">{cycleTargetName(cycle)}</h1>
          <p className="mt-0.5 text-sm text-ink-soft">
            {cycleTargetKind(cycle)} · {cycle.termName}
          </p>
          <p className="mt-0.5 text-size-13 text-ink-faint">
            Enviado por {cycle.submittedByName ?? '—'} · {formatearFecha(cycle.submittedAt) ?? '—'}
          </p>
        </div>
        {pending && <StatusPill tone="warning">Pendiente</StatusPill>}
      </div>

      {!pending && <DecidedNotice cycle={cycle} alreadyDecided={alreadyDecided} />}

      {pending && conflicts.length > 0 && <ConflictsNotice conflicts={conflicts} />}
      {pending && conflictsQuery.isError && (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-2xl bg-warning-bg px-4 py-3 text-size-13 text-ink">
          No pudimos comprobar los cruces de aulas y docentes.
          <Button variant="outline" size="sm" onClick={() => conflictsQuery.refetch()}>Reintentar</Button>
        </div>
      )}

      <GlassCard className="flex flex-col gap-2 p-2">
        <h2 className="px-3 pt-2 font-heading text-size-16 font-semibold text-ink">Comisiones del ciclo</h2>
        <DataTable
          columns={buildSectionColumns(conflictedSectionIds(conflicts))}
          data={sections}
          getRowId={(section) => String(section.id)}
          isLoading={sectionsQuery.isLoading}
          isError={sectionsQuery.isError}
          onRetry={sectionsQuery.refetch}
          errorMessage="No pudimos cargar las comisiones."
          minWidth={760}
        />
      </GlassCard>

      {pending && (
        <StickyActionBar
          summary={confirming ? '¿Aprobar el ciclo?' : approvalSummary(cycle.sectionCount)}
          hint={confirming ? 'Las comisiones se activan al confirmar.' : 'La decisión le llega a Preceptoría.'}
        >
          {confirming ? (
            <>
              <Button variant="outline" disabled={deciding} onClick={() => setConfirming(false)}>Cancelar</Button>
              <Button variant="gradient" loading={approve.isPending} onClick={confirmApprove}>Confirmar aprobación</Button>
            </>
          ) : (
            <>
              <Button variant="outline" disabled={deciding} onClick={() => setRejecting(true)}>Rechazar</Button>
              <Button variant="gradient" disabled={deciding} onClick={() => setConfirming(true)}>Aprobar ciclo</Button>
            </>
          )}
        </StickyActionBar>
      )}

      <ModalMotivo
        open={rejecting}
        onClose={() => setRejecting(false)}
        onConfirmar={confirmReject}
        confirmando={reject.isPending}
        requerido
        minLength={MIN_REJECTION_REASON_LENGTH}
        showCounter
        titulo="Rechazar ciclo"
        descripcion="Preceptoría recibe este motivo para corregir y volver a enviar el ciclo."
        placeholder="Qué hay que corregir"
        confirmarLabel="Rechazar ciclo"
      />
    </PageContainer>
  );
}
