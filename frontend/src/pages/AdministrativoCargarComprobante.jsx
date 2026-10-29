import { useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { CheckCircle, Info } from 'lucide-react';
import { Breadcrumb } from '@/components/Breadcrumb';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { Stepper } from '@/components/Stepper';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { BankTransferReceiptForm } from '@/components/billing/BankTransferReceiptForm';
import { useAuth } from '@/context/AuthContext';
import { useLegajoAlumno } from '@/features/identity/hooks/useLegajoAlumno';
import { useAdminBillingOverview, useSubmitBankTransferReceipt } from '@/hooks/useBilling';
import { formatDate, formatMoney } from '@/lib/billing';
import {
  buildReceiptSubmission,
  coverageOf,
  isUnconfirmedSubmission,
  orderedPayableInvoices,
  receiptResultRows,
  receiptSubmissionFailure,
} from '@/lib/bankTransferReceipt';
import { moneyValue } from '@/lib/utils';

const STEPS = [
  { key: 'datos', label: 'Datos del comprobante' },
  { key: 'review', label: 'Revisar y confirmar' },
];

const RESULT_ROW = {
  protected: { note: 'Protegida: sin recargo mientras se revisa', pill: { tone: 'warning', label: 'En revisión' } },
  overdue: { note: 'Sigue vencida, el recargo corre hasta la aprobación', pill: { tone: 'danger', label: 'Vencida' } },
  uncovered: { note: 'Sigue pendiente: el monto no la cubre completa', pill: { tone: 'neutral', label: 'Pendiente' } },
};

function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatFileSize(bytes) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

function money(amount, currency) {
  return formatMoney({ amount: amount.toFixed(2), currency });
}

function StudentHeader({ student }) {
  return (
    <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
      <div className="min-w-0">
        <p className="font-semibold text-ink">
          {student.apellido}, {student.nombre}
        </p>
        <p className="text-sm text-ink-faint">DNI {student.dni}</p>
      </div>
      <span className="inline-flex items-center gap-1.5">
        <StatusPill tone="accent">Carga por secretaría</StatusPill>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                aria-label="Qué significa carga por secretaría"
                className="inline-flex size-6 items-center justify-center rounded-full text-ink-faint hover:text-ink"
              >
                <Info className="size-4" aria-hidden="true" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Lo cargás en nombre del alumno. Queda registrado quién lo carga y quién lo aprueba.</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </span>
    </GlassCard>
  );
}

function SummaryRow({ label, detail, children }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-border py-3 last:border-b-0">
      <span className="flex min-w-0 flex-col">
        <span className="text-sm text-ink-soft">{label}</span>
        {detail && <span className="truncate text-xs text-ink-faint">{detail}</span>}
      </span>
      <span className="shrink-0 text-right text-sm font-semibold text-ink">{children}</span>
    </div>
  );
}

function ReviewStep({ student, draft, uploaderName, state, onBack, onSend, onViewReceipts }) {
  const selected = draft.selected;
  const coverage = coverageOf(selected, draft.amount);
  return (
    <>
      <GlassCard className="flex flex-col gap-3 p-5">
        <h2 className="font-heading text-base font-bold text-ink">Revisá antes de cargar</h2>
        <div className="flex flex-col">
          <SummaryRow label="Alumno" detail={`DNI ${student.dni}`}>
            {student.apellido}, {student.nombre}
          </SummaryRow>
          {selected.map((invoice, index) => (
            <SummaryRow
              key={invoice.id}
              label={invoice.title}
              detail={invoice.dueDate ? `${invoice.paymentStatus === 'OVERDUE' ? 'Venció el' : 'Vence el'} ${formatDate(invoice.dueDate)}` : null}
            >
              <span className="flex flex-col items-end gap-1">
                <span className="tabular-nums">{formatMoney(invoice.outstanding)}</span>
                {coverage[index].state === 'covered' ? (
                  <StatusPill tone="success">Cubierta</StatusPill>
                ) : (
                  <StatusPill tone="warning">Parcial</StatusPill>
                )}
              </span>
            </SummaryRow>
          ))}
          <SummaryRow label="Operación" detail={`${draft.originAccount} · ${draft.holderName}`}>
            Nº {draft.operationNumber}
          </SummaryRow>
          <SummaryRow label="Archivo" detail={formatFileSize(draft.file.size)}>
            {draft.file.name}
          </SummaryRow>
          <SummaryRow label="Monto transferido">
            <span className="text-base tabular-nums">{money(draft.amount, draft.currency)}</span>
          </SummaryRow>
        </div>
        <p className="rounded-xl bg-neutral-bg px-4 py-3 text-sm text-ink-soft">
          Lo cargás {uploaderName}. Queda registrado como carga de secretaría y pendiente de revisión.
        </p>
        {state.message && (
          <p role="alert" className="rounded-xl border border-danger/40 bg-danger-bg p-4 text-sm text-ink">
            {state.message}
          </p>
        )}
        {state.unconfirmed && (
          <div role="alert" className="flex flex-col gap-3 rounded-xl border border-danger/40 bg-danger-bg p-4">
            <div className="flex flex-col gap-0.5">
              <span className="text-sm font-bold text-ink">No pudimos confirmar si se cargó</span>
              <span className="text-sm text-ink-soft">
                Reintentar no lo duplica: es el mismo envío. Antes de reintentar podés mirar la pestaña Comprobantes.
              </span>
            </div>
            <div>
              <Button type="button" variant="outline" size="sm" onClick={onViewReceipts}>
                Ver Comprobantes
              </Button>
            </div>
          </div>
        )}
      </GlassCard>
      <GlassCard className="sticky bottom-4 z-10 flex items-center justify-end gap-3 p-4">
        <Button type="button" variant="ghost" onClick={onBack} disabled={state.sending}>
          Volver a editar
        </Button>
        <Button type="button" variant="gradient" onClick={onSend} loading={state.sending}>
          {state.sending ? 'Cargando…' : state.unconfirmed ? 'Reintentar' : 'Cargar comprobante'}
        </Button>
      </GlassCard>
    </>
  );
}

