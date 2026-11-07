import { useState } from 'react';
import { Banknote } from 'lucide-react';
import { toast } from 'sonner';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useAuth } from '@/context/AuthContext';
import { formatDateTime, formatMoney } from '@/lib/billing';
import { isUnknownOutcome, useIdempotencyKey } from '../idempotency';
import { DISCARD_REASON_LABELS, INELIGIBLE_REASONS, REQUEST_STATUS, liveRequestOf } from './annualUpfront';
import { useAnnualUpfrontQuote, useAnnualUpfrontRequests, useRegisterAnnualUpfrontCashPayment } from './useAnnualUpfront';

const RECEIPT_MAX_LENGTH = 100;

const CASH_ERRORS = {
  ANNUAL_UPFRONT_PAYMENT_EXPIRED: 'El plazo del pedido venció. Actualizá la pantalla.',
  ANNUAL_UPFRONT_PAYMENT_ALREADY_INITIATED: 'El pago de este pedido ya se inició. Actualizá la pantalla.',
  ANNUAL_UPFRONT_RECEIPT_AMOUNT_MUST_BE_TOTAL: 'El cobro tiene que ser por el total del año.',
  CASH_PAYMENT_RECEIPT_NUMBER_INVALID: 'El número de recibo no puede superar los 100 caracteres.',
  IDEMPOTENCY_KEY_CONFLICT: 'Este cobro ya se había enviado con otros datos. Revisá el estado de cuenta antes de volver a cobrar.',
};

const UNKNOWN_OUTCOME_MESSAGE =
  'No pudimos confirmar si el cobro se registró. Revisá el estado de cuenta antes de volver a cobrar. Si reintentás desde acá, no se duplica.';

function cashErrorMessage(error) {
  const codes = [error?.code, ...(error?.violations ?? []).map((violation) => violation.code)].filter(Boolean);
  const known = codes.find((code) => CASH_ERRORS[code]);
  return known ? CASH_ERRORS[known] : (error?.message ?? 'No pudimos registrar el cobro. Probá de nuevo.');
}

