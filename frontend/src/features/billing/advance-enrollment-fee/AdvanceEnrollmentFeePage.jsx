import { useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowRight, Check } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { Stepper } from '@/components/Stepper';
import { StatusPill } from '@/components/StatusPill';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { useEstadoMatricula, useMisMatriculaciones } from '@/features/enrollment';
import { paymentOriginState } from '@/hooks/usePaymentReturn';
import { formatDate, formatMoney } from '@/lib/billing';
import { advanceErrorMessage, advanceKindOf, advanceOf, advanceYearOf } from './advanceEnrollmentFee';
import { useRequestAdvanceEnrollmentFee } from './useAdvanceEnrollmentFee';

const STEPS = [
  { key: 'confirm', label: 'Confirmar' },
  { key: 'invoice', label: 'Factura' },
];

const NEXT_STEPS = [
  ['Generamos la factura', 'Todavía no pagás nada.'],
  ['La pagás cuando quieras', 'Elegís cómo pagarla en el paso siguiente.'],
  ['En enero no se vuelve a cobrar', 'La matrícula queda cubierta para el año que viene.'],
];

function SummaryRows({ rows }) {
  return (
    <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-[13px]">
      {rows.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-ink-faint">{label}</dt>
          <dd className="font-semibold text-ink">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function AdvanceEnrollmentFeePage() {
  const { enrollmentId } = useParams();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const detailPath = `/alumno/pagos/carrera/${enrollmentId}`;
  const [issuedInvoiceId, setIssuedInvoiceId] = useState(null);
  const [message, setMessage] = useState('');
  const statusQuery = useEstadoMatricula(enrollmentId);
  const { data: programs } = useMisMatriculaciones();
  const mutation = useRequestAdvanceEnrollmentFee(enrollmentId, 'student');

  const advance = advanceOf(statusQuery.data);
  const year = advanceYearOf(advance);
  const concept = year ? `Matrícula ${year}` : 'Matrícula del año siguiente';
  const program = (programs ?? []).find((item) => String(item.id) === String(enrollmentId));
  const programName = program?.programName ?? '';
  const issued = issuedInvoiceId != null;

  function generate() {
    setMessage('');
    mutation.mutate(undefined, {
      onSuccess: (result) => setIssuedInvoiceId(result.invoiceId),
      onError: (error) => setMessage(advanceErrorMessage(error)),
    });
  }

  function payNow() {
    navigate(`/alumno/pagos/${issuedInvoiceId}/seleccion`, { state: paymentOriginState(pathname, 'Volver a la matrícula') });
  }

  if (statusQuery.isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-40 w-full rounded-2xl" />
      </PageContainer>
    );
  }

  if (statusQuery.isError && !issued) {
    return (
      <PageContainer>
        <LoadErrorState
          error={statusQuery.error}
          resource="la matrícula del año siguiente"
          onRetry={statusQuery.refetch}
          backTo={detailPath}
          backLabel="las cuotas"
        />
      </PageContainer>
    );
  }

  if (!issued && advanceKindOf(advance) !== 'available') {
    return (
      <PageContainer>
        <GlassCard className="p-5">
          <EmptyState
            title="La matrícula del año siguiente no está disponible por ahora."
            description="Volvé a tus cuotas para ver el motivo."
            action={
              <Button variant="outline" onClick={() => navigate(detailPath)}>
                Volver a las cuotas
              </Button>
            }
          />
        </GlassCard>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="flex flex-col gap-4">
      <Stepper pasos={STEPS} pasoActual={issued ? 'invoice' : 'confirm'} />

      {!issued && (
        <>
          <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.25fr)]">
            <GlassCard className="flex flex-col gap-3.5 p-5">
              <h2 className="font-heading text-base font-semibold text-ink">Resumen</h2>
              <div className="flex items-start justify-between gap-3 text-sm">
                <span className="text-ink">
                  {concept}
                  {programName && <small className="block text-xs text-ink-faint">{programName}</small>}
                </span>
                <span className="font-semibold tabular-nums text-ink">{formatMoney(advance.amount)}</span>
              </div>
              <div className="flex justify-between border-t border-border pt-3 text-[15px] font-bold text-ink">
                <span>Total</span>
                <span className="tabular-nums">{formatMoney(advance.amount)}</span>
              </div>
              <SummaryRows
                rows={[
                  ['Precio', 'El de hoy'],
                  ['Vence', formatDate(advance.dueDate)],
                ]}
              />
            </GlassCard>
            <GlassCard className="flex flex-col gap-3.5 p-5">
              <h2 className="font-heading text-base font-semibold text-ink">Cómo sigue</h2>
              <ol className="flex flex-col gap-3">
                {NEXT_STEPS.map(([step, detail], index) => (
                  <li key={step} className="flex items-start gap-3 text-sm">
                    <span aria-hidden="true" className="grid size-6 shrink-0 place-items-center rounded-full bg-accent-soft text-xs font-bold text-primary">
                      {index + 1}
                    </span>
                    <span>
                      <b className="font-semibold text-ink">{step}</b>
                      <small className="block text-xs text-ink-faint">{detail}</small>
                    </span>
                  </li>
                ))}
              </ol>
              <Notice title="Qué tenés que saber.">Si no cursás ese año, la matrícula no se devuelve ni queda a favor.</Notice>
            </GlassCard>
          </div>
          {message && (
            <p role="alert" className="text-sm text-danger">
              {message}
            </p>
          )}
          <StickyActionBar summary={formatMoney(advance.amount)} hint="Se genera la factura. Todavía no pagás nada.">
            <Button variant="ghost" onClick={() => navigate(detailPath)} disabled={mutation.isPending}>
              Cancelar
            </Button>
            <Button variant="gradient" size="lg" onClick={generate} loading={mutation.isPending}>
              {mutation.isPending ? 'Generando…' : 'Continuar al pago'}
              {!mutation.isPending && <ArrowRight size={16} />}
            </Button>
          </StickyActionBar>
        </>
      )}

      {issued && (
        <GlassCard className="flex flex-col gap-4 p-5">
          <div className="flex items-center gap-3">
            <div className="grid size-10 place-items-center rounded-xl bg-success-bg text-success">
              <Check size={20} />
            </div>
            <div>
              <b className="block text-[15px] text-ink">Factura generada</b>
              <small className="text-xs text-ink-faint">Falta el pago para completar la {concept.toLowerCase()}.</small>
            </div>
          </div>
          <SummaryRows
            rows={[
              ['Concepto', programName ? `${concept} · ${programName}` : concept],
              ['Importe', advance ? formatMoney(advance.amount) : '-'],
              ['Vence', advance ? formatDate(advance.dueDate) : '-'],
              ['Estado', <StatusPill key="status" tone="warning">Pendiente</StatusPill>],
            ]}
          />
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button variant="gradient" size="lg" onClick={payNow}>
              Pagar ahora
            </Button>
            <Button variant="outline" size="lg" onClick={() => navigate(detailPath)}>
              Pagar más tarde
            </Button>
          </div>
        </GlassCard>
      )}
    </PageContainer>
  );
}
