import { useMemo } from 'react';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { DataTable } from '@/components/table/DataTable';
import { useNightlyRunStepItems } from '@/features/billing/hooks/useNightlyRuns';
import { HELD_ACCOUNT_CODE, ITEM_STATUS_LABELS, ITEM_STATUS_TONES, SUBJECT_TYPE_LABELS } from './nightlyRunLabels';

function subjectLabel(item) {
  return `${SUBJECT_TYPE_LABELS[item.subjectType] ?? item.subjectType} #${item.subjectId}`;
}

const COLUMNS = [
  {
    id: 'subject',
    header: 'Sujeto',
    accessorFn: subjectLabel,
    cell: ({ row }) => (
      <span className="flex flex-wrap items-center gap-2 font-semibold text-ink">
        {subjectLabel(row.original)}
        {row.original.decisionCode === HELD_ACCOUNT_CODE && <StatusPill tone="warning">Cuenta retenida</StatusPill>}
      </span>
    ),
    meta: { cardTitle: true },
  },
  {
    id: 'status',
    header: 'Resultado',
    accessorFn: (item) => item.status,
    cell: ({ row }) => <StatusPill tone={ITEM_STATUS_TONES[row.original.status] ?? 'neutral'}>{ITEM_STATUS_LABELS[row.original.status] ?? row.original.status}</StatusPill>,
    meta: { filterable: true, filterTitle: 'resultado', filterLabel: (status) => ITEM_STATUS_LABELS[status] ?? status },
  },
  {
    id: 'reason',
    header: 'Motivo',
    enableSorting: false,
    accessorFn: (item) => item.message,
    cell: ({ row }) => (
      <span className="flex flex-col gap-0.5">
        <span className="text-ink">{row.original.message}</span>
        <span className="text-xs text-ink-faint">{row.original.decisionCode || row.original.errorCode}</span>
      </span>
    ),
  },
];

export function StepItemsTable({ nightlyRunId, step }) {
  const items = useNightlyRunStepItems(nightlyRunId, step);
  const columns = useMemo(() => COLUMNS, []);

  return (
    <DataTable
      columns={columns}
      data={items.data ?? []}
      getRowId={(item) => String(item.id)}
      isLoading={items.isLoading}
      isError={items.isError}
      onRetry={items.refetch}
      errorMessage="No pudimos cargar los omitidos y con error de este paso."
      emptyState={<EmptyState icon="✅" title="Nada omitido ni con error" />}
      minWidth={520}
    />
  );
}
