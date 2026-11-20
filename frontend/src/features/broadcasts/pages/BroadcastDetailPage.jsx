import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { ProgressBar } from '@/components/ProgressBar';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { ModalMotivo } from '@/components/ModalMotivo';
import { Paginador } from '@/components/Paginador';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/table/DataTable';
import { describeError } from '@/api/describeError';
import { formatDateTime } from '@/lib/billing';
import { useBroadcast, useBroadcastRecipients, useCancelBroadcast } from '../hooks/useBroadcasts';
import {
  BROADCAST_STATUS,
  CANCELLABLE_STATUSES,
  EXCLUSION_REASON_LABEL,
  RECIPIENT_STATUS,
  summarizeRecipientCounts,
} from '../lib/broadcastLabels';

const RECIPIENTS_PAGE_SIZE = 50;
const MIN_REASON_LENGTH = 10;

const RECIPIENT_COLUMNS = [
  { accessorKey: 'fullName', header: 'Destinatario', meta: { stickyStart: true } },
  { id: 'address', header: 'Dirección', cell: ({ row }) => row.original.addressMasked ?? '—' },
  {
    id: 'status',
    header: 'Estado',
    cell: ({ row }) => {
      const status = RECIPIENT_STATUS[row.original.status];
      return <StatusPill tone={status?.tone}>{status?.label ?? row.original.status}</StatusPill>;
    },
  },
  {
    id: 'exclusionReason',
    header: 'Motivo de exclusión',
    cell: ({ row }) => EXCLUSION_REASON_LABEL[row.original.exclusionReason] ?? '—',
  },
];

function DetailSkeleton() {
  return (
    <PageContainer>
      <Skeleton className="h-10 w-64" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-64 w-full" />
    </PageContainer>
  );
}

export function BroadcastDetailPage() {
  const { broadcastId } = useParams();
  const [recipientsPage, setRecipientsPage] = useState(1);
  const [cancelOpen, setCancelOpen] = useState(false);
  const broadcast = useBroadcast(broadcastId);
  const recipients = useBroadcastRecipients(broadcastId, recipientsPage);
  const cancelBroadcast = useCancelBroadcast(broadcastId);

  if (broadcast.isLoading) return <DetailSkeleton />;
  if (broadcast.isError) {
    return (
      <PageContainer>
        <LoadErrorState error={broadcast.error} onRetry={broadcast.refetch} resource="la comunicación" />
      </PageContainer>
    );
  }

  const data = broadcast.data;
  const status = BROADCAST_STATUS[data.status];
  const summary = summarizeRecipientCounts(data.recipientCounts);
  const totalPages = Math.max(1, Math.ceil((recipients.data?.totalElements ?? 0) / RECIPIENTS_PAGE_SIZE));

  async function submitCancellation(reason) {
    try {
      await cancelBroadcast.mutateAsync(reason);
      toast.success('Cancelaste la comunicación.');
      setCancelOpen(false);
      return true;
    } catch (error) {
      toast.error(describeError(error));
      return false;
    }
  }

  return (
    <PageContainer
      title={data.name}
      description={`Creada ${formatDateTime(data.createdAt)}${data.confirmedAt ? ` · Confirmada ${formatDateTime(data.confirmedAt)}` : ''}`}
      action={
        <div className="flex items-center gap-3">
          <StatusPill tone={status?.tone}>{status?.label ?? data.status}</StatusPill>
          {CANCELLABLE_STATUSES.includes(data.status) && (
            <Button variant="destructive" onClick={() => setCancelOpen(true)}>Cancelar envío</Button>
          )}
        </div>
      }
    >
      <Link to=".." relative="path" className="text-size-12-5 font-semibold text-primary">← Volver a comunicaciones</Link>
      {data.status === 'CANCELLED' && data.cancellationReason && (
        <GlassCard className="p-4 text-sm text-ink-soft">
          <span className="font-semibold text-ink">Motivo de la cancelación: </span>
          {data.cancellationReason}
        </GlassCard>
      )}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Enviados"><span className="font-heading text-2xl font-bold">{summary.sent}</span></StatCard>
        <StatCard label="Fallidos"><span className="font-heading text-2xl font-bold">{summary.failed}</span></StatCard>
        <StatCard label="Excluidos"><span className="font-heading text-2xl font-bold">{summary.excluded}</span></StatCard>
        <StatCard label="Pendientes"><span className="font-heading text-2xl font-bold">{summary.pending}</span></StatCard>
      </div>
      <GlassCard className="p-5">
        <ProgressBar value={summary.percent} label={`${summary.sent} de ${summary.deliverable} enviados`} />
      </GlassCard>
      <GlassCard className="p-2 sm:p-4">
        <DataTable
          columns={RECIPIENT_COLUMNS}
          data={recipients.data?.items ?? []}
          getRowId={(recipient) => String(recipient.personId)}
          isLoading={recipients.isLoading}
          isError={recipients.isError}
          onRetry={recipients.refetch}
          errorMessage="No pudimos cargar los destinatarios."
          emptyState={<EmptyState title="Sin destinatarios" description="Esta comunicación no tiene destinatarios congelados." />}
        />
        <Paginador page={recipientsPage} totalPages={totalPages} onChange={setRecipientsPage} />
      </GlassCard>
      <ModalMotivo
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        onConfirmar={submitCancellation}
        confirmando={cancelBroadcast.isPending}
        requerido
        titulo="Cancelar envío"
        descripcion="Los destinatarios pendientes no recibirán el mensaje. Contanos el motivo."
        placeholder="Motivo de la cancelación"
        confirmarLabel="Cancelar envío"
        confirmarVariant="destructive"
        minLength={MIN_REASON_LENGTH}
        disableUntilMinLength
        showCounter
        mensajeRequerido="El motivo de la cancelación es obligatorio."
      />
    </PageContainer>
  );
}