function CashDialog({ studentId, request, keyFor, onClose }) {
  const [receiptNumber, setReceiptNumber] = useState('');
  const [message, setMessage] = useState('');
  const [unknownOutcome, setUnknownOutcome] = useState(false);
  const mutation = useRegisterAnnualUpfrontCashPayment(studentId, request.id);
  const tooLong = receiptNumber.trim().length > RECEIPT_MAX_LENGTH;

  function confirm() {
    const body = { amount: request.total };
    if (receiptNumber.trim()) body.receiptNumber = receiptNumber.trim();
    setMessage('');
    mutation.mutate(
      { key: keyFor(body), body },
      {
        onSuccess: () => {
          toast.success('Cobraste el año completo en efectivo.');
          onClose();
        },
        onError: (error) => {
          const unknown = isUnknownOutcome(error);
          setUnknownOutcome(unknown);
          setMessage(unknown ? UNKNOWN_OUTCOME_MESSAGE : cashErrorMessage(error));
        },
      },
    );
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !mutation.isPending && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cobrar el año en efectivo</DialogTitle>
          <DialogDescription>El cobro confirma el año completo y no se deshace desde acá.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between rounded-xl bg-neutral-bg px-4 py-3">
            <span className="text-sm text-ink-soft">Cobrás</span>
            <span className="text-xl font-semibold tabular-nums text-ink">{formatMoney(request.total)}</span>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="annual-cash-receipt">Número de recibo (opcional)</Label>
            <Input id="annual-cash-receipt" value={receiptNumber} onChange={(event) => setReceiptNumber(event.target.value)} aria-invalid={tooLong} />
            {tooLong && <p className="text-xs text-danger">{CASH_ERRORS.CASH_PAYMENT_RECEIPT_NUMBER_INVALID}</p>}
          </div>
          {message && (
            <p role="alert" className="text-sm text-danger">
              {message}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={mutation.isPending}>
            Cancelar
          </Button>
          <Button variant="gradient" onClick={confirm} loading={mutation.isPending} disabled={tooLong}>
            {unknownOutcome ? 'Reintentar cobro' : 'Cobrar y confirmar el año'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function AnnualUpfrontStaffSection({ enrollmentId, studentId }) {
  const { usuario } = useAuth();
  const [collecting, setCollecting] = useState(false);
  const keyFor = useIdempotencyKey();
  const canCollect = (usuario?.roles ?? []).some((role) => role === 'ADMINISTRATIVO' || role === 'DIRECTOR');
  const requestsQuery = useAnnualUpfrontRequests(enrollmentId, 'staff', canCollect);
  const quoteQuery = useAnnualUpfrontQuote(enrollmentId, 'staff', canCollect);

  if (!canCollect) return null;

  if (requestsQuery.isLoading || quoteQuery.isLoading) return <Skeleton className="h-10 w-full rounded-xl" />;
  if (requestsQuery.isError) {
    return (
      <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
        <span>No pudimos cargar el pago anual.</span>
        <Button variant="outline" size="sm" onClick={() => requestsQuery.refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }

  const requests = requestsQuery.data ?? [];
  const live = liveRequestOf(requests);

  if (!live) {
    const eligibility = quoteQuery.data;
    if (!eligibility || eligibility.reasonCode === 'ENROLLMENT_NOT_ACTIVE') return null;
    const known = INELIGIBLE_REASONS[eligibility.reasonCode];
    const lastDiscarded = [...requests].sort((a, b) => b.id - a.id)[0];
    return (
      <div className="flex flex-wrap items-center gap-2 rounded-xl bg-neutral-bg px-4 py-3 text-[13px] text-ink-soft">
        <span className="font-semibold text-ink">Pago anual</span>
        <StatusPill tone={eligibility.eligible ? 'accent' : (known?.tone ?? 'neutral')}>
          {eligibility.eligible ? 'Puede pedirlo' : (known?.label ?? 'No disponible')}
        </StatusPill>
        {lastDiscarded && <span>Último pedido: {DISCARD_REASON_LABELS[lastDiscarded.discardReason] ?? 'descartado'}.</span>}
      </div>
    );
  }

  const status = REQUEST_STATUS[live.status] ?? { label: live.status, tone: 'neutral' };
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="font-semibold text-ink">Pago anual {live.lectiveYear ?? ''}</span>
          <StatusPill tone={status.tone}>{status.label}</StatusPill>
        </div>
        <span className="text-lg font-semibold tabular-nums text-ink">{formatMoney(live.total)}</span>
      </div>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-1 text-[13px] sm:grid-cols-2">
        <div className="flex gap-2">
          <dt className="text-ink-faint">Pedido por el alumno</dt>
          <dd className="text-ink">{formatDateTime(live.acceptedAt)}</dd>
        </div>
        {live.status === 'AWAITING_PAYMENT' && (
          <div className="flex gap-2">
            <dt className="text-ink-faint">Plazo de pago</dt>
            <dd className="text-ink">{formatDateTime(live.paymentDeadline)}</dd>
          </div>
        )}
        <div className="flex gap-2">
          <dt className="text-ink-faint">Condición aceptada</dt>
          <dd className="text-ink">{live.acceptedTermsVersion}</dd>
        </div>
      </dl>
      {live.status === 'PAYMENT_UNDER_REVIEW' && (
        <p className="text-[13px] text-ink-soft">El alumno subió un comprobante. Se cobra desde la bandeja de comprobantes.</p>
      )}
      {live.status === 'AWAITING_PAYMENT' && (
        <Button variant="outline" className="w-full sm:w-fit" onClick={() => setCollecting(true)}>
          <Banknote size={14} className="mr-1.5" />
          Cobrar en efectivo
        </Button>
      )}
      {collecting && <CashDialog studentId={studentId} request={live} keyFor={keyFor} onClose={() => setCollecting(false)} />}
    </div>
  );
}
