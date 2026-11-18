import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { ChoiceChip } from '@/components/ChoiceChip';
import { EmptyState } from '@/components/EmptyState';
import { Paginador } from '@/components/Paginador';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { formatDateTime } from '@/lib/billing';
import { useBroadcastList } from '../hooks/useBroadcasts';
import { BROADCAST_STATUS, CATEGORY_LABEL, CHANNEL_LABEL, STATUS_FILTERS } from '../lib/broadcastLabels';

const PAGE_SIZE = 20;

const COLUMNS = [
  { accessorKey: 'name', header: 'Comunicación', meta: { stickyStart: true } },
  { id: 'channel', header: 'Canal', cell: ({ row }) => CHANNEL_LABEL[row.original.channel] ?? row.original.channel },
  { id: 'category', header: 'Categoría', cell: ({ row }) => CATEGORY_LABEL[row.original.category] ?? row.original.category },
  {
    accessorKey: 'status',
    header: 'Estado',
    cell: ({ row }) => {
      const status = BROADCAST_STATUS[row.original.status];
      return <StatusPill tone={status?.tone}>{status?.label ?? row.original.status}</StatusPill>;
    },
  },
  { accessorKey: 'createdAt', header: 'Creada', cell: ({ row }) => formatDateTime(row.original.createdAt) },
  {
    accessorKey: 'confirmedAt',
    header: 'Confirmada',
    cell: ({ row }) => (row.original.confirmedAt ? formatDateTime(row.original.confirmedAt) : '—'),
  },
];

export function BroadcastListPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState(null);
  const [page, setPage] = useState(1);
  const { data, isLoading, isError, refetch } = useBroadcastList(status, page);
  const totalPages = Math.max(1, Math.ceil((data?.totalElements ?? 0) / PAGE_SIZE));

  function selectStatus(next) {
    setStatus(next);
    setPage(1);
  }

  return (
    <PageContainer title="Comunicaciones" description="Registro de envíos masivos.">
      <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar por estado">
        <ChoiceChip selected={status === null} onClick={() => selectStatus(null)}>Todos</ChoiceChip>
        {STATUS_FILTERS.map((value) => (
          <ChoiceChip key={value} selected={status === value} onClick={() => selectStatus(value)}>
            {BROADCAST_STATUS[value].label}
          </ChoiceChip>
        ))}
      </div>
      <GlassCard className="p-2 sm:p-4">
        <DataTable
          columns={COLUMNS}
          data={data?.items ?? []}
          getRowId={(broadcast) => String(broadcast.id)}
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
          errorMessage="No pudimos cargar las comunicaciones. Revisá tu conexión y probá de nuevo."
          onRowClick={(row) => navigate(String(row.original.id))}
          emptyState={
            <EmptyState
              icon="📨"
              title="Todavía no hay comunicaciones"
              description="Cuando crees un envío masivo, vas a ver acá su estado y avance."
            />
          }
        />
        <Paginador page={page} totalPages={totalPages} onChange={setPage} />
      </GlassCard>
    </PageContainer>
  );
}
