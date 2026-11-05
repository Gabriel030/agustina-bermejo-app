import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { usePaymentReturn } from '@/hooks/usePaymentReturn';
import { ChevronLeft, Copy, Check, Clock, CheckCircle, XCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Stepper } from '@/components/Stepper';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { InvoiceSummary } from '@/components/alumno/InvoiceSummary';
import { BankTransferReceiptForm } from '@/components/billing/BankTransferReceiptForm';
import { useAuth } from '@/context/AuthContext';
import { useMisSolicitudesCarrera } from '@/features/enrollment/hooks/useCatalogo';
import { usePaymentConfig, useMisComprobantes, usePriceBreakdown, useMyOpenInvoices } from '@/hooks/useBilling';
import { formatDate, formatMoney, invoiceConcept } from '@/lib/billing';
import {
  isPartialReceipt,
  latestReceiptCovering,
  orderedPayableInvoices,
  receiptTrackingRows,
} from '@/lib/bankTransferReceipt';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const PASOS = [
  { key: 'datos', label: 'Datos bancarios' },
  { key: 'comprobante', label: 'Subir comprobante' },
  { key: 'estado', label: 'Seguimiento' },
];

function useCopied() {
  const [copiado, setCopiado] = useState(false);
  function copiar(texto) {
    navigator.clipboard.writeText(texto).then(() => {
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    });
  }
  return { copiado, copiar };
}

function DatoField({ label, value }) {
  const { copiado, copiar } = useCopied();
  if (!value) return null;
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-faint">{label}</p>
        <p className="mt-0.5 font-mono font-semibold">{value}</p>
      </div>
      <button
        type="button"
        onClick={() => copiar(value)}
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border text-ink-faint transition-colors hover:border-brand hover:text-brand"
        title="Copiar"
        aria-label={`Copiar ${label}`}
      >
        {copiado ? <Check size={14} /> : <Copy size={14} />}
      </button>
    </div>
  );
}

function PasoDatos({ config, amount, onNext }) {
  return (
    <div className="flex flex-col gap-6">
      <GlassCard className="divide-y divide-border p-5">
        <DatoField label="Titular" value={config?.accountHolder} />
        <DatoField label="CBU" value={config?.bankCbu} />
        <DatoField label="Alias" value={config?.bankAlias} />
      </GlassCard>

      {config?.qrImageUrl && (
        <GlassCard className="flex flex-col items-center gap-3 p-5">
          <p className="text-sm font-semibold text-ink-faint">Código QR de pago</p>
          <img
            src={`${API_BASE}${config.qrImageUrl}`}
            alt="QR de pago"
            className="h-48 w-48 rounded-xl object-contain"
          />
        </GlassCard>
      )}

      <p className="text-sm text-ink-faint">
        {amount != null
          ? `Transferí exactamente ${formatMoney(amount)} y luego subí el comprobante en el siguiente paso.`
          : 'Realizá la transferencia por el monto exacto de la cuota y luego subí el comprobante en el siguiente paso.'}
      </p>

      <Button variant="gradient" onClick={onNext}>
        Ya transferí — subir comprobante
      </Button>
    </div>
  );
}

function PasoComprobante({ invoiceId, breakdown, onSuccess, onViewReceipt }) {
  const { usuario } = useAuth();
  const { invoices, isLoading } = useMyOpenInvoices();

  if (isLoading || !breakdown) return <Skeleton className="h-64" />;

  const { selectable, underReview } = orderedPayableInvoices(invoices);
  const position = selectable.findIndex((invoice) => invoice.id === Number(invoiceId));
  const fallback = [
    {
      id: Number(invoiceId),
      title: invoiceConcept(breakdown.feeType, breakdown.period),
      dueDate: null,
      outstanding: breakdown.remainingBalance,
      paymentStatus: breakdown.status,
    },
  ];
  const usingList = position >= 0;
  const holder = usuario ? `${usuario.nombre ?? ''} ${usuario.apellido ?? ''}`.trim() : '';

  return (
    <BankTransferReceiptForm
      invoices={usingList ? selectable : fallback}
      underReview={usingList ? underReview : []}
      initialCount={usingList ? position + 1 : 1}
      defaultHolderName={holder}
      onSubmitted={onSuccess}
      onViewReceipt={onViewReceipt}
    />
  );
}

