import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Info } from 'lucide-react';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { InfoTip } from '@/components/charts/InfoTip';
import { useAuth } from '@/context/AuthContext';
import { formatMoney, invoiceTitle, invoiceStatusInfo } from '@/lib/billing';
import { useAdminBillingOverview } from '@/hooks/useBilling';
import { inboxPath, roleBaseOf } from '../authorization/authorizationRequests';
import { useIssueCreditNote, useStudentCreditBalance, useStudentHeader } from '../hooks/useCreditNotes';
import { fromCents, toCents } from '../refunds/refundForm';
import { AmountDiffTable } from './AmountDiffTable';
import { CreditNoteLinesForm } from './CreditNoteLinesForm';
import { CreditNoteViolation } from './CreditNoteViolation';
import {
  JUSTIFICATION_MAX_LENGTH,
  STAFF_REASON_LABELS,
  billingPath,
  creditNoteDiffRowsOf,
  creditNoteDraftOf,
  creditNoteErrorOf,
  isAuthorizationAck,
  receiptsLocation,
} from './creditNotes';

const STEPS = [
  { key: 'amounts', label: 'Importes' },
  { key: 'confirm', label: 'Confirmar' },
];

function SummaryRow({ label, children }) {
  return (
    <div className="flex items-baseline justify-between gap-3 text-sm">
      <span className="text-ink-faint">{label}</span>
      <span className="font-medium tabular-nums text-ink">{children}</span>
    </div>
  );
}

function CreditNoteSummary({ draft, creditBalance, onCancelInstead }) {
  const total = fromCents(draft.totalCents, draft.currency);
  const creditTodayCents = toCents(creditBalance?.unallocatedCredit);
  return (
    <div className="flex flex-col gap-3">
      <SummaryRow label="Efecto">{draft.paid ? 'Crédito a favor' : 'Reduce la deuda'}</SummaryRow>
      {draft.paid ? (
        <>
          <SummaryRow label="Crédito a favor hoy">{formatMoney(fromCents(creditTodayCents, draft.currency))}</SummaryRow>
          <SummaryRow label="Crédito a favor después">
            {formatMoney(fromCents(creditTodayCents + draft.freeCents, draft.currency))}
          </SummaryRow>
        </>
      ) : (
        <>
          <SummaryRow label="Deuda de la cuota hoy">{formatMoney(fromCents(draft.outstandingCents, draft.currency))}</SummaryRow>
          <SummaryRow label="Deuda de la cuota después">
            {formatMoney(fromCents(Math.max(0, draft.outstandingCents - draft.offsetCents), draft.currency))}
          </SummaryRow>
        </>
      )}
      <div className="flex items-baseline justify-between gap-3 border-t border-border pt-3">
        <span className="font-semibold text-ink">Total de la nota</span>
        <span className="text-lg font-semibold tabular-nums text-ink">{formatMoney(total)}</span>
      </div>
      {draft.equalsDebt && (
        <div role="alert" className="rounded-xl border border-danger/30 bg-danger-bg px-3 py-2 text-sm text-ink">
          Esa nota cubre toda la deuda. Para dejar la cuota sin efecto usá{' '}
          <button type="button" onClick={onCancelInstead} className="font-semibold text-brand underline">
            Cancelar cuota
          </button>
          .
        </div>
      )}
    </div>
  );
}

