import { useRef, useState } from 'react';
import { toast } from 'sonner';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Skeleton } from '@/components/Skeleton';
import { InfoTip } from '@/components/charts/InfoTip';
import { useAuth } from '@/context/AuthContext';
import { formatDate } from '@/lib/billing';
import { formatMoney } from '@/lib/utils';
import { usePreviewCashPayment, useRegisterCashPayment } from '@/hooks/useBilling';

const ADMINISTRATIVE_WINDOW_DAYS = 5;
const RECEIPT_MAX_LENGTH = 100;
const REASON_MAX_LENGTH = 500;
const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;

const FIELD_ERRORS = {
  CASH_PAYMENT_AMOUNT_INVALID: ['amount', 'Ingresá un monto mayor a 0.'],
  PAYMENT_DATE_IN_FUTURE: ['paidOn', 'La fecha no puede ser posterior a hoy.'],
  CASH_PAYMENT_DATE_TOO_OLD: ['paidOn', 'No podés cobrar con una fecha de más de 5 días atrás. Pedíselo a la dirección.'],
  CASH_PAYMENT_BACKDATE_REASON_REQUIRED: ['reason', 'Indicá el motivo de la fecha retroactiva.'],
  CASH_PAYMENT_BACKDATE_REASON_INVALID: ['reason', 'El motivo no puede superar los 500 caracteres.'],
  CASH_PAYMENT_RECEIPT_NUMBER_INVALID: ['receipt', 'El número de recibo no puede superar los 100 caracteres.'],
};

const PREVIEW_OUTDATED_NOTICE = 'La cuenta cambió, revisá el detalle actualizado.';
const KEY_CONFLICT_NOTICE = 'Este cobro ya se había registrado con otros datos. Revisá el estado de cuenta antes de volver a cobrar.';
const STUDENT_NOT_FOUND_MESSAGE = 'El alumno ya no está disponible. Volvé a buscarlo.';

