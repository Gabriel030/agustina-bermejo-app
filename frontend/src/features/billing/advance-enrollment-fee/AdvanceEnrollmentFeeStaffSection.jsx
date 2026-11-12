import { useState } from 'react';
import { toast } from 'sonner';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { useEstadoMatriculaDeInscripcion } from '@/features/identity';
import { formatDate, formatMoney, invoiceDisplayStatus, invoiceStatusInfo, isAdvanceEnrollmentFeeInvoice } from '@/lib/billing';
import { advanceErrorMessage, advanceKindOf, advanceOf, advanceYearOf, formatLongDate } from './advanceEnrollmentFee';
import { useRequestAdvanceEnrollmentFee } from './useAdvanceEnrollmentFee';

const BLOCKED_REASONS = {
  debt: 'El alumno tiene cuotas vencidas: tiene que regularizarlas primero.',
};

function GenerateDialog({ open, onOpenChange, enrollmentId, advance, concept }) {
  const [message, setMessage] = useState('');
  const mutation = useRequestAdvanceEnrollmentFee(enrollmentId, 'staff');

  function confirm() {
    setMessage('');
    mutation.mutate(undefined, {
      onSuccess: () => {
        toast.success(`Generaste la ${concept.toLowerCase()}. Se cobra desde la caja.`);
        onOpenChange(false);
      },
      onError: (error) => setMessage(advanceErrorMessage(error)),
    });
  }

  return (
    <ConfirmActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={`Generar la ${concept.toLowerCase()}`}
      description={`Se genera la factura por ${formatMoney(advance.amount)}, con vencimiento el ${formatDate(advance.dueDate)}. Todavía no se cobra.`}
      confirmLabel="Generar la factura"
      confirmVariant="gradient"
      pending={mutation.isPending}
      onConfirm={confirm}
    >
      {message && (
        <p role="alert" className="text-sm text-danger">
          {message}
        </p>
      )}
    </ConfirmActionDialog>
  );
}

export function AdvanceEnrollmentFeeStaffSection({ enrollmentId, invoices = [] }) {
  const { usuario } = useAuth();
  const [confirming, setConfirming] = useState(false);
  const canGenerate = (usuario?.roles ?? []).some((role) => role === 'ADMINISTRATIVO' || role === 'DIRECTOR');
  const statusQuery = useEstadoMatriculaDeInscripcion(canGenerate ? enrollmentId : null);

  if (!canGenerate) return null;
  if (statusQuery.isLoading) return <Skeleton className="h-10 w-full rounded-xl" />;
  if (statusQuery.isError) return null;

  const advance = advanceOf(statusQuery.data);
  const kind = advanceKindOf(advance);
  if (kind === 'hidden') return null;

  const year = advanceYearOf(advance);
  const concept = year ? `Matrícula ${year}` : 'Matrícula del año siguiente';
  const issuedInvoice = invoices.find(isAdvanceEnrollmentFeeInvoice);
  const issuedInfo = issuedInvoice ? invoiceStatusInfo(invoiceDisplayStatus(issuedInvoice)) : null;

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-xl bg-neutral-bg px-4 py-3 text-size-13 text-ink-soft">
      <span className="font-semibold text-ink">{concept}</span>
      {kind === 'issued' && issuedInfo && <StatusPill tone={issuedInfo.tone}>{issuedInfo.label}</StatusPill>}
      {kind === 'issued' && !issuedInfo && <StatusPill tone="neutral">Generada</StatusPill>}
      {kind !== 'issued' && <StatusPill tone="neutral">No emitida</StatusPill>}
      {kind === 'debt' && <span>{BLOCKED_REASONS.debt}</span>}
      {kind === 'early' && <span>Se puede generar desde el {formatLongDate(advance.opensOn)}.</span>}
      {kind === 'available' && (
        <>
          <span className="flex-1">Al precio cargado: {formatMoney(advance.amount)}. Vence el {formatDate(advance.dueDate)}.</span>
          <Button variant="outline" size="sm" onClick={() => setConfirming(true)}>
            {`Generar la matrícula ${year ?? ''}`.trim()}
          </Button>
          {confirming && (
            <GenerateDialog open onOpenChange={setConfirming} enrollmentId={enrollmentId} advance={advance} concept={concept} />
          )}
        </>
      )}
    </div>
  );
}