const RECEIPT_STATUS = {
  PENDING_REVIEW: {
    label: 'Comprobante en revisión',
    icon: Clock,
    tone: 'warning',
    text: 'Lo estamos revisando. Te avisamos apenas esté listo.',
  },
  PARTIAL: {
    label: 'Revisamos parte de tu transferencia',
    icon: Clock,
    tone: 'warning',
    text: 'El monto que transferiste cubre solo las primeras cuotas. Las otras siguen pendientes.',
  },
  APPROVED: { label: 'Comprobante aprobado', icon: CheckCircle, tone: 'success', text: null },
  APPLIED_TO_DEBT: { label: 'Aprobado, aplicado a deuda anterior', icon: Clock, tone: 'warning', text: null },
  DATE_MISMATCH: {
    label: 'La fecha de la transferencia no coincide',
    icon: XCircle,
    tone: 'danger',
    text: 'La fecha que figura en el banco es distinta de la del día en que subiste el comprobante, así que no lo pudimos aprobar.',
  },
  REJECTED: {
    label: 'Comprobante rechazado',
    icon: XCircle,
    tone: 'danger',
    text: 'Revisó tu comprobante una persona de Secretaría y no lo pudo aprobar.',
  },
};

const TRACKING_PILL = {
  review: { tone: 'warning', label: 'En revisión' },
  paid: { tone: 'success', label: 'Pagada' },
  pending: { tone: 'neutral', label: 'Pendiente' },
};

const TRACKING_NOTE = {
  review: 'Incluida en tu comprobante en revisión',
  paid: 'Acreditada con tu comprobante',
  pending: 'Sigue pendiente de pago',
};

const TONE_CLASS = { warning: 'text-warning', success: 'text-success', danger: 'text-danger', neutral: 'text-ink-faint' };

function trackingKey(receipt) {
  if (receipt.status === 'REJECTED') return receipt.rejectionCode === 'TRANSFER_DATE_MISMATCH' ? 'DATE_MISMATCH' : 'REJECTED';
  if (receipt.status === 'PENDING_REVIEW' && isPartialReceipt(receipt)) return 'PARTIAL';
  return receipt.status;
}

function textoDePagoAprobado(solicitud) {
  if (!solicitud) return 'Registramos tu pago. Ya podés ver el detalle en Pagos.';
  if (solicitud.admissionInvoice?.paymentStatus !== 'PAID') {
    return 'Aprobamos tu comprobante, pero el monto acreditado todavía no alcanza a cubrir la matrícula de ingreso completa. Revisá el detalle en Mis pagos.';
  }
  if (solicitud.programEnrollmentId) return `Tu pago fue confirmado. Ya estás inscripto en ${solicitud.programName}.`;
  return `Tu pago fue confirmado. Estamos confirmando tu inscripción en ${solicitud.programName}.`;
}

