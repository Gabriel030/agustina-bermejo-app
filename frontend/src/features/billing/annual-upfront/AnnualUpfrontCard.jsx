import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { ChevronDown, ChevronUp, Info } from 'lucide-react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { paymentOriginState } from '@/hooks/usePaymentReturn';
import { formatDate, formatDateTime, formatMoney } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';
import { isUnknownOutcome, useIdempotencyKey } from '../idempotency';
import { TermsAcceptanceRow } from './terms/TermsAcceptanceRow';
import { TERMS_KEY, useCurrentTerms } from './terms/useAnnualUpfrontTerms';
import {
  DISCARD_REASON_LABELS,
  INELIGIBLE_REASONS,
  installmentsSummary,
  lastDiscardedRequestOf,
  liveRequestOf,
  periodLabel,
  REQUEST_STATUS,
} from './annualUpfront';
import {
  useAnnualUpfrontQuote,
  useAnnualUpfrontRequests,
  useDiscardAnnualUpfront,
  useRequestAnnualUpfront,
} from './useAnnualUpfront';

const REQUEST_ERRORS = {
  ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED: 'Para pedir el año tenés que aceptar los términos.',
  ANNUAL_UPFRONT_TERMS_OUTDATED: 'Cambiaron los términos mientras pedías. Leelos de nuevo y volvé a aceptar.',
  ANNUAL_UPFRONT_REQUEST_WINDOW_CLOSED: 'Ya pasó la fecha límite para pedir el año completo.',
  ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED: 'Ya hay un pedido del año completo. Actualizamos la pantalla.',
  OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT: 'Tenés cuotas vencidas: regularizalas para pedir el año.',
  NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT: 'Ya no está disponible: alguna cuota del año ya se emitió.',
  ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP: 'Con una beca activa no se puede pedir el año completo.',
  IDEMPOTENCY_KEY_CONFLICT: 'Este pedido ya se había enviado con otros datos. Actualizá la pantalla.',
};

const DISCARD_ERRORS = {
  ANNUAL_UPFRONT_REQUEST_NOT_FOUND: 'No encontramos el pedido. Actualizamos la pantalla.',
  ILLEGAL_ANNUAL_UPFRONT_REQUEST_TRANSITION: 'El pedido ya cambió de estado y no se puede descartar. Actualizamos la pantalla.',
};

const UNKNOWN_OUTCOME_MESSAGE =
  'No pudimos confirmar si el pedido se registró. Si reintentás desde acá no se duplica.';

const DISCARDED_TITLES = {
  DEADLINE_EXPIRED: 'Pedido vencido',
  PAYMENT_REJECTED: 'Pago rechazado',
  CANCELLED_BY_STUDENT: 'Pedido descartado',
  WITHDRAWAL: 'Pedido descartado',
};

function errorMessageOf(error) {
  const codes = [error?.code, ...(error?.violations ?? []).map((violation) => violation.code)].filter(Boolean);
  const known = codes.find((code) => REQUEST_ERRORS[code]);
  return known ? REQUEST_ERRORS[known] : (error?.message ?? 'No pudimos completar el pedido. Probá de nuevo.');
}