function ResultStep({ student, draft, uploaderName, onBackToAccount, onGoToReceipts }) {
  const selected = draft.selected;
  const rows = receiptResultRows(selected, draft.amount);
  const total = selected.reduce((sum, invoice) => sum + moneyValue(invoice.outstanding), 0);
  const excess = draft.amount - total;
  const now = new Date();
  const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  return (
    <>
      <GlassCard className="flex items-start gap-4 p-6">
        <CheckCircle className="mt-0.5 size-8 shrink-0 text-success" aria-hidden="true" />
        <div className="flex flex-col gap-1">
          <h2 className="font-heading text-lg font-bold text-ink">
            Comprobante cargado para {student.nombre} {student.apellido}
          </h2>
          <p className="text-sm text-ink-soft">
            {money(draft.amount, draft.currency)} · Nº {draft.operationNumber}. Quedó pendiente de revisión; no se aprueba solo.
          </p>
          <p className="text-xs text-ink-faint">
            Cargado por {uploaderName} el {formatDate(draft.transferDate)}, {time}. Queda registrado.
          </p>
        </div>
      </GlassCard>
      <GlassCard className="flex flex-col gap-3 p-5">
        <h2 className="font-heading text-base font-bold text-ink">Qué pasó con cada cuota</h2>
        <ul className="flex flex-col gap-1.5">
          {rows.map((row) => {
            const info = RESULT_ROW[row.outcome];
            return (
              <li key={row.id} className="flex items-center justify-between gap-3 rounded-2xl border border-border px-4 py-3">
                <span className="flex min-w-0 flex-col gap-0.5">
                  <span className="text-sm font-semibold text-ink">{row.title}</span>
                  <span className={row.outcome === 'protected' ? 'text-xs text-success' : 'text-xs text-ink-faint'}>{info.note}</span>
                </span>
                <StatusPill tone={info.pill.tone}>{info.pill.label}</StatusPill>
              </li>
            );
          })}
        </ul>
        {excess > 0.004 && (
          <p role="note" className="rounded-xl border border-warning/40 bg-warning-bg p-4 text-sm text-ink">
            Se transfirieron {money(excess, draft.currency)} de más. Quedan como saldo a favor del alumno cuando se apruebe.
          </p>
        )}
      </GlassCard>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
        <Button type="button" variant="outline" onClick={onBackToAccount}>
          Volver al estado de cuenta
        </Button>
        <Button type="button" variant="gradient" onClick={onGoToReceipts}>
          Ir a Comprobantes
        </Button>
      </div>
    </>
  );
}

