import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { formatDateTime } from '@/lib/billing';
import { AUDIT_LABELS } from './paymentMethods';
import { AUDIT_FETCH_SIZE, useInstituteSettingsAudit } from './useInstituteSettings';

const PAGE_SIZE = 10;

const COLUMNS = [
  {
    id: 'changedAt',
    header: 'Fecha',
    accessorKey: 'changedAt',
    cell: ({ row }) => formatDateTime(row.original.changedAt),
  },
  {
    id: 'code',
    header: 'Qué cambió',
    accessorKey: 'code',
    enableColumnFilter: true,
    meta: { filterable: true, filterTitle: 'Qué cambió', filterLabel: (value) => AUDIT_LABELS[value] ?? value },
    cell: ({ row }) => <span className="font-semibold text-ink">{AUDIT_LABELS[row.original.code] ?? row.original.code}</span>,
  },
  {
    id: 'after',
    header: 'Cambio',
    accessorKey: 'after',
    enableSorting: false,
    cell: ({ row }) => (
      <StatusPill tone={row.original.after ? 'success' : 'neutral'} dot>
        {row.original.after ? 'Activado' : 'Desactivado'}
      </StatusPill>
    ),
  },
  {
    id: 'actor',
    header: 'Quién',
    accessorFn: (change) => change.actor?.fullName ?? 'Sin dato',
  },
  {
    id: 'reason',
    header: 'Motivo',
    accessorKey: 'reason',
    enableSorting: false,
    meta: { cardBlock: true },
  },
];

export function PaymentMethodsAuditTab() {
  const query = useInstituteSettingsAudit({ enabled: true });
  const truncated = query.data && query.data.totalElements > AUDIT_FETCH_SIZE;

  return (
    <GlassCard className="flex flex-col gap-3 p-2 sm:p-4">
      <DataTable
        columns={COLUMNS}
        data={query.data?.content}
        getRowId={(change) => String(change.id)}
        isLoading={query.isPending}
        isError={query.isError}
        onRetry={() => query.refetch()}
        errorMessage="No pudimos cargar el historial."
        emptyState={<EmptyState icon="🕘" title="Todavía no hay cambios" description="Cuando se active o desactive algo, queda registrado acá." />}
        pageSize={PAGE_SIZE}
      />
      {truncated && <p className="px-2 text-xs text-ink-faint">Se muestran los {AUDIT_FETCH_SIZE} cambios más recientes.</p>}
    </GlassCard>
  );
}
