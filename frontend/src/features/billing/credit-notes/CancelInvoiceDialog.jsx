import { useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Info } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { InfoTip } from '@/components/charts/InfoTip';
import { useAuth } from '@/context/AuthContext';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { formatMoney, invoiceTitle } from '@/lib/billing';
import { inboxPath, roleBaseOf } from '../authorization/authorizationRequests';
import { useCancelInvoice, useCancellationPreview } from '../hooks/useCreditNotes';
import { CancellationEffects } from './CancellationEffects';
import { CreditNoteViolation } from './CreditNoteViolation';
import { ReasonCards } from './ReasonCards';
import {
  CANCELLATION_REASONS,
  JUSTIFICATION_MAX_LENGTH,
  JUSTIFICATION_REQUIRED_CODE,
  RECEIPT_REVIEW_CODES,
  STALE_INVOICE_CODES,
  blockOf,
  cancellationToastOf,
  creditNoteErrorOf,
  hasEnrollmentFeeWarning,
  isAuthorizationAck,
  isHardBlock,
  isLateFeeInvoice,
  receiptsLocation,
} from './creditNotes';

const PREVIEW_DEBOUNCE_MS = 400;
const HARD_SERVER_CODES = [...RECEIPT_REVIEW_CODES, ...STALE_INVOICE_CODES];

