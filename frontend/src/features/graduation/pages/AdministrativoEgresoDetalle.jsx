import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { ModalMotivo } from '@/components/ModalMotivo';
import { UnavailableRequestState } from '@/components/director/UnavailableRequestState';
import { GraduationDebtCard } from '@/features/graduation/components/GraduationDebtCard';
import {
  useEgreso,
  useGraduationDebtSummary,
  useRechazarEgreso,
  useVerificarDeudaEgreso,
} from '@/features/graduation/hooks/useGraduation';
import {
  GRADUATION_VERIFICATION_TRAY,
  isPendingDebtFailure,
  verificationStatusLabel,
} from '@/features/graduation/lib/graduationVerification';
import { formatMoney } from '@/lib/billing';
import { formatearFecha } from '@/lib/comisiones';
import { ageLabel, decisionErrorMessage, isAlreadyDecided, MIN_REJECTION_REASON_LENGTH } from '@/lib/aprobaciones';

function Field({ label, children }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-[12px] text-ink-faint">{label}</dt>
      <dd className="text-[14px] font-medium text-ink">{children}</dd>
    </div>
  );
}

function ResultBanner({ outcome, summary, studentId }) {
  if (outcome === 'verified') {
    return (
      <div className="rounded-2xl bg-success-bg px-4 py-3 text-[13.5px] font-medium text-success-ink">
        <b>Deuda verificada.</b> La solicitud pasó al Director para su decisión.
      </div>
    );
  }
  if (outcome === 'debt') {
    return (
      <div className="flex flex-wrap items-center gap-3 rounded-2xl bg-danger-bg px-4 py-3 text-[13.5px] font-medium text-danger-ink">
        <span>
          <b>No se puede verificar:</b> el alumno tiene deuda pendiente
          {summary?.totalDue ? ` por ${formatMoney(summary.totalDue)}` : ''}. La solicitud sigue por verificar.
        </span>
        <Button asChild size="sm" variant="outline">
          <Link to={`/administrativo/alumnos/${studentId}?view=financiero`}>Ir al Financiero del alumno</Link>
        </Button>
      </div>
    );
  }
  return null;
}

export function AdministrativoEgresoDetalle() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: graduation, isLoading, isError, error, refetch } = useEgreso(id);
  const isActionable = graduation?.status === 'REQUESTED';
  const summaryQuery = useGraduationDebtSummary(isActionable ? id : null);
  const verify = useVerificarDeudaEgreso(id);
  const reject = useRechazarEgreso(id);
  const [outcome, setOutcome] = useState(null);
  const [rejectOpen, setRejectOpen] = useState(false);

  if (isError) {
    return (
      <UnavailableRequestState
        error={error}
        backTo={GRADUATION_VERIFICATION_TRAY}
        backLabel="Egresos por verificar"
        onRetry={refetch}
      />
    );
  }

  if (isLoading || !graduation) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-40" />
      </PageContainer>
    );
  }

  const pill = verificationStatusLabel(outcome === 'verified' ? 'ADMIN_VERIFIED' : graduation.status);
  const readOnly = !isActionable || outcome === 'verified';
  const busy = verify.isPending || reject.isPending;

  async function handleVerify() {
    setOutcome(null);
    try {
      await verify.mutateAsync();
      setOutcome('verified');
      toast.success('Deuda verificada. Pasó al Director.');
    } catch (failure) {
      if (isPendingDebtFailure(failure)) {
        setOutcome('debt');
        summaryQuery.refetch();
      } else if (isAlreadyDecided(failure)) {
        toast.error('Otra persona ya resolvió esta solicitud.');
        refetch();
      } else {
        toast.error('No se pudo verificar la deuda. Probá de nuevo.');
      }
    }
  }

  async function handleReject(reason) {
    try {
      await reject.mutateAsync(reason);
      setRejectOpen(false);
      toast.success('Egreso rechazado');
      navigate(GRADUATION_VERIFICATION_TRAY);
      return true;
    } catch (failure) {
      toast.error(decisionErrorMessage(failure));
      if (isAlreadyDecided(failure)) {
        setRejectOpen(false);
        refetch();
      }
      return false;
    }
  }

  return (
    <PageContainer>
      <Link
        to={GRADUATION_VERIFICATION_TRAY}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← Volver a Egresos por verificar
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-bold text-ink">{graduation.studentName}</h1>
          <p className="mt-0.5 text-sm text-ink-soft">{graduation.programName}</p>
        </div>
        <StatusPill tone={pill.tone}>{pill.label}</StatusPill>
      </div>

      <div role="status" aria-live="polite">
        <ResultBanner outcome={outcome} summary={summaryQuery.data} studentId={graduation.studentId} />
      </div>

      {isActionable && summaryQuery.data && !summaryQuery.data.mustCheckDebt && (
        <div className="rounded-2xl bg-neutral-bg px-4 py-3 text-[13.5px] font-medium text-neutral-ink">
          El control de deuda está desactivado en este instituto: la verificación no revisa cuotas.
        </div>
      )}

      <GlassCard className="p-4">
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Alumno">{graduation.studentName}</Field>
          <Field label="Carrera">{graduation.programName}</Field>
          <Field label="Solicitado">
            {formatearFecha(graduation.requestedAt) ?? '—'}
            {ageLabel(graduation.requestedAt) ? ` · ${ageLabel(graduation.requestedAt)}` : ''}
          </Field>
        </dl>
      </GlassCard>

      {isActionable && (
        <GraduationDebtCard
          summary={summaryQuery.data}
          isLoading={summaryQuery.isLoading}
          isError={summaryQuery.isError}
          onRetry={summaryQuery.refetch}
          financeAction={
            <Button asChild size="sm" variant="outline" className="w-fit">
              <Link to={`/administrativo/alumnos/${graduation.studentId}?view=financiero`}>Ir al Financiero del alumno</Link>
            </Button>
          }
        />
      )}

      {graduation.status === 'REJECTED' && graduation.rejectionReason && (
        <div className="text-[13px] font-semibold text-danger-ink">
          Motivo del rechazo: &ldquo;{graduation.rejectionReason}&rdquo;
        </div>
      )}

      {!readOnly && (
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="gradient" disabled={verify.isPending} onClick={handleVerify}>
            {verify.isPending ? 'Verificando…' : 'Verificar deuda'}
          </Button>
          <Button variant="outline" disabled={busy} onClick={() => setRejectOpen(true)}>Rechazar egreso</Button>
          <span className="text-[12px] text-ink-faint">Verificar no se puede deshacer.</span>
        </div>
      )}

      <ModalMotivo
        open={rejectOpen}
        onClose={() => setRejectOpen(false)}
        confirmando={reject.isPending}
        requerido
        minLength={MIN_REJECTION_REASON_LENGTH}
        titulo="Rechazar egreso"
        descripcion="El alumno va a ver el motivo. Esta acción no se puede deshacer."
        onConfirmar={handleReject}
      />
    </PageContainer>
  );
}
