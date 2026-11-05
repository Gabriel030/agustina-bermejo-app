import { useMemo } from 'react';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { formatDate } from '@/lib/billing';

const VERSION_STATUS = {
  EFFECTIVE: { label: 'Vigente', tone: 'success' },
  SCHEDULED: { label: 'Programada', tone: 'accent' },
  PROPOSED: { label: 'Pendiente de aprobación', tone: 'warning' },
  SUPERSEDED: { label: 'Reemplazada', tone: 'neutral' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  WITHDRAWN: { label: 'Retirada', tone: 'neutral' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
  CANCELLED: { label: 'Cancelada', tone: 'neutral' },
};

const statusLabel = (status) => VERSION_STATUS[status]?.label ?? status;

function buildColumns() {
  return [
    { id: 'effectiveFrom', header: 'Rige desde', accessorFn: (version) => version.effectiveFrom, cell: ({ row }) => formatDate(row.original.effectiveFrom), meta: { cardTitle: true } },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (version) => version.status,
      cell: ({ row }) => <StatusPill tone={VERSION_STATUS[row.original.status]?.tone ?? 'neutral'}>{statusLabel(row.original.status)}</StatusPill>,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: statusLabel },
    },
    {
      id: 'proposedBy',
      header: 'Propuso',
      accessorFn: (version) => version.proposedByName ?? 'Sin dato',
      meta: { filterable: true, filterTitle: 'quién propuso' },
    },
    {
      id: 'decidedBy',
      header: 'Decidió',
      accessorFn: (version) => version.decidedByName ?? '—',
    },
    { id: 'reason', header: 'Motivo', enableSorting: false, accessorFn: (version) => version.changeReason ?? '', meta: { cardBlock: true } },
  ];
}

export function VersionHistory({ versions, isLoading, isError, onRetry }) {
  const columns = useMemo(buildColumns, []);
  return (
    <DataTable
      columns={columns}
      data={versions ?? []}
      getRowId={(version) => String(version.id)}
      isLoading={isLoading}
      isError={isError}
      onRetry={onRetry}
      errorMessage="No pudimos cargar el historial."
      emptyState={<EmptyState title="Todavía no hay cambios registrados" />}
      initialSorting={[{ id: 'effectiveFrom', desc: true }]}
      minWidth={720}
    />
  );
}