export function CancelInvoiceDialog({ invoice, studentId, onClose }) {
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const lateFee = isLateFeeInvoice(invoice);
  const [reason, setReason] = useState(lateFee ? 'LATE_FEE_ANNULMENT' : 'BILLING_ERROR');
  const [justification, setJustification] = useState('');
  const [fieldError, setFieldError] = useState(null);
  const [serverError, setServerError] = useState(null);

  const debouncedJustification = useDebouncedValue(justification, PREVIEW_DEBOUNCE_MS);
  const previewPayload = useMemo(
    () => ({ reason, justification: debouncedJustification.trim() }),
    [reason, debouncedJustification],
  );
  const previewQuery = useCancellationPreview(invoice.id, previewPayload, !serverError);
  const preview = previewQuery.data;
  const previewIsCurrent = !previewQuery.isFetching && debouncedJustification === justification;
  const mutation = useCancelInvoice();

  const previewBlock = useMemo(() => {
    const violation = (preview?.blockedBy ?? []).find(isHardBlock);
    return violation ? blockOf(violation) : null;
  }, [preview]);
  const hardServerError = serverError && HARD_SERVER_CODES.includes(serverError.code) ? serverError : null;
  const block = hardServerError ?? previewBlock;
  const executesImmediately = preview?.executesImmediately ?? roleBase === 'director';

  const trimmed = justification.trim();
  const tooLong = justification.length > JUSTIFICATION_MAX_LENGTH;
  const canSubmit = Boolean(preview) && previewIsCurrent && !previewQuery.isError && !block && trimmed.length > 0 && !tooLong && !mutation.isPending;

  const label = lateFee ? 'Anular recargo' : 'Cancelar cuota';
  const confirmLabel = executesImmediately ? label : 'Enviar a autorización';
  const subtitle = [
    invoiceTitle(invoice),
    invoice.invoiceNumber,
    `${formatMoney(invoice.outstandingAmount)} pendiente`,
    invoice.paymentStatus === 'OVERDUE' ? 'vencida' : null,
  ]
    .filter(Boolean)
    .join(' · ');

  function updateJustification(value) {
    setJustification(value);
    setFieldError(null);
    if (serverError && !HARD_SERVER_CODES.includes(serverError.code)) setServerError(null);
  }

  function handleReload() {
    queryClient.invalidateQueries({ queryKey: ['students'] });
    onClose();
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (!trimmed) {
      setFieldError('La justificación es obligatoria.');
      return;
    }
    if (!canSubmit) return;
    try {
      const result = await mutation.mutateAsync({ invoiceId: invoice.id, payload: { reason, justification: trimmed } });
      if (isAuthorizationAck(result)) {
        toast.warning('Pedido enviado. Todavía no se aplicó.', {
          description: 'La cuota sigue vigente hasta que lo autorice la dirección.',
          action: { label: 'Ver mis pedidos', onClick: () => navigate(inboxPath(roleBase)) },
        });
      } else {
        const { message, description } = cancellationToastOf({ result, reason, lateFee, preview });
        toast.success(message, { description });
      }
      onClose();
    } catch (error) {
      if (error?.code === JUSTIFICATION_REQUIRED_CODE) {
        setFieldError('La justificación es obligatoria.');
        return;
      }
      setServerError(creditNoteErrorOf(error));
    }
  }

  function handleOpenChange(open) {
    if (!open && !mutation.isPending) onClose();
  }

  return (
    <Dialog open onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{lateFee ? `Anular recargo · ${invoiceTitle(invoice)}` : `Cancelar cuota · ${invoiceTitle(invoice)}`}</DialogTitle>
          <DialogDescription>{subtitle}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          {!lateFee && (
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-1">
                <span id="cancel-reason-label" className="text-sm font-medium text-ink">
                  Motivo
                </span>
                <InfoTip>Define si la cuota se puede volver a emitir. Elegí con cuidado: no se cambia después.</InfoTip>
              </div>
              <ReasonCards labelledBy="cancel-reason-label" options={CANCELLATION_REASONS} value={reason} onChange={setReason} />
            </div>
          )}

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between gap-3">
              <Label htmlFor="cancel-justification">Justificación · obligatoria</Label>
              <span className={tooLong ? 'text-xs text-danger' : 'text-xs text-ink-faint'}>
                {justification.length}/{JUSTIFICATION_MAX_LENGTH}
              </span>
            </div>
            <Textarea
              id="cancel-justification"
              rows={3}
              value={justification}
              placeholder="Contá por qué se hace. Queda en el registro de auditoría."
              onChange={(event) => updateJustification(event.target.value)}
              aria-invalid={Boolean(fieldError) || tooLong}
              aria-describedby={fieldError ? 'cancel-justification-error' : undefined}
            />
            {(fieldError || tooLong) && (
              <p id="cancel-justification-error" className="text-xs text-danger">
                {fieldError ?? `Hasta ${JUSTIFICATION_MAX_LENGTH} caracteres.`}
              </p>
            )}
          </div>

          {hasEnrollmentFeeWarning(preview, reason) && (
            <div className="flex items-start gap-3 rounded-2xl border border-warning/30 bg-warning-bg px-4 py-3 text-sm text-ink">
              <AlertTriangle className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
              <span>
                <strong>Antes de reemitir la matrícula:</strong> ya hay otra matrícula vigente en el mismo ciclo lectivo, así que el descuento no se aplicaría a la nueva. Avisale a la dirección.
              </span>
            </div>
          )}

          <CancellationEffects preview={preview} isLoading={previewQuery.isFetching} lateFee={lateFee} />

          {previewQuery.isError && (
            <div role="alert" className="flex items-center justify-between gap-3 rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
              <span>No pudimos calcular el impacto.</span>
              <Button type="button" variant="outline" size="sm" onClick={() => previewQuery.refetch()}>
                Reintentar
              </Button>
            </div>
          )}

          <CreditNoteViolation
            error={block ?? serverError}
            receiptsTo={receiptsLocation(location.pathname, studentId)}
            onReload={handleReload}
          />

          {executesImmediately ? (
            <div className="flex items-start gap-3 rounded-2xl border border-border bg-neutral-bg px-4 py-3 text-sm text-ink">
              <Info className="mt-0.5 size-4 shrink-0 text-ink-soft" aria-hidden="true" />
              <span>Como director, se aplica de inmediato. No se puede deshacer.</span>
            </div>
          ) : (
            <div className="flex items-start gap-3 rounded-2xl border border-warning/30 bg-warning-bg px-4 py-3 text-sm text-ink">
              <Info className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
              <span>
                <strong>Necesita autorización de la dirección.</strong> La cuota sigue vigente hasta que la apruebe.
              </span>
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
              Descartar
            </Button>
            <Button type="submit" variant="destructive" loading={mutation.isPending} disabled={!canSubmit}>
              {confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
