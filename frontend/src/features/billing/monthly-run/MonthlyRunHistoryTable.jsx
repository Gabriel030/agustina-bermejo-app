import { useMemo } from 'react';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { formatDateTime } from '@/lib/billing';
import { RUN_STATUS_LABELS, RUN_STATUS_TONES, RUN_TRIGGER_LABELS, formatPeriod, periodOf } from './monthlyRunLabels';

const COLUMNS = [
  {
    id: 'period',
    header: 'Período',
    accessorFn: (run) => run.period,
    cell: ({ row }) => <span className="font-semibold text-ink">{formatPeriod(periodOf(row.original))}</span>,
    meta: { cardTitle: true },
  },
  {
    id: 'status',
    header: 'Estado',
    accessorFn: (run) => run.status,
    cell: ({ row }) => <StatusPill tone={RUN_STATUS_TONES[row.original.status] ?? 'neutral'}>{RUN_STATUS_LABELS[row.original.status] ?? row.original.status}</StatusPill>,
    meta: { filterable: true, filterTitle: 'estado', filterLabel: (status) => RUN_STATUS_LABELS[status] ?? status },
  },
  {
    id: 'trigger',
    header: 'Disparador',
    accessorFn: (run) => run.trigger,
    cell: ({ row }) => RUN_TRIGGER_LABELS[row.original.trigger] ?? row.original.trigger,
    meta: { filterable: true, filterTitle: 'disparador', filterLabel: (trigger) => RUN_TRIGGER_LABELS[trigger] ?? trigger },
  },
  {
    id: 'issued',
    header: 'Emitidas',
    accessorFn: (run) => run.counters?.issued ?? 0,
    cell: ({ row }) => <span className="tabular-nums">{row.original.counters?.issued ?? 0}</span>,
    meta: { align: 'right' },
  },
  {
    id: 'problems',
    header: 'Fallidas',
    accessorFn: (run) => (run.counters?.failed ?? 0) + (run.counters?.retriesExhausted ?? 0),
    cell: ({ row }) => <span className="tabular-nums">{(row.original.counters?.failed ?? 0) + (row.original.counters?.retriesExhausted ?? 0)}</span>,
    meta: { align: 'right' },
  },
  {
    id: 'startedAt',
    header: 'Inicio',
    accessorFn: (run) => run.startedAt,
    cell: ({ row }) => formatDateTime(row.original.startedAt),
  },
];

export function MonthlyRunHistoryTable({ runs, total, isLoading, isError, onRetry, onSelectRun }) {
  const columns = useMemo(() => COLUMNS, []);
  return (
    <div className="flex flex-col gap-2">
      <DataTable
        columns={columns}
        data={runs}
        getRowId={(run) => String(run.id)}
        isLoading={isLoading}
        isError={isError}
        onRetry={onRetry}
        errorMessage="No pudimos cargar el historial de corridas."
        emptyState={<EmptyState icon="🗓️" title="Todavía no hay corridas anteriores" />}
        onRowClick={onSelectRun}
        minWidth={640}
      />
      {!isLoading && !isError && runs.length > 0 && (
        <p className="px-4 pb-2 text-xs text-ink-faint">
          Mostrando {runs.length} de {total}
        </p>
      )}
    </div>
  );
}
