import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, FilePlus2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { CreateDirectExceptionDialog } from '@/features/graduation/certificate-exceptions/components/CreateDirectExceptionDialog';
import { DecideExceptionDialog } from '@/features/graduation/certificate-exceptions/components/DecideExceptionDialog';
import { DIRECTOR_EXCEPTIONS_PATH, PendingExceptionRow } from '@/features/graduation/certificate-exceptions/components/PendingExceptionRow';
import {
  useCertificateExceptionSummary,
  useCertificateExceptions,
  useDescribedExceptions,
} from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';
import { pendingLinkLabel } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

const PREVIEW = 3;

function CardBody({ query, rows, onApprove, onReject }) {
  if (query.isLoading) {
    return (
      <div className="flex flex-col gap-2 p-2" aria-busy="true" aria-label="Cargando excepciones">
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <EmptyState
        title="No pudimos cargar esta bandeja"
        className="py-6"
        action={
          <Button type="button" variant="outline" className="min-h-11 md:min-h-0" onClick={() => query.refetch()}>
            Reintentar
          </Button>
        }
      />
    );
  }
  if (!rows?.length) return <EmptyState title="Ninguna excepción esperando tu decisión" className="py-6" />;
  return rows.slice(0, PREVIEW).map((exception) => (
    <PendingExceptionRow key={exception.id} exception={exception} onApprove={onApprove} onReject={onReject} />
  ));
}

export function DirectorExceptionsCard() {
  const summary = useCertificateExceptionSummary();
  const pending = useCertificateExceptions({ status: 'PENDING' });
  const { rows, requests } = useDescribedExceptions(pending.data);
  const [dialog, setDialog] = useState(null);
  const total = pending.data?.length ?? 0;
  const kpi = summary.isError ? '—' : (summary.data?.pending ?? total);

  return (
    <GlassCard className="flex flex-col gap-2 p-4" aria-labelledby="director-exceptions-title">
      <div className="flex flex-wrap items-center justify-between gap-3 px-2">
        <div className="flex items-center gap-3">
          <div>
            <h2 id="director-exceptions-title" className="font-heading text-size-14-5 font-semibold text-ink">
              Excepciones por reclamo
            </h2>
            <div className="text-size-12 text-ink-faint">Pendientes de decisión</div>
          </div>
          {summary.isLoading ? (
            <Skeleton className="h-8 w-10" />
          ) : (
            <span className="font-heading text-2xl font-bold tabular-nums text-ink" aria-label={`${kpi} pendientes`}>
              {kpi}
            </span>
          )}
        </div>
        <Button type="button" variant="outline" className="min-h-11 md:min-h-0" onClick={() => setDialog({ kind: 'create' })}>
          <FilePlus2 aria-hidden="true" />
          Crear excepción
        </Button>
      </div>
      <Link
        to={DIRECTOR_EXCEPTIONS_PATH}
        className="flex items-center justify-end gap-1 rounded-xl px-2 text-size-12-5 font-semibold text-primary hover:underline"
      >
        {pendingLinkLabel(pending.isError ? 0 : total)}
        <ChevronRight className="h-4 w-4" aria-hidden="true" />
      </Link>
      <CardBody
        query={pending}
        rows={rows}
        onApprove={(exception) => setDialog({ kind: 'approve', exception })}
        onReject={(exception) => setDialog({ kind: 'reject', exception })}
      />
      {dialog?.kind === 'create' && <CreateDirectExceptionDialog requests={requests.data} onClose={() => setDialog(null)} />}
      {(dialog?.kind === 'approve' || dialog?.kind === 'reject') && (
        <DecideExceptionDialog mode={dialog.kind} exception={dialog.exception} onClose={() => setDialog(null)} />
      )}
    </GlassCard>
  );
}