function ReceiptDetails({ receipt }) {
  const entries = [
    ['Número de operación', receipt.operationNumber],
    ['Monto', receipt.declaredAmount ? formatMoney(receipt.declaredAmount) : null],
    ['Fecha', receipt.transferDate ? formatDate(receipt.transferDate) : null],
  ].filter(([, value]) => value);
  if (entries.length === 0) return null;
  return (
    <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1.5 border-t border-border pt-3 text-sm">
      {entries.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-ink-faint">{label}</dt>
          <dd className="text-right font-semibold text-ink">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function PasoEstado({ invoiceId, onResend }) {
  const { originLabel, goToOrigin } = usePaymentReturn();
  const { data: comprobantes, isLoading, isFetching } = useMisComprobantes();
  const comprobante = latestReceiptCovering(comprobantes, Number(invoiceId));
  const { data: solicitudes } = useMisSolicitudesCarrera();
  const solicitudDeMatricula = (solicitudes ?? []).find((s) => s.admissionInvoice?.id === parseInt(invoiceId, 10));

  if (isLoading || (isFetching && !comprobante)) return <Skeleton className="h-32" />;

  if (!comprobante) {
    return (
      <GlassCard className="p-6 text-center text-sm text-ink-faint">
        No se encontró el comprobante. Intentá recargar la página.
      </GlassCard>
    );
  }

  const key = trackingKey(comprobante);
  const info = RECEIPT_STATUS[key] ?? { label: comprobante.status, icon: Clock, tone: 'neutral', text: null };
  const Icon = info.icon;
  const rejected = comprobante.status === 'REJECTED';
  const rows = receiptTrackingRows(comprobante);
  const description = key === 'APPROVED' ? textoDePagoAprobado(solicitudDeMatricula) : info.text;

  return (
    <div className="flex flex-col gap-4">
      <GlassCard className="flex flex-col items-center gap-4 p-8 text-center">
        <Icon size={48} className={TONE_CLASS[info.tone]} aria-hidden="true" />
        <div>
          <p className="text-lg font-bold">{info.label}</p>
          {description && <p className="mt-1 text-sm text-ink-faint">{description}</p>}
          {key === 'REJECTED' && comprobante.rejectionReason && (
            <p className="mt-3 rounded-xl bg-danger-bg px-4 py-2 text-sm font-semibold text-danger">{comprobante.rejectionReason}</p>
          )}
        </div>
      </GlassCard>

      {(rows.length > 0 || comprobante.operationNumber) && (
        <GlassCard className="flex flex-col gap-3 p-5">
          {rows.length > 0 && (
            <>
              <h2 className="font-heading text-base font-bold text-ink">Tus cuotas</h2>
              <ul className="flex flex-col gap-1.5">
                {rows.map((row) => {
                  const pill = TRACKING_PILL[row.status];
                  const noteId = `tracking-note-${row.id}`;
                  return (
                    <li
                      key={row.id}
                      aria-describedby={noteId}
                      className="flex items-center justify-between gap-3 rounded-2xl border border-border px-4 py-3"
                    >
                      <span className="flex min-w-0 flex-col gap-0.5">
                        <span className="truncate text-sm font-semibold text-ink">{row.title}</span>
                        <span id={noteId} className="text-xs text-ink-faint">
                          {TRACKING_NOTE[row.status]}
                        </span>
                      </span>
                      <span className="flex shrink-0 items-center gap-3">
                        <span className="text-sm font-semibold tabular-nums text-ink">{formatMoney(row.outstanding)}</span>
                        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
                      </span>
                    </li>
                  );
                })}
              </ul>
            </>
          )}
          <ReceiptDetails receipt={comprobante} />
        </GlassCard>
      )}

      {rejected && (
        <div role="note" className="rounded-xl border border-warning/40 bg-warning-bg p-4 text-sm text-ink">
          <p className="font-bold">Tus cuotas siguen pendientes</p>
          <p className="mt-0.5 text-ink-soft">
            {key === 'DATE_MISMATCH'
              ? 'Si hiciste la transferencia otro día, avisale a Secretaría para que la carguen.'
              : 'Podés corregir los datos y volver a enviar el comprobante.'}
          </p>
        </div>
      )}

      {rejected ? (
        <div className="flex flex-col gap-2 sm:flex-row-reverse">
          <Button variant="gradient" onClick={onResend}>
            Corregir y reenviar
          </Button>
          <Button variant="outline" onClick={goToOrigin}>
            {originLabel}
          </Button>
        </div>
      ) : (
        <Button variant="gradient" onClick={goToOrigin}>
          {originLabel}
        </Button>
      )}
    </div>
  );
}

export function AlumnoPagarTransferencia() {
  const { invoiceId } = useParams();
  const { goTo } = usePaymentReturn();
  const { data: config, isLoading } = usePaymentConfig();
  const { data: breakdown } = usePriceBreakdown(invoiceId);
  const { data: receipts } = useMisComprobantes();
  const latestReceipt = latestReceiptCovering(receipts, Number(invoiceId));
  const hasReceiptToFollow = ['PENDING_REVIEW', 'REJECTED'].includes(latestReceipt?.status);
  const [chosenStep, setChosenStep] = useState(null);
  const [pasosVisitados, setPasosVisitados] = useState(['datos']);
  const pasoActual = chosenStep ?? (hasReceiptToFollow ? 'estado' : 'datos');
  const visitedSteps = hasReceiptToFollow ? PASOS.map((step) => step.key) : pasosVisitados;
  const stepperRef = useRef(null);
  const previousStep = useRef(pasoActual);

  useEffect(() => {
    if (previousStep.current === pasoActual) return;
    previousStep.current = pasoActual;
    stepperRef.current?.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
  }, [pasoActual]);

  function irAPaso(paso) {
    setChosenStep(paso);
    if (!pasosVisitados.includes(paso)) {
      setPasosVisitados((prev) => [...prev, paso]);
    }
  }

  return (
    <PageContainer className="max-w-lg">
      <button
        type="button"
        onClick={() => goTo(`/alumno/pagos/${invoiceId}/seleccion`)}
        className="flex items-center gap-1.5 text-sm text-ink-faint hover:text-ink"
      >
        <ChevronLeft size={16} />
        Volver
      </button>

      <h1 className="text-xl font-bold">Pago por transferencia</h1>

      {breakdown && <InvoiceSummary breakdown={breakdown} />}

      <div ref={stepperRef} className="scroll-mt-4">
        <Stepper
          pasos={PASOS}
          pasoActual={pasoActual}
          pasosVisitados={visitedSteps}
          onStepClick={pasoActual === 'estado' ? undefined : irAPaso}
        />
      </div>

      {isLoading && <Skeleton className="h-48" />}

      {!isLoading && pasoActual === 'datos' && (
        <PasoDatos config={config} amount={breakdown?.remainingBalance} onNext={() => irAPaso('comprobante')} />
      )}
      {pasoActual === 'comprobante' && (
        <PasoComprobante
          invoiceId={invoiceId}
          breakdown={breakdown}
          onSuccess={() => irAPaso('estado')}
          onViewReceipt={() => irAPaso('estado')}
        />
      )}
      {pasoActual === 'estado' && <PasoEstado invoiceId={invoiceId} onResend={() => irAPaso('comprobante')} />}
    </PageContainer>
  );
}