function InstallmentsList({ lines }) {
  const [open, setOpen] = useState(false);
  const summary = installmentsSummary(lines);
  if (!summary) return null;
  const Icon = open ? ChevronUp : ChevronDown;
  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex w-fit items-center gap-1 text-[13px] font-semibold text-primary"
      >
        {summary.amount ? `${summary.count} cuotas de ${formatMoney(summary.amount)}` : `${summary.count} cuotas`}
        <Icon size={14} />
      </button>
      {open && (
        <ul className="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
          {lines.map((line) => (
            <li key={line.lineNumber} className="flex justify-between gap-3 rounded-lg bg-neutral-bg px-3 py-1.5 text-[13px]">
              <span className="capitalize text-ink-soft">{periodLabel(line.period)}</span>
              <span className="tabular-nums text-ink">{formatMoney(line.finalAmount)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function RequestDialog({ enrollmentId, quote, keyFor, onClose }) {
  const queryClient = useQueryClient();
  const terms = useCurrentTerms();
  const [acceptedVersion, setAcceptedVersion] = useState(null);
  const [message, setMessage] = useState('');
  const [unknownOutcome, setUnknownOutcome] = useState(false);
  const mutation = useRequestAnnualUpfront(enrollmentId);
  const shownVersion = terms.data?.version ?? null;
  const accepted = shownVersion != null && acceptedVersion === shownVersion;

  function submit() {
    const body = { termsAccepted: true, termsVersion: shownVersion };
    setMessage('');
    mutation.mutate(
      { key: keyFor(body), body },
      {
        onSuccess: () => {
          toast.success('Pedimos el año completo. Falta el pago.');
          onClose();
        },
        onError: (error) => {
          const unknown = isUnknownOutcome(error);
          setUnknownOutcome(unknown);
          setMessage(unknown ? UNKNOWN_OUTCOME_MESSAGE : errorMessageOf(error));
          if (error?.code === 'ANNUAL_UPFRONT_TERMS_OUTDATED') {
            setAcceptedVersion(null);
            queryClient.invalidateQueries({ queryKey: TERMS_KEY });
          }
        },
      },
    );
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !mutation.isPending && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Pedir el año completo</DialogTitle>
          <DialogDescription>Ahora no pagás nada: primero reservamos el precio y después tenés un plazo para pagar.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between rounded-xl bg-neutral-bg px-4 py-3">
            <span className="text-sm text-ink-soft">Total del año</span>
            <span className="text-xl font-semibold tabular-nums text-ink">{formatMoney(quote.total)}</span>
          </div>
          <p className="text-[13px] text-ink-soft">
            Vas a tener hasta el {formatDateTime(quote.paymentDeadline)} para pagar por transferencia o en efectivo en administración.
          </p>
          <TermsAcceptanceRow
            terms={terms}
            checked={accepted}
            onCheckedChange={(checked) => {
              setAcceptedVersion(checked ? shownVersion : null);
              if (checked) setMessage('');
            }}
          />
          {message && (
            <p role="alert" className="text-sm text-danger">
              {message}
            </p>
          )}
        </div>
        {!accepted && (
          <p id="annual-terms-reason" className="text-xs text-ink-faint">
            Aceptá los términos para pedir el año.
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={mutation.isPending}>
            Cancelar
          </Button>
          <Button
            variant="gradient"
            onClick={submit}
            loading={mutation.isPending}
            disabled={!accepted}
            aria-describedby={accepted ? undefined : 'annual-terms-reason'}
          >
            {unknownOutcome ? 'Reintentar pedido' : 'Pedir el año completo'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function DiscardDialog({ request, onClose }) {
  const mutation = useDiscardAnnualUpfront();
  const [message, setMessage] = useState('');

  function discard() {
    setMessage('');
    mutation.mutate(request.id, {
      onSuccess: () => {
        toast.success('Descartaste el pedido del año.');
        onClose();
      },
      onError: (error) => setMessage(DISCARD_ERRORS[error?.code] ?? error?.message ?? 'No pudimos descartar el pedido. Probá de nuevo.'),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !mutation.isPending && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Descartar el pedido</DialogTitle>
          <DialogDescription>Liberás el lugar y el precio reservado. Mientras estés a tiempo, podés volver a pedirlo.</DialogDescription>
        </DialogHeader>
        {message && (
          <p role="alert" className="text-sm text-danger">
            {message}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={mutation.isPending}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={discard} loading={mutation.isPending}>
            Descartar pedido
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Frame({ title, pill, children }) {
  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="font-heading text-[15px] font-semibold text-ink">{title}</div>
        {pill}
      </div>
      {children}
    </GlassCard>
  );
}

function Offer({ enrollmentId, programName, eligibility, lastDiscarded, primary }) {
  const [requesting, setRequesting] = useState(false);
  const keyFor = useIdempotencyKey();
  const { quote, requestDeadline } = eligibility;
  const saving = quote.discount?.totalSaving;
  const hasSaving = saving && moneyValue(saving) > 0;
  return (
    <Frame title={`Pagá el año completo · ${programName}`} pill={!quote.discount && <StatusPill tone="neutral">Sin promoción</StatusPill>}>
      {lastDiscarded && (
        <div className="flex items-start gap-2 rounded-xl bg-neutral-bg px-3 py-2 text-[13px] text-ink-soft">
          <Info size={14} className="mt-0.5 shrink-0" />
          <span>
            {DISCARDED_TITLES[lastDiscarded.discardReason] ?? 'Pedido anterior descartado'}
            {lastDiscarded.discardReason && DISCARD_REASON_LABELS[lastDiscarded.discardReason] ? `. ${DISCARD_REASON_LABELS[lastDiscarded.discardReason]}.` : '.'}
          </span>
        </div>
      )}
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="text-3xl font-semibold tabular-nums text-ink">{formatMoney(quote.total)}</span>
        {hasSaving && <StatusPill tone="success">Ahorrás {formatMoney(saving)}</StatusPill>}
      </div>
      <InstallmentsList lines={quote.lines} />
      <p className="text-[13px] text-ink-soft">
        Podés elegirla una sola vez, hasta el {formatDate(requestDeadline)}. Si pagás una cuota, ya no.
      </p>
      <Button variant={primary ? 'gradient' : 'outline'} className="w-full sm:w-fit" onClick={() => setRequesting(true)}>
        {lastDiscarded ? 'Volver a pedir' : 'Pedir el año completo'}
      </Button>
      {requesting && <RequestDialog enrollmentId={enrollmentId} quote={quote} keyFor={keyFor} onClose={() => setRequesting(false)} />}
    </Frame>
  );
}

function Unavailable({ programName, eligibility, lastDiscarded }) {
  const known = INELIGIBLE_REASONS[eligibility.reasonCode];
  return (
    <Frame title={`Pagá el año completo · ${programName}`} pill={<StatusPill tone={known?.tone ?? 'neutral'}>{known?.label ?? 'No disponible'}</StatusPill>}>
      <p className="text-[13px] text-ink-soft">{eligibility.reasonMessage}</p>
      {lastDiscarded && (
        <p className="text-[13px] text-ink-faint">
          {DISCARDED_TITLES[lastDiscarded.discardReason] ?? 'Pedido descartado'}.
        </p>
      )}
    </Frame>
  );
}

function Tracking({ request, programName, primary }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [discarding, setDiscarding] = useState(false);
  const originState = paymentOriginState(pathname, 'Volver a Mis pagos');
  const goToReceipt = () => navigate(`/alumno/pagos/${request.invoiceId}/transferencia`, { state: originState });
  const title = `Pago anual · ${programName}`;

  if (request.status === 'PAYMENT_UNDER_REVIEW') {
    return (
      <Frame title={title} pill={<StatusPill tone="warning">Pago en revisión</StatusPill>}>
        <div className="text-2xl font-semibold tabular-nums text-ink">{formatMoney(request.total)}</div>
        <p className="text-[13px] text-ink-soft">Estamos revisando tu pago. Mientras tanto el pedido no vence.</p>
        <Button variant="outline" className="w-full sm:w-fit" onClick={goToReceipt}>
          Ver estado del pago
        </Button>
      </Frame>
    );
  }

  if (request.status === 'CONFIRMED') {
    return (
      <Frame title={title} pill={<StatusPill tone="success">Año confirmado</StatusPill>}>
        <div className="text-2xl font-semibold tabular-nums text-ink">{formatMoney(request.total)}</div>
        <p className="text-[13px] text-ink-soft">Pagaste el año completo. Las cuotas quedan cubiertas.</p>
      </Frame>
    );
  }

  if (request.status !== 'AWAITING_PAYMENT') {
    const fallback = REQUEST_STATUS[request.status] ?? { label: request.status, tone: 'neutral' };
    return (
      <Frame title={title} pill={<StatusPill tone={fallback.tone}>{fallback.label}</StatusPill>}>
        <div className="text-2xl font-semibold tabular-nums text-ink">{formatMoney(request.total)}</div>
      </Frame>
    );
  }

  return (
    <Frame title={title} pill={<StatusPill tone="warning">Esperando el pago</StatusPill>}>
      <div className="flex flex-wrap items-baseline gap-x-3">
        <span className="text-2xl font-semibold tabular-nums text-ink">{formatMoney(request.total)}</span>
        <span className="text-[13px] text-ink-soft">Pagalo antes del {formatDateTime(request.paymentDeadline)}</span>
      </div>
      <div className="rounded-xl bg-neutral-bg px-4 py-3 text-[13px] text-ink-soft">
        <p className="font-semibold text-ink">Cómo sigue</p>
        <ul className="mt-1 list-disc pl-5">
          <li>Transferí el total y subí el comprobante.</li>
          <li>O pagalo en efectivo en administración.</li>
          <li>Te avisamos 24 h antes del plazo.</li>
        </ul>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button variant={primary ? 'gradient' : 'outline'} onClick={goToReceipt}>
          Subir comprobante
        </Button>
        <Button variant="ghost" onClick={() => setDiscarding(true)}>
          Descartar pedido
        </Button>
      </div>
      {discarding && <DiscardDialog request={request} onClose={() => setDiscarding(false)} />}
    </Frame>
  );
}

export function AnnualUpfrontCard({ enrollmentId, programName, primary = false }) {
  const quoteQuery = useAnnualUpfrontQuote(enrollmentId, 'student');
  const requestsQuery = useAnnualUpfrontRequests(enrollmentId, 'student');

  if (quoteQuery.isLoading || requestsQuery.isLoading) {
    return <Skeleton className="h-32 w-full" />;
  }
  if (quoteQuery.isError || requestsQuery.isError) {
    return (
      <GlassCard className="flex flex-wrap items-center justify-between gap-2 p-5 text-sm text-danger">
        <span>No pudimos cargar el pago anual de {programName}.</span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            quoteQuery.refetch();
            requestsQuery.refetch();
          }}
        >
          Reintentar
        </Button>
      </GlassCard>
    );
  }

  const live = liveRequestOf(requestsQuery.data);
  if (live) return <Tracking request={live} programName={programName} primary={primary} />;

  const eligibility = quoteQuery.data;
  const lastDiscarded = lastDiscardedRequestOf(requestsQuery.data);
  if (eligibility.eligible && eligibility.quote) {
    return <Offer enrollmentId={enrollmentId} programName={programName} eligibility={eligibility} lastDiscarded={lastDiscarded} primary={primary} />;
  }
  if (!eligibility.reasonCode || eligibility.reasonCode === 'ENROLLMENT_NOT_ACTIVE') return null;
  return <Unavailable programName={programName} eligibility={eligibility} lastDiscarded={lastDiscarded} />;
}
