import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ExternalLink } from 'lucide-react';
import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/context/AuthContext';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { formatDate, formatMoney, todayIsoDate } from '@/lib/billing';
import {
  useApprovalRevocationPreview,
  useChargebackPreview,
  useChargebackReversalPreview,
  useRegisterChargeback,
  useReverseChargeback,
  useRevokeApproval,
} from '../hooks/usePaymentReversals';
import { refundMethodLabel } from '../refunds/refundRequests';
import { PendingRefundsNotice } from './PendingRefundsNotice';
import { ReversalImpact } from './ReversalImpact';
import {
  BLOCKING_CODES,
  FIELD_BY_CODE,
  PENDING_REFUND_CODE,
  REVERSAL_ERROR_MESSAGES,
  REVERSAL_KINDS,
  REVERSAL_REFERENCE_MAX_LENGTH,
  REVOCATION_REASON_MAX_LENGTH,
  STALE_PAYMENT_CODES,
  isHardBlock,
  reversalErrorMessage,
  successMessage,
  validateReversalForm,
} from './paymentReversals';

const PREVIEW_DEBOUNCE_MS = 300;

const DESCRIPTIONS = {
  chargeback: 'Registrá el contracargo que informó el banco.',
  reversal: 'El banco devolvió el contracargo y el pago vuelve a quedar aprobado.',
  revocation: 'Dejás sin efecto una transferencia que se aprobó por error.',
};

function PaymentIdentity({ kind, payment }) {
  return (
    <dl className="flex flex-col gap-1.5 rounded-xl bg-neutral-bg px-4 py-3 text-sm">
      <div className="flex items-baseline justify-between gap-4">
        <dt className="shrink-0 text-xs text-ink-faint">Pago #{payment.id}</dt>
        <dd className="min-w-0 text-right text-ink">{refundMethodLabel(payment.methodType)}</dd>
      </div>
      <div className="flex items-baseline justify-between gap-4">
        <dt className="shrink-0 text-xs text-ink-faint">Monto</dt>
        <dd className="font-semibold tabular-nums text-ink">{formatMoney(payment.amount)}</dd>
      </div>
      <div className="flex items-baseline justify-between gap-4">
        <dt className="shrink-0 text-xs text-ink-faint">Pagado el</dt>
        <dd className="text-ink">{formatDate(payment.paidOn)}</dd>
      </div>
      {kind === 'reversal' && payment.chargeback && (
        <>
          <div className="flex items-baseline justify-between gap-4">
            <dt className="shrink-0 text-xs text-ink-faint">Contracargo del</dt>
            <dd className="text-ink">{formatDate(payment.chargeback.chargedBackOn)}</dd>
          </div>
          <div className="flex items-baseline justify-between gap-4">
            <dt className="shrink-0 text-xs text-ink-faint">Referencia</dt>
            <dd className="min-w-0 break-words text-right text-ink">{payment.chargeback.reference}</dd>
          </div>
        </>
      )}
    </dl>
  );
}

function FieldError({ id, message }) {
  return message ? (
    <p id={id} className="text-xs text-danger">
      {message}
    </p>
  ) : null;
}