function toIsoDate(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function daysAgoIso(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return toIsoDate(date);
}

function normalizeAmount(text) {
  const normalized = text.trim().replace(',', '.');
  if (!AMOUNT_PATTERN.test(normalized) || Number(normalized) <= 0) return null;
  return Number(normalized).toFixed(2);
}

function CoveredRows({ invoices, currency }) {
  return (
    <ul className="flex flex-col gap-1.5">
      {invoices.map((invoice) => (
        <li key={invoice.invoiceId} className="flex items-start justify-between gap-3 rounded-xl border border-border px-3 py-2">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-ink">{invoice.concept}</p>
            {invoice.dueDate && <p className="text-xs text-ink-faint">Vence el {formatDate(invoice.dueDate)}</p>}
          </div>
          <span className="shrink-0 text-sm font-semibold tabular-nums text-ink">
            {formatMoney({ amount: invoice.amount.amount, currency: invoice.amount.currency ?? currency })}
          </span>
        </li>
      ))}
    </ul>
  );
}

function FieldError({ message }) {
  if (!message) return null;
  return <p className="text-xs text-danger">{message}</p>;
}

export function CashPaymentDialog({ student, currency, totalDebt, onClose, onStudentGone }) {
  const { roles } = useAuth();
  const isDirector = roles.includes('DIRECTOR');
  const today = toIsoDate(new Date());
  const oldestAdministrativeDate = daysAgoIso(ADMINISTRATIVE_WINDOW_DAYS);

  const [amount, setAmount] = useState('');
  const [paidOn, setPaidOn] = useState(today);
  const [receipt, setReceipt] = useState('');
  const [reason, setReason] = useState('');
  const [step, setStep] = useState('form');
  const [preview, setPreview] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [notice, setNotice] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [globalError, setGlobalError] = useState('');
  const attempt = useRef({ signature: null, key: null });

  const previewMutation = usePreviewCashPayment(student.id);
  const registerMutation = useRegisterCashPayment(student.id);
  const busy = loadingPreview || registerMutation.isPending;

  const normalizedAmount = normalizeAmount(amount);
  const isBackdated = paidOn < oldestAdministrativeDate;
  const needsReason = isDirector && isBackdated;
  const studentName = `${student.apellido}, ${student.nombre}`;

  function validateForm() {
    const errors = {};
    if (!normalizedAmount) errors.amount = FIELD_ERRORS.CASH_PAYMENT_AMOUNT_INVALID[1];
    if (!paidOn) errors.paidOn = 'Indicá la fecha del cobro.';
    else if (paidOn > today) errors.paidOn = FIELD_ERRORS.PAYMENT_DATE_IN_FUTURE[1];
    else if (isBackdated && !isDirector) errors.paidOn = FIELD_ERRORS.CASH_PAYMENT_DATE_TOO_OLD[1];
    if (needsReason && !reason.trim()) errors.reason = FIELD_ERRORS.CASH_PAYMENT_BACKDATE_REASON_REQUIRED[1];
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  function handleFailure(error) {
    if (error?.code === 'STUDENT_NOT_FOUND') {
      toast.error(STUDENT_NOT_FOUND_MESSAGE);
      onStudentGone();
      return;
    }
    const mapped = FIELD_ERRORS[error?.code];
    if (mapped) {
      setFieldErrors({ [mapped[0]]: mapped[1] });
      setStep('form');
      return;
    }
    setGlobalError(error?.message ?? 'No pudimos completar el cobro. Probá de nuevo.');
  }

  async function requestPreview() {
    setLoadingPreview(true);
    try {
      const result = await previewMutation.mutateAsync({ amount: normalizedAmount, currency });
      setPreview(result);
      return true;
    } catch (error) {
      handleFailure(error);
      return false;
    } finally {
      setLoadingPreview(false);
    }
  }

  async function handleReview() {
    setGlobalError('');
    setNotice('');
    if (!validateForm()) return;
    if (await requestPreview()) setStep('review');
  }

  async function handleConfirm() {
    setGlobalError('');
    const payload = {
      amount: { amount: normalizedAmount, currency },
      paidOn,
      previewFingerprint: preview.previewFingerprint,
      ...(receipt.trim() && { receiptNumber: receipt.trim() }),
      ...(needsReason && { backdateReason: reason.trim() }),
    };
    const signature = JSON.stringify(payload);
    if (attempt.current.signature !== signature) {
      attempt.current = { signature, key: crypto.randomUUID() };
    }
    try {
      await registerMutation.mutateAsync({ key: attempt.current.key, payload });
      toast.success(`Cobro registrado por ${formatMoney({ amount: normalizedAmount, currency })}`);
      onClose(true);
    } catch (error) {
      if (error?.status === 409) {
        setNotice(error.code === 'IDEMPOTENCY_KEY_CONFLICT' ? KEY_CONFLICT_NOTICE : PREVIEW_OUTDATED_NOTICE);
        setPreview(null);
        await requestPreview();
        return;
      }
      handleFailure(error);
    }
  }

  function handleBack() {
    setStep('form');
    setNotice('');
    setGlobalError('');
  }

  const uncovered = preview && preview.coveredInvoices.length === 0 && preview.coveredByRemainingCredit.length === 0;
  const hasLeftover = preview && Number(preview.unallocatedAfterwards.amount) > 0;

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose(false); }}>
      <DialogContent
        className="max-h-[90dvh] overflow-y-auto sm:max-w-md"
        showCloseButton={!busy}
        onEscapeKeyDown={(event) => { if (busy) event.preventDefault(); }}
        onInteractOutside={(event) => event.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>{step === 'form' ? 'Cobrar en efectivo' : 'Revisar el cobro'}</DialogTitle>
          <DialogDescription>
            {studentName} · DNI {student.dni}
          </DialogDescription>
          <p className="text-xs text-ink-faint">{step === 'form' ? 'Paso 1 de 2 · Datos del cobro' : 'Paso 2 de 2 · Revisá antes de confirmar'}</p>
        </DialogHeader>

        {step === 'form' && (
          <div className="flex flex-col gap-4">
            {totalDebt && (
              <div className="flex items-center justify-between rounded-xl bg-neutral-bg px-4 py-3">
                <span className="flex items-center gap-1 text-sm text-ink-soft">
                  Deuda
                  <InfoTip label="Qué incluye la deuda">Cuotas vencidas y por vencer de todas las inscripciones. El cobro se imputa primero a las más viejas.</InfoTip>
                </span>
                <span className="text-lg font-semibold tabular-nums text-ink">{formatMoney(totalDebt)}</span>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="cash-payment-amount">Monto</Label>
                {totalDebt && (
                  <Button type="button" variant="ghost" size="xs" onClick={() => setAmount(totalDebt.amount)}>
                    Usar total
                  </Button>
                )}
              </div>
              <Input
                id="cash-payment-amount"
                inputMode="decimal"
                autoFocus
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                placeholder="0,00"
                aria-invalid={!!fieldErrors.amount}
              />
              <FieldError message={fieldErrors.amount} />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cash-payment-date">Fecha del cobro</Label>
              <Input
                id="cash-payment-date"
                type="date"
                value={paidOn}
                max={today}
                min={isDirector ? undefined : oldestAdministrativeDate}
                onChange={(event) => setPaidOn(event.target.value)}
                aria-invalid={!!fieldErrors.paidOn}
              />
              <FieldError message={fieldErrors.paidOn} />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cash-payment-receipt">N° de recibo (opcional)</Label>
              <Input
                id="cash-payment-receipt"
                maxLength={RECEIPT_MAX_LENGTH}
                value={receipt}
                onChange={(event) => setReceipt(event.target.value)}
                aria-invalid={!!fieldErrors.receipt}
              />
              <FieldError message={fieldErrors.receipt} />
            </div>

            {needsReason && (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="cash-payment-reason">Motivo de la fecha retroactiva</Label>
                <Textarea
                  id="cash-payment-reason"
                  rows={3}
                  maxLength={REASON_MAX_LENGTH}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  aria-invalid={!!fieldErrors.reason}
                />
                <div className="flex justify-between">
                  <FieldError message={fieldErrors.reason} />
                  <span className="ml-auto text-xs text-ink-faint">{reason.length}/{REASON_MAX_LENGTH}</span>
                </div>
              </div>
            )}

            {globalError && <p className="text-sm text-danger">{globalError}</p>}
          </div>
        )}

        {step === 'review' && (
          <div className="flex flex-col gap-4">
            {notice && (
              <div role="status" aria-live="polite" className="rounded-xl bg-warning-bg px-3 py-2 text-sm text-warning">
                {notice}
              </div>
            )}

            <div className="rounded-xl bg-neutral-bg px-4 py-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-ink-soft">Cobrás</span>
                <span className="text-xl font-semibold tabular-nums text-ink">{formatMoney({ amount: normalizedAmount, currency })}</span>
              </div>
              <p className="mt-1 text-xs text-ink-faint">
                {formatDate(paidOn)}
                {receipt.trim() && ` · Recibo ${receipt.trim()}`}
              </p>
            </div>

            {!preview && (
              <div className="flex flex-col gap-2">
                <Skeleton className="h-12 w-full rounded-xl" />
                <Skeleton className="h-12 w-full rounded-xl" />
              </div>
            )}

            {preview && (
              <div className="flex max-h-[40vh] flex-col gap-4 overflow-y-auto">
                {preview.coveredInvoices.length > 0 && (
                  <section className="flex flex-col gap-2">
                    <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-faint">Se imputa a</h3>
                    <CoveredRows invoices={preview.coveredInvoices} currency={currency} />
                  </section>
                )}
                {preview.coveredByRemainingCredit.length > 0 && (
                  <section className="flex flex-col gap-2">
                    <h3 className="flex items-center gap-1 text-xs font-semibold uppercase tracking-wide text-ink-faint">
                      También se paga con el saldo restante
                      <InfoTip label="Qué es el saldo restante">Cuotas de ofertas 100 % online: el efectivo no las paga en caja, se cubren después con el saldo a favor.</InfoTip>
                    </h3>
                    <CoveredRows invoices={preview.coveredByRemainingCredit} currency={currency} />
                  </section>
                )}
                {uncovered && (
                  <p className="text-sm text-ink-soft">
                    No hay cuotas por cubrir: {formatMoney({ amount: normalizedAmount, currency })} quedan a favor.
                  </p>
                )}
                {hasLeftover && !uncovered && (
                  <div className="flex items-center justify-between border-t border-border pt-2">
                    <span className="flex items-center gap-1 text-sm text-ink-soft">
                      Queda a favor
                      <InfoTip label="Qué es el saldo a favor">Se usa en el próximo cobro.</InfoTip>
                    </span>
                    <span className="text-sm font-semibold tabular-nums text-ink">
                      {formatMoney({ amount: preview.unallocatedAfterwards.amount, currency: preview.unallocatedAfterwards.currency ?? currency })}
                    </span>
                  </div>
                )}
              </div>
            )}

            {globalError && <p className="text-sm text-danger">{globalError}</p>}
          </div>
        )}

        <DialogFooter>
          {step === 'form' ? (
            <>
              <Button variant="outline" onClick={() => onClose(false)} disabled={busy}>
                Cancelar
              </Button>
              <Button variant="gradient" onClick={handleReview} loading={loadingPreview}>
                Revisar cobro
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={handleBack} disabled={busy}>
                Volver
              </Button>
              <Button variant="gradient" onClick={handleConfirm} loading={registerMutation.isPending} disabled={!preview || loadingPreview}>
                Confirmar cobro
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
