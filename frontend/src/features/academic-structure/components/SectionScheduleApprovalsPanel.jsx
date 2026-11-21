import { useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { ModalMotivo } from '@/components/ModalMotivo';
import { MIN_REJECTION_REASON_LENGTH } from '@/lib/aprobaciones';
import { describeError } from '@/api/describeError';
import {
  useApproveSectionScheduleChange,
  usePendingSectionScheduleRequests,
  useRejectSectionScheduleChange,
  useSectionScheduleChangePreview,
} from '@/features/academic-structure/hooks/useSectionScheduleApprovals';
import { sectionScheduleTitle, toScheduleSlotRequests } from '@/features/academic-structure/lib/sectionScheduleApprovals';
import { SectionScheduleApprovalRow } from '@/features/academic-structure/components/SectionScheduleApprovalRow';
import { SectionScheduleApproveDialog } from '@/features/academic-structure/components/SectionScheduleApproveDialog';

const APPROVE_FALLBACK = 'No se pudo aprobar el cambio de horario. Probá de nuevo.';
const REJECT_FALLBACK = 'No se pudo rechazar el cambio de horario. Probá de nuevo.';

export function SectionScheduleApprovalsPanel() {
  const pending = usePendingSectionScheduleRequests();
  const approve = useApproveSectionScheduleChange();
  const reject = useRejectSectionScheduleChange();
  const [approving, setApproving] = useState(null);
  const [rejecting, setRejecting] = useState(null);
  const [approveError, setApproveError] = useState(null);
  const [rejectError, setRejectError] = useState(null);

  const requests = pending.data ?? [];
  const busy = approve.isPending || reject.isPending;
  const preview = useSectionScheduleChangePreview(
    approving?.sectionId,
    approving ? toScheduleSlotRequests(approving.proposedSlots) : [],
    !!approving,
  );

  function startApproving(request) {
    setApproveError(null);
    setApproving(request);
  }

  function startRejecting(request) {
    setRejectError(null);
    setRejecting(request);
  }

  function closeApproving() {
    setApproving(null);
    setApproveError(null);
  }

  function closeRejecting() {
    setRejecting(null);
    setRejectError(null);
  }

  async function confirmApprove() {
    setApproveError(null);
    try {
      await approve.mutateAsync({ sectionId: approving.sectionId });
      setApproving(null);
    } catch (error) {
      setApproveError(describeError(error, { fallback: APPROVE_FALLBACK }).message);
    }
  }

  async function confirmReject(reason) {
    setRejectError(null);
    try {
      await reject.mutateAsync({ sectionId: rejecting.sectionId, reason });
      setRejecting(null);
    } catch (error) {
      setRejectError(describeError(error, { fallback: REJECT_FALLBACK }).message);
      return false;
    }
  }

  function renderContent() {
    if (pending.isLoading) {
      return <Skeleton className="h-24" />;
    }
    if (pending.isError) {
      return <EmptyState title="No pudimos cargar los cambios de horario" description="Probá recargar la página en un momento." className="py-6" />;
    }
    if (requests.length === 0) {
      return <EmptyState title="Ningún cambio de horario esperando tu decisión" className="py-6" />;
    }
    return (
      <ul className="flex flex-col gap-3">
        {requests.map((request) => (
          <SectionScheduleApprovalRow
            key={request.sectionId}
            request={request}
            disabled={busy}
            onApprove={startApproving}
            onReject={startRejecting}
          />
        ))}
      </ul>
    );
  }

  return (
    <section aria-labelledby="section-schedule-approvals-title">
      <GlassCard className="flex flex-col gap-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <h2 id="section-schedule-approvals-title" className="font-heading text-size-14-5 font-semibold text-ink">
            Horarios de comisión
          </h2>
          {!pending.isLoading && !pending.isError && (
            <span className="text-size-12 font-semibold tabular-nums text-ink-faint">
              {requests.length === 1 ? '1 pendiente' : `${requests.length} pendientes`}
            </span>
          )}
        </div>
        {renderContent()}
        <SectionScheduleApproveDialog
          request={approving}
          open={!!approving}
          onClose={closeApproving}
          onConfirm={confirmApprove}
          confirming={approve.isPending}
          errorMessage={approveError}
          preview={preview}
        />
        <ModalMotivo
          open={!!rejecting}
          onClose={closeRejecting}
          onConfirmar={confirmReject}
          confirmando={reject.isPending}
          errorMessage={rejectError}
          requerido
          titulo="Rechazar el cambio de horario"
          descripcion={rejecting ? `${sectionScheduleTitle(rejecting)}. Contale a quien lo pidió por qué no se aprueba — es obligatorio.` : undefined}
          placeholder="Motivo del rechazo"
          confirmarLabel="Rechazar horario"
          minLength={MIN_REJECTION_REASON_LENGTH}
          disableUntilMinLength
          showCounter
        />
      </GlassCard>
    </section>
  );
}