export function ReversalDialog({ kind, payment, studentId, invoiceLabel, onClose }) {
  const definition = REVERSAL_KINDS[kind];
  const { usuario } = useAuth();
  const roleBase = usuario?.roles?.[0]?.toLowerCase();
  const today = todayIsoDate();
  const [form, setForm] = useState({ date: today, reference: '', reason: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [serverBlock, setServerBlock] = useState(null);

  const availability = payment.actions?.[definition.actionKey];
  const initialBlock =
    availability && !availability.allowed && availability.blockedBy?.code === PENDING_REFUND_CODE
      ? availability.blockedBy
      : null;
  const previewEnabled = !serverBlock && !initialBlock;

  const debouncedForm = useDebouncedValue(form, PREVIEW_DEBOUNCE_MS);
  const chargebackPreview = useChargebackPreview(
    payment.id,
    { amount: payment.amount, chargedBackOn: debouncedForm.date || today, reference: debouncedForm.reference.trim() },
    previewEnabled && kind === 'chargeback',
  );
  const reversalPreview = useChargebackReversalPreview(payment.chargeback?.chargebackId, previewEnabled && kind === 'reversal');
  const revocationPreview = useApprovalRevocationPreview(
    payment.id,
    { reason: debouncedForm.reason.trim() },
    previewEnabled && kind === 'revocation',
  );
  const previewQuery = { chargeback: chargebackPreview, reversal: reversalPreview, revocation: revocationPreview }[kind];
  const preview = previewQuery.data;

  const registerChargeback = useRegisterChargeback();
  const reverseChargeback = useReverseChargeback();
  const revokeApproval = useRevokeApproval();
  const mutation = { chargeback: registerChargeback, reversal: reverseChargeback, revocation: revokeApproval }[kind];

  const previewBlock = isHardBlock(preview) ? preview.blockedBy : null;
  const block = serverBlock ?? initialBlock ?? previewBlock;
  const previewIsCurrent = !previewQuery.isFetching && debouncedForm === form;
  const canConfirm = Boolean(preview) && previewIsCurrent && !previewQuery.isError && !block && !mutation.isPending;

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
  }

  function execute() {
    if (kind === 'chargeback') {
      return registerChargeback.mutateAsync({
        paymentId: payment.id,
        payload: { amount: payment.amount, chargedBackOn: form.date, reference: form.reference.trim() },
      });
    }
    if (kind === 'reversal') {
      return reverseChargeback.mutateAsync({ chargebackId: payment.chargeback.chargebackId });
    }
    return revokeApproval.mutateAsync({ paymentId: payment.id, payload: { reason: form.reason.trim() } });
  }

  function handleError(error) {
    const code = error?.code;
    if (BLOCKING_CODES.includes(code)) {
      setServerBlock({ code, message: REVERSAL_ERROR_MESSAGES[code] });
      toast.error(REVERSAL_ERROR_MESSAGES[code]);
      return;
    }
    if (STALE_PAYMENT_CODES.includes(code)) {
      toast.error(reversalErrorMessage(error));
      onClose();
      return;
    }
    if (FIELD_BY_CODE[code]) {
      setFieldErrors({ [FIELD_BY_CODE[code]]: REVERSAL_ERROR_MESSAGES[code] });
      return;
    }
    toast.error(reversalErrorMessage(error));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (!canConfirm) return;
    const errors = validateReversalForm(kind, form, payment, today);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    try {
      const result = await execute();
      toast.success(successMessage(kind, result));
      onClose();
    } catch (error) {
      handleError(error);
    }
  }

  function handleOpenChange(open) {
    if (!open && !mutation.isPending) onClose();
  }

  const refundsPath = `/${roleBase}/alumnos/${studentId}?view=financiero`;
  const blockedByRefunds = block?.code === PENDING_REFUND_CODE;

  return (
    <Dialog open onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{definition.title}</DialogTitle>
          <DialogDescription>{DESCRIPTIONS[kind]}</DialogDescription>
        </DialogHeader>

        {block ? (
          <div className="flex flex-col gap-4">
            <PaymentIdentity kind={kind} payment={payment} />
            <PendingRefundsNotice
              title={blockedByRefunds ? 'Todavía no se puede hacer' : 'No se puede hacer'}
              message={
                blockedByRefunds
                  ? 'Este pago tiene reembolsos pendientes. Cerralos primero: completalos, rechazalos o marcalos como fallidos.'
                  : (block.message ?? REVERSAL_ERROR_MESSAGES[block.code] ?? 'La operación no está disponible para este pago.')
              }
              parts={blockedByRefunds ? (payment.pendingRefunds?.parts ?? []) : []}
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>
                Cerrar
              </Button>
              {blockedByRefunds && (
                <Button asChild variant="outline">
                  <Link to={refundsPath}>
                    Ir a reembolsos
                    <ExternalLink aria-hidden="true" />
                  </Link>
                </Button>
              )}
            </DialogFooter>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
            <PaymentIdentity kind={kind} payment={payment} />

            {kind === 'chargeback' && (
              <>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="reversal-amount">Monto</Label>
                  <Input id="reversal-amount" value={formatMoney(payment.amount)} readOnly className="cursor-default tabular-nums" />
                  <p className="text-xs text-ink-faint">El contracargo siempre es por el total del pago.</p>
                </div>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="reversal-date">Fecha del contracargo</Label>
                    <Input
                      id="reversal-date"
                      type="date"
                      value={form.date}
                      min={payment.paidOn ?? undefined}
                      max={today}
                      onChange={(event) => updateField('date', event.target.value)}
                      aria-invalid={!!fieldErrors.date}
                      aria-describedby={fieldErrors.date ? 'reversal-date-error' : undefined}
                    />
                    <FieldError id="reversal-date-error" message={fieldErrors.date} />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="reversal-reference">Referencia del banco</Label>
                      <span className="text-xs text-ink-faint">
                        {form.reference.length}/{REVERSAL_REFERENCE_MAX_LENGTH}
                      </span>
                    </div>
                    <Input
                      id="reversal-reference"
                      value={form.reference}
                      maxLength={REVERSAL_REFERENCE_MAX_LENGTH}
                      placeholder="Ej.: CBK-88213"
                      onChange={(event) => updateField('reference', event.target.value)}
                      aria-invalid={!!fieldErrors.reference}
                      aria-describedby={fieldErrors.reference ? 'reversal-reference-error' : undefined}
                    />
                    <FieldError id="reversal-reference-error" message={fieldErrors.reference} />
                  </div>
                </div>
              </>
            )}

            {kind === 'revocation' && (
              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <Label htmlFor="reversal-reason">Motivo</Label>
                  <span className="text-xs text-ink-faint">
                    {form.reason.length}/{REVOCATION_REASON_MAX_LENGTH}
                  </span>
                </div>
                <Textarea
                  id="reversal-reason"
                  rows={3}
                  value={form.reason}
                  maxLength={REVOCATION_REASON_MAX_LENGTH}
                  placeholder="Por qué se revoca la aprobación"
                  onChange={(event) => updateField('reason', event.target.value)}
                  aria-invalid={!!fieldErrors.reason}
                  aria-describedby={fieldErrors.reason ? 'reversal-reason-error' : undefined}
                />
                <FieldError id="reversal-reason-error" message={fieldErrors.reason} />
              </div>
            )}

            <ReversalImpact kind={kind} preview={preview} isLoading={previewQuery.isFetching} invoiceLabel={invoiceLabel} />

            {previewQuery.isError && (
              <div role="alert" className="flex items-center justify-between gap-3 rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
                <span>No pudimos calcular el impacto.</span>
                <Button type="button" variant="outline" size="sm" onClick={() => previewQuery.refetch()}>
                  Reintentar
                </Button>
              </div>
            )}

            {definition.irreversible && (
              <div className="flex items-start gap-3 rounded-2xl border border-danger/30 bg-danger-bg px-4 py-3 text-sm text-ink">
                <AlertTriangle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
                <span>
                  Esta acción no se puede deshacer desde acá.
                  {kind === 'chargeback' && ' Si el banco devuelve el contracargo, se revierte aparte.'}
                </span>
              </div>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose} disabled={mutation.isPending}>
                Cancelar
              </Button>
              <Button type="submit" variant={definition.confirmVariant} loading={mutation.isPending} disabled={!canConfirm}>
                {definition.confirmLabel}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
