import { useMemo, useState } from 'react';
import { MoreHorizontal, RotateCw } from 'lucide-react';
import { toast } from 'sonner';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { formatDateTime } from '@/lib/billing';
import { useMonthlyRunItems, useRetryFailedMonthlyRunItems, useRetryMonthlyRunItem } from '@/features/billing/hooks/useMonthlyRuns';
import { ENROLLMENT_TYPE_LABELS, ITEM_RESULT_LABELS, ITEM_RESULT_TONES, isRetryableResult } from './monthlyRunLabels';

const PAGE_STEP = 100;

function enrollmentLabel(item) {
  return `Inscripción #${item.enrollmentId}`;
}

function ItemDetail({ item }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 p-4 text-sm sm:grid-cols-2">
      <div>
        <dt className="text-xs font-bold uppercase text-ink-faint">Motivo</dt>
        <dd className="text-ink">{item.message || 'Sin detalle'}</dd>
      </div>
      <div>
        <dt className="text-xs font-bold uppercase text-ink-faint">Código</dt>
        <dd className="text-ink">{item.errorCode || item.decisionCode || '—'}</dd>
      </div>
      <div>
        <dt className="text-xs font-bold uppercase text-ink-faint">Intentos</dt>
        <dd className="tabular-nums text-ink">{item.attempts ?? 0}</dd>
      </div>
      <div>
        <dt className="text-xs font-bold uppercase text-ink-faint">Próximo reintento</dt>
        <dd className="text-ink">{item.nextRetryAt ? formatDateTime(item.nextRetryAt) : '—'}</dd>
      </div>
    </dl>
  );
}

function RowMenu({ item, monthlyRunId }) {
  const retryItem = useRetryMonthlyRunItem();
  const handleRetry = () =>
    retryItem.mutate(
      { monthlyRunId, itemId: item.id },
      {
        onSuccess: (retried) =>
          retried && isRetryableResult(retried.result)
            ? toast.error(`${enrollmentLabel(item)} sigue fallando`)
            : toast.success(`${enrollmentLabel(item)} reintentada`),
        onError: (error) => toast.error(error.message),
      },
    );
  if (!isRetryableResult(item.result)) return <span className="text-ink-faint" aria-label="Sin acciones disponibles">—</span>;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="icon" aria-label={`Acciones de ${enrollmentLabel(item)}`}>
          <MoreHorizontal aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="bg-dialog-bg backdrop-blur-[28px] backdrop-saturate-[1.6]">
        <DropdownMenuItem disabled={retryItem.isPending} onSelect={handleRetry}>
          <RotateCw aria-hidden="true" /> Reintentar ahora
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function MonthlyRunItemsTable({ monthlyRunId }) {
  const [size, setSize] = useState(PAGE_STEP);
  const items = useMonthlyRunItems(monthlyRunId, { size });
  const retryFailed = useRetryFailedMonthlyRunItems();
  const data = items.data?.items ?? [];
  const total = items.data?.total ?? 0;
  const retryable = data.filter((item) => isRetryableResult(item.result));

  const handleRetryFailed = () =>
    retryFailed.mutate(
      { monthlyRunId, items: data },
      {
        onSuccess: ({ recovered, stillFailing, errored }) =>
          toast.message(`Recuperadas: ${recovered}. Siguen fallando: ${stillFailing + errored}.`),
        onError: (error) => toast.error(error.message),
      },
    );

  const columns = useMemo(
    () => [
      {
        id: 'enrollment',
        header: 'Inscripción',
        accessorFn: enrollmentLabel,
        cell: ({ row }) => <span className="font-semibold text-ink">{enrollmentLabel(row.original)}</span>,
        meta: { cardTitle: true },
      },
      {
        id: 'type',
        header: 'Tipo',
        accessorFn: (item) => item.enrollmentType,
        cell: ({ row }) => ENROLLMENT_TYPE_LABELS[row.original.enrollmentType] ?? row.original.enrollmentType,
        meta: { filterable: true, filterTitle: 'tipo', filterLabel: (type) => ENROLLMENT_TYPE_LABELS[type] ?? type },
      },
      {
        id: 'result',
        header: 'Resultado',
        accessorFn: (item) => item.result,
        cell: ({ row }) => (
          <StatusPill tone={ITEM_RESULT_TONES[row.original.result] ?? 'neutral'}>{ITEM_RESULT_LABELS[row.original.result] ?? row.original.result}</StatusPill>
        ),
        meta: { filterable: true, filterTitle: 'resultado', filterLabel: (result) => ITEM_RESULT_LABELS[result] ?? result },
      },
      {
        id: 'attempts',
        header: 'Intentos',
        accessorFn: (item) => item.attempts ?? 0,
        cell: ({ row }) => <span className="tabular-nums">{row.original.attempts ?? 0}</span>,
        meta: { align: 'right' },
      },
      {
        id: 'actions',
        header: '',
        enableSorting: false,
        cell: ({ row }) => <RowMenu item={row.original} monthlyRunId={monthlyRunId} />,
        meta: { headerClassName: 'w-12' },
      },
    ],
    [monthlyRunId],
  );

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2 px-4 pt-3">
        <p className="text-xs text-ink-faint">
          Mostrando {data.length} de {total}
        </p>
        <Button type="button" variant="outline" size="sm" disabled={retryFailed.isPending || retryable.length === 0} onClick={handleRetryFailed}>
          <RotateCw aria-hidden="true" /> Reintentar fallidos
        </Button>
      </div>
      <DataTable
        columns={columns}
        data={data}
        getRowId={(item) => String(item.id)}
        isLoading={items.isLoading}
        isError={items.isError}
        onRetry={items.refetch}
        errorMessage="No pudimos cargar los ítems de la corrida."
        emptyState={<EmptyState icon="📭" title="Esta corrida no tiene ítems" />}
        renderExpanded={(row) => <ItemDetail item={row.original} />}
        minWidth={640}
      />
      {data.length < total && (
        <div className="flex justify-center pb-3">
          <Button type="button" variant="outline" size="sm" onClick={() => setSize((current) => current + PAGE_STEP)}>
            Ver más
          </Button>
        </div>
      )}
    </div>
  );
}