export function CreateCreditNotePage() {
  const { invoiceId } = useParams();
  const [searchParams] = useSearchParams();
  const studentId = searchParams.get('alumno');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { usuario } = useAuth();
  const roleBase = roleBaseOf(usuario);
  const isDirector = roleBase === 'director';

  const overviewQuery = useAdminBillingOverview(studentId);
  const { data: student } = useStudentHeader(studentId);
  const { data: creditBalance } = useStudentCreditBalance(studentId);
  const mutation = useIssueCreditNote();

  const [step, setStep] = useState('amounts');
  const [form, setForm] = useState({ justification: '', amounts: {} });
  const [serverError, setServerError] = useState(null);

  const invoices = (overviewQuery.data ?? []).flatMap((enrollment) => enrollment.invoices ?? []);
  const invoice = invoices.find((candidate) => String(candidate.id) === invoiceId);
  const backPath = billingPath(roleBase, { studentId });
  const studentName = student ? `${student.apellido}, ${student.nombre}` : 'el alumno';

  if (!studentId) {
    return (
      <PageContainer title="Nota de crédito">
        <EmptyState title="Falta elegir el alumno" description="Entrá desde la cuota, en Facturación." action={<Button asChild variant="outline"><Link to={`/${roleBase}/facturacion`}>Ir a Facturación</Link></Button>} />
      </PageContainer>
    );
  }

  if (overviewQuery.isLoading) {
    return (
      <PageContainer title="Nota de crédito">
        <GlassCard className="p-5">
          <Skeleton className="h-48 w-full" />
        </GlassCard>
      </PageContainer>
    );
  }

  if (overviewQuery.isError || !invoice) {
    return (
      <PageContainer title="Nota de crédito">
        <GlassCard className="flex flex-col items-start gap-3 p-5">
          <p role="alert" className="text-sm text-danger">No pudimos cargar la cuota.</p>
          <div className="flex gap-2">
            {overviewQuery.isError && (
              <Button type="button" variant="outline" onClick={() => overviewQuery.refetch()}>
                Reintentar
              </Button>
            )}
            <Button asChild variant="outline">
              <Link to={backPath}>Volver a la ficha</Link>
            </Button>
          </div>
        </GlassCard>
      </PageContainer>
    );
  }

  const draft = creditNoteDraftOf(invoice, form);
  const statusLabel = invoiceStatusInfo(invoice.paymentStatus).label;
  const executesImmediately = isDirector;
  const total = fromCents(draft.totalCents, draft.currency);

  function updateAmount(lineNumber, value) {
    setForm((current) => ({ ...current, amounts: { ...current.amounts, [lineNumber]: value } }));
    setServerError(null);
  }

  function updateJustification(value) {
    setForm((current) => ({ ...current, justification: value }));
  }

  function handleReload() {
    setForm((current) => ({ ...current, amounts: {} }));
    setServerError(null);
    setStep('amounts');
    queryClient.invalidateQueries({ queryKey: ['students'] });
  }

  function handleCancelInstead() {
    navigate(backPath, { state: { cancelInvoiceId: invoice.id } });
  }

  async function handleSubmit() {
    if (draft.blocked || mutation.isPending) return;
    setServerError(null);
    try {
      const result = await mutation.mutateAsync({ invoiceId: invoice.id, payload: draft.payload });
      if (isAuthorizationAck(result)) {
        toast.warning('Pedido enviado. Todavía no se aplicó.', {
          description: 'La nota de crédito se emite cuando la autorice la dirección.',
          action: { label: 'Ver mis pedidos', onClick: () => navigate(inboxPath(roleBase)) },
        });
      } else {
        const detail = draft.paid
          ? `${formatMoney(total)} quedaron como crédito a favor del alumno.`
          : `La deuda de la cuota bajó a ${formatMoney(fromCents(Math.max(0, draft.outstandingCents - draft.offsetCents), draft.currency))}.`;
        toast.success(`Nota de crédito ${result.creditNoteNumber} emitida.`, {
          description: detail,
          action: draft.paid
            ? { label: 'Pedir reembolso', onClick: () => navigate(`/${roleBase}/alumnos/${studentId}/reembolsos/nuevo`) }
            : undefined,
        });
      }
      navigate(backPath);
    } catch (error) {
      setServerError(creditNoteErrorOf(error));
    }
  }

  const confirmLabel = executesImmediately ? 'Emitir nota de crédito' : 'Enviar a autorización';
  const rows = creditNoteDiffRowsOf(draft, creditBalance);
  const issuedLines = draft.lines.filter((line) => line.cents > 0);

  return (
    <PageContainer title="Nota de crédito" description={`${studentName} · ${invoiceTitle(invoice)}`}>
      <div className="mx-auto w-full max-w-md px-6">
        <Stepper
          pasos={STEPS}
          pasoActual={step}
          pasosVisitados={step === 'confirm' ? ['amounts', 'confirm'] : ['amounts']}
          onStepClick={(key) => key === 'amounts' && setStep('amounts')}
        />
      </div>

      {step === 'amounts' ? (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <GlassCard className="flex flex-col gap-4 p-5">
            <h2 className="font-heading text-base font-semibold text-ink">
              {invoiceTitle(invoice)} <span className="text-sm font-normal text-ink-faint">· {invoice.invoiceNumber} · {statusLabel}</span>
            </h2>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-1">
                <Label htmlFor="credit-note-reason">Motivo</Label>
                <InfoTip>Las notas de crédito del personal corrigen un error de facturación. Para cancelar una cuota completa se usa Cancelar cuota.</InfoTip>
              </div>
              <Input id="credit-note-reason" value={STAFF_REASON_LABELS.BILLING_ERROR} readOnly className="cursor-default" />
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between gap-3">
                <Label htmlFor="credit-note-justification">Justificación · obligatoria</Label>
                <span className={form.justification.length > JUSTIFICATION_MAX_LENGTH ? 'text-xs text-danger' : 'text-xs text-ink-faint'}>
                  {form.justification.length}/{JUSTIFICATION_MAX_LENGTH}
                </span>
              </div>
              <Textarea
                id="credit-note-justification"
                rows={3}
                value={form.justification}
                placeholder="Contá qué se corrige. Queda en el registro de auditoría."
                onChange={(event) => updateJustification(event.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <span className="text-sm font-medium text-ink">Importes por renglón</span>
              <CreditNoteLinesForm invoice={invoice} draft={draft} amounts={form.amounts} onChange={updateAmount} />
            </div>

            <CreditNoteViolation error={serverError} receiptsTo={receiptsLocation(`/${roleBase}/facturacion`, studentId)} onReload={handleReload} onCancelInstead={handleCancelInstead} />
          </GlassCard>

          <GlassCard className="flex h-fit flex-col gap-4 p-5 lg:sticky lg:top-4">
            <h2 className="font-heading text-base font-semibold text-ink">Resumen</h2>
            <CreditNoteSummary draft={draft} creditBalance={creditBalance} onCancelInstead={handleCancelInstead} />
            {!executesImmediately && (
              <div className="flex items-start gap-2 rounded-xl border border-warning/30 bg-warning-bg px-3 py-2 text-sm text-ink">
                <Info className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
                <span>Necesita autorización de la dirección.</span>
              </div>
            )}
          </GlassCard>
        </div>
      ) : (
        <GlassCard className="flex flex-col gap-5 p-5">
          <h2 className="font-heading text-base font-semibold text-ink">Así queda la cuota</h2>
          <AmountDiffTable rows={rows} unchanged={[{ key: 'status', label: 'Estado de la cuota', value: statusLabel }]} />

          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold text-ink">Renglones acreditados</span>
            <ul className="flex flex-col gap-1 text-sm text-ink-soft">
              {issuedLines.map((line) => {
                const source = invoice.lines.find((candidate) => candidate.lineNumber === line.lineNumber);
                return (
                  <li key={line.lineNumber} className="flex items-baseline justify-between gap-3">
                    <span>{source.description || invoiceTitle(invoice)}</span>
                    <span className="tabular-nums text-ink">{formatMoney(fromCents(line.cents, draft.currency))}</span>
                  </li>
                );
              })}
            </ul>
          </div>

          <div className="flex flex-col gap-1">
            <span className="text-sm font-semibold text-ink">Justificación</span>
            <p className="text-sm text-ink-soft">{form.justification.trim()}</p>
          </div>

          <div className="flex items-start gap-3 rounded-2xl border border-border bg-neutral-bg px-4 py-3 text-sm text-ink">
            <Info className="mt-0.5 size-4 shrink-0 text-ink-soft" aria-hidden="true" />
            <span>
              {draft.paid
                ? 'Queda como crédito a favor en la cuenta del alumno. No es una devolución de dinero.'
                : 'La nota reduce lo que debe el alumno en esta cuota.'}{' '}
              La nota de crédito no se puede deshacer.
            </span>
          </div>
          {!executesImmediately && (
            <div className="flex items-start gap-3 rounded-2xl border border-warning/30 bg-warning-bg px-4 py-3 text-sm text-ink">
              <Info className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
              <span>
                <strong>Necesita autorización de la dirección.</strong> No se aplica hasta que la apruebe.
              </span>
            </div>
          )}

          <CreditNoteViolation error={serverError} receiptsTo={receiptsLocation(`/${roleBase}/facturacion`, studentId)} onReload={handleReload} onCancelInstead={handleCancelInstead} />
        </GlassCard>
      )}

      <GlassCard className="sticky bottom-4 z-10 flex flex-wrap items-center gap-3 p-3 px-5">
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="text-xs text-ink-faint">Total de la nota</span>
          <span className="font-semibold tabular-nums text-ink">{formatMoney(total)}</span>
          {step === 'amounts' && draft.blockReason && <span className="text-xs text-ink-soft">{draft.blockReason}</span>}
        </div>
        {step === 'amounts' ? (
          <>
            <Button asChild variant="outline">
              <Link to={backPath}>Cancelar</Link>
            </Button>
            <Button type="button" variant="gradient" disabled={draft.blocked} onClick={() => setStep('confirm')}>
              Revisar la nota
            </Button>
          </>
        ) : (
          <>
            <Button type="button" variant="outline" onClick={() => setStep('amounts')} disabled={mutation.isPending}>
              Volver a editar
            </Button>
            <Button type="button" variant="gradient" loading={mutation.isPending} onClick={handleSubmit}>
              {confirmLabel}
            </Button>
          </>
        )}
      </GlassCard>
    </PageContainer>
  );
}