export function AdministrativoCargarComprobante() {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const { usuario } = useAuth();
  const base = usuario?.roles?.includes('DIRECTOR') ? '/director' : '/administrativo';
  const accountPath = `${base}/facturacion?alumno=${studentId}`;
  const receiptsPath = `${base}/facturacion?tab=comprobantes`;
  const uploaderName = usuario ? `${usuario.nombre ?? ''} ${usuario.apellido ?? ''}`.trim() : '';

  const { data: student } = useLegajoAlumno(studentId);
  const { data: overview, isLoading, isError } = useAdminBillingOverview(studentId);
  const { mutate, isPending } = useSubmitBankTransferReceipt({ studentId });
  const idempotencyKey = useRef(null);

  const [step, setStep] = useState('datos');
  const [draft, setDraft] = useState(null);
  const [duplicate, setDuplicate] = useState(false);
  const [outcome, setOutcome] = useState({ message: null, unconfirmed: false });
  const [done, setDone] = useState(false);

  const { selectable, underReview } = useMemo(
    () => orderedPayableInvoices((overview ?? []).flatMap((enrollment) => enrollment.invoices ?? [])),
    [overview],
  );

  const studentName = student ? `${student.apellido}, ${student.nombre}` : '…';
  const crumbs = [
    { label: 'Facturación', to: `${base}/facturacion` },
    { label: studentName, to: accountPath },
    { label: done ? 'Comprobante cargado' : 'Cargar comprobante' },
  ];

  function handleReview(next) {
    idempotencyKey.current = null;
    setDraft({ ...next, selected: selectable.slice(0, next.count) });
    setDuplicate(false);
    setOutcome({ message: null, unconfirmed: false });
    setStep('review');
  }

  function handleSend() {
    idempotencyKey.current ??= newIdempotencyKey();
    setOutcome({ message: null, unconfirmed: false });
    const submission = buildReceiptSubmission({ ...draft, studentId });
    mutate(
      { submission, idempotencyKey: idempotencyKey.current },
      {
        onSuccess: () => setDone(true),
        onError: (error) => {
          if (isUnconfirmedSubmission(error)) {
            setOutcome({ message: null, unconfirmed: true });
            return;
          }
          const failure = receiptSubmissionFailure(error);
          if (failure.duplicate) {
            idempotencyKey.current = null;
            setDuplicate(true);
            setStep('datos');
            return;
          }
          const first = Object.values(failure.fieldErrors)[0];
          setOutcome({ message: failure.message ?? first, unconfirmed: false });
        },
      },
    );
  }

  let content;
  if (isLoading || !student) {
    content = <Skeleton className="h-64 w-full rounded-2xl" />;
  } else if (isError) {
    content = (
      <GlassCard className="p-6 text-center text-sm text-ink-faint">No pudimos cargar la cuenta del alumno. Recargá la página.</GlassCard>
    );
  } else if (selectable.length === 0 && !done) {
    content = (
      <GlassCard>
        <EmptyState
          title="No tiene cuotas pendientes"
          description="No hay nada para cubrir con un comprobante."
          action={
            <Button type="button" variant="outline" onClick={() => navigate(accountPath)}>
              Volver al estado de cuenta
            </Button>
          }
        />
      </GlassCard>
    );
  } else if (done) {
    content = (
      <ResultStep
        student={student}
        draft={draft}
        uploaderName={uploaderName}
        onBackToAccount={() => navigate(accountPath)}
        onGoToReceipts={() => navigate(receiptsPath)}
      />
    );
  } else {
    content = (
      <>
        <Stepper pasos={STEPS} pasoActual={step} pasosVisitados={step === 'review' ? ['datos', 'review'] : ['datos']} onStepClick={() => setStep('datos')} />
        {step === 'datos' ? (
          <BankTransferReceiptForm
            key={duplicate ? 'duplicate' : 'form'}
            audience="staff"
            invoices={selectable}
            underReview={underReview}
            initialCount={1}
            defaultHolderName={`${student.nombre} ${student.apellido}`}
            draft={draft}
            initialDuplicate={duplicate}
            onReview={handleReview}
            onCancel={() => navigate(accountPath)}
            onViewReceipt={() => navigate(receiptsPath)}
          />
        ) : (
          <ReviewStep
            student={student}
            draft={draft}
            uploaderName={uploaderName}
            state={{ ...outcome, sending: isPending }}
            onBack={() => setStep('datos')}
            onSend={handleSend}
            onViewReceipts={() => navigate(receiptsPath)}
          />
        )}
      </>
    );
  }

  return (
    <PageContainer title="Cargar comprobante" className="max-w-3xl">
      <Breadcrumb items={crumbs} />
      {student && <StudentHeader student={student} />}
      {content}
    </PageContainer>
  );
}
