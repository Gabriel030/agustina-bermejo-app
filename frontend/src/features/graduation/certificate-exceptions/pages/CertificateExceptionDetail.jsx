import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { useAuth } from '@/context/AuthContext';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Button } from '@/components/ui/button';
import { formatMoney } from '@/lib/utils';
import { DecideExceptionDialog } from '@/features/graduation/certificate-exceptions/components/DecideExceptionDialog';
import { ExceptionStatusPill } from '@/features/graduation/certificate-exceptions/components/ExceptionStatusPill';
import { ExceptionTimeline } from '@/features/graduation/certificate-exceptions/components/ExceptionTimeline';
import { DIRECTOR_EXCEPTIONS_PATH } from '@/features/graduation/certificate-exceptions/components/PendingExceptionRow';
import {
  useCertificateException,
  useDescribedExceptions,
  useWithdrawCertificateException,
} from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';
import { formatFullDate, organismLabelOf, overdueLabel } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

function Fact({ label, children }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-size-12 text-ink-faint">{label}</dt>
      <dd className="text-size-13-5 text-ink">{children}</dd>
    </div>
  );
}

function SectionCard({ title, children }) {
  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <h2 className="font-heading text-size-14-5 font-semibold text-ink">{title}</h2>
      {children}
    </GlassCard>
  );
}

function StatusBanner({ exception }) {
  if (exception.status === 'REJECTED') {
    return (
      <Notice tone="danger" title="Rechazada.">
        {exception.decisionReason}
      </Notice>
    );
  }
  if (exception.status === 'APPROVED') {
    return (
      <Notice tone="success" title={`Aprobada hasta el ${formatFullDate(exception.validUntil)}.`}>
        {exception.createdByDirector ? 'Creada por el Director, sin pedido previo.' : 'Se usa una sola vez.'}
      </Notice>
    );
  }
  if (exception.status === 'EXPIRED') {
    return <Notice tone="warning" title={`Venció el ${formatFullDate(exception.validUntil)}.`}>No se usó.</Notice>;
  }
  return null;
}

function ForbiddenOrMissing({ error, backTo }) {
  const forbidden = error?.status === 403;
  const notFound = error?.status === 404;
  return (
    <GlassCard className="flex flex-1 flex-col p-12" role="alert">
      <EmptyState
        icon={forbidden ? '🔒' : '⚠️'}
        title={forbidden ? 'No tenés permiso para ver esta pantalla' : notFound ? 'No encontramos este pedido' : 'No pudimos cargar este pedido'}
        description={forbidden || notFound ? undefined : 'Es un problema de conexión o del servidor. Podés reintentar en un momento.'}
        action={
          <Button asChild variant="outline" className="min-h-11 md:min-h-0">
            <Link to={backTo}>Volver</Link>
          </Button>
        }
      />
    </GlassCard>
  );
}

export function CertificateExceptionDetail() {
  const { id } = useParams();
  const { usuario } = useAuth();
  const isDirector = (usuario?.roles ?? []).includes('DIRECTOR');
  const backTo = isDirector ? DIRECTOR_EXCEPTIONS_PATH : '/administrativo/constancias';
  const query = useCertificateException(id);
  const { rows } = useDescribedExceptions(query.data ? [query.data] : undefined);
  const withdraw = useWithdrawCertificateException();
  const [dialog, setDialog] = useState(null);
  const exception = rows?.[0];

  if (query.isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-40 w-full" />
      </PageContainer>
    );
  }
  if (query.isError || !exception) {
    return (
      <PageContainer>
        <ForbiddenOrMissing error={query.error} backTo={backTo} />
      </PageContainer>
    );
  }

  const isPending = exception.status === 'PENDING';
  const ownsRequest = exception.requestedById === usuario?.id;

  async function confirmWithdraw() {
    try {
      await withdraw.mutateAsync(exception.id);
      toast.success('Pedido retirado');
      setDialog(null);
    } catch (error) {
      toast.error(describeError(error).message);
    }
  }

  return (
    <PageContainer>
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h1 className="font-heading text-2xl font-bold tracking-tight text-ink">{exception.studentName}</h1>
          <p className="text-sm text-ink-soft">{exception.programName}</p>
        </div>
        <ExceptionStatusPill status={exception.status} validUntil={exception.validUntil} />
      </div>
      <StatusBanner exception={exception} />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <SectionCard title="Pedido">
          <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Fact label="Organismo">{organismLabelOf(exception.organismKind)}</Fact>
            <Fact label="Expediente">{exception.organismReference}</Fact>
            <Fact label={exception.createdByDirector ? 'Creó' : 'Pidió'}>{exception.requesterName}</Fact>
            <Fact label="Fecha">
              <span className="tabular-nums">{formatFullDate(exception.requestedAt)}</span>
            </Fact>
          </dl>
          <div>
            <div className="text-size-12 text-ink-faint">Motivo</div>
            <p className="whitespace-pre-wrap text-size-13-5 text-ink">{exception.reason}</p>
          </div>
        </SectionCard>
        <SectionCard title="Situación del alumno">
          <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Fact label="Deuda al pedirla">
              <span className="font-heading font-bold tabular-nums">{formatMoney(exception.debtTotalAtRequest)}</span>
              {exception.overdueInstallmentsAtRequest != null && (
                <span className="tabular-nums text-ink-soft"> · {overdueLabel(exception.overdueInstallmentsAtRequest)}</span>
              )}
            </Fact>
          </dl>
          <p className="text-size-12 text-ink-faint">La excepción no reemplaza la asistencia.</p>
        </SectionCard>
        {isDirector && isPending && (
          <SectionCard title="Tu decisión">
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="primary" className="min-h-11 md:min-h-0" onClick={() => setDialog('approve')}>
                Aprobar
              </Button>
              <Button type="button" variant="outline" className="min-h-11 md:min-h-0" onClick={() => setDialog('reject')}>
                Rechazar
              </Button>
            </div>
          </SectionCard>
        )}
        {!isDirector && isPending && ownsRequest && (
          <SectionCard title="Tu pedido">
            <div>
              <Button type="button" variant="destructive" className="min-h-11 md:min-h-0" onClick={() => setDialog('withdraw')}>
                Retirar pedido
              </Button>
            </div>
          </SectionCard>
        )}
        <SectionCard title="Historial">
          <ExceptionTimeline exception={exception} />
        </SectionCard>
      </div>
      {(dialog === 'approve' || dialog === 'reject') && (
        <DecideExceptionDialog mode={dialog} exception={exception} onClose={() => setDialog(null)} />
      )}
      {dialog === 'withdraw' && (
        <ConfirmActionDialog
          open
          onOpenChange={(open) => !open && setDialog(null)}
          title="Retirar pedido"
          description="El pedido deja de esperar la decisión del Director. No se puede deshacer."
          confirmLabel="Retirar pedido"
          pending={withdraw.isPending}
          onConfirm={confirmWithdraw}
        />
      )}
    </PageContainer>
  );
}
