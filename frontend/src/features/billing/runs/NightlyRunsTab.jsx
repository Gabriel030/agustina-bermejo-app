import { useMemo, useState } from 'react';
import { Info } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { formatDate } from '@/lib/billing';
import { useNightlyRunStepItems, useNightlyRuns } from '@/features/billing/hooks/useNightlyRuns';
import { StepItemsTable } from './StepItemsTable';
import {
  RUN_STATUS_LABELS,
  RUN_STATUS_TONES,
  STEP_STATUS_LABELS,
  STEP_STATUS_TONES,
  TRIGGER_LABELS,
  formatRunDuration,
  notApplicableReason,
  stepLabel,
} from './nightlyRunLabels';

const SCHEDULE_HINT = 'Corre todos los días a las 03:00: vencimientos, recargos, estado de pago e imputación del saldo a favor. Lo que falla se reprocesa la noche siguiente.';

function StepNote({ nightlyRunId, step }) {
  const items = useNightlyRunStepItems(nightlyRunId, step.step, { enabled: step.failed > 0 });
  if (step.status === 'NOT_APPLICABLE') return <span className="text-ink-soft">{notApplicableReason(step.notApplicableCode)}</span>;
  if (step.failed === 0) return null;
  const first = items.data?.find((item) => item.status === 'FAILED') ?? items.data?.[0];
  if (!first) return null;
  const others = (items.data?.length ?? 1) - 1;
  return (
    <span className="text-danger">
      {first.message}
      {others > 0 && <span className="text-ink-faint"> · y {others} más</span>}
    </span>
  );
}

function buildColumns(nightlyRunId) {
  return [
    {
      id: 'position',
      header: '#',
      enableSorting: false,
      accessorFn: (step) => step.position,
      cell: ({ row }) => <span className="tabular-nums text-ink-faint">{row.original.position}</span>,
    },
    {
      id: 'step',
      header: 'Paso',
      enableSorting: false,
      accessorFn: (step) => stepLabel(step.step),
      cell: ({ row }) => <span className="font-semibold text-ink">{stepLabel(row.original.step)}</span>,
      meta: { cardTitle: true },
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (step) => step.status,
      cell: ({ row }) => <StatusPill tone={STEP_STATUS_TONES[row.original.status] ?? 'neutral'}>{STEP_STATUS_LABELS[row.original.status] ?? row.original.status}</StatusPill>,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: (status) => STEP_STATUS_LABELS[status] ?? status },
    },
    {
      id: 'processed',
      header: 'Procesados',
      accessorFn: (step) => step.processed,
      cell: ({ row }) => <span className="tabular-nums">{row.original.processed}</span>,
    },
    {
      id: 'skipped',
      header: 'Omitidos',
      accessorFn: (step) => step.skipped,
      cell: ({ row }) => <span className="tabular-nums">{row.original.skipped}</span>,
    },
    {
      id: 'failed',
      header: 'Con error',
      accessorFn: (step) => step.failed,
      cell: ({ row }) => (
        <span className={row.original.failed > 0 ? 'font-bold tabular-nums text-danger' : 'tabular-nums text-ink-faint'}>{row.original.failed}</span>
      ),
    },
    {
      id: 'detail',
      header: 'Detalle',
      enableSorting: false,
      accessorFn: (step) => step.notApplicableCode ?? '',
      cell: ({ row }) => <StepNote nightlyRunId={nightlyRunId} step={row.original} />,
    },
  ];
}

function NightOption({ run }) {
  return (
    <span>
      {formatDate(run.businessDate)} · {RUN_STATUS_LABELS[run.status] ?? run.status}
    </span>
  );
}

export function NightlyRunsTab() {
  const runs = useNightlyRuns();
  const [selectedId, setSelectedId] = useState(null);
  const list = useMemo(() => runs.data ?? [], [runs.data]);
  const selected = list.find((run) => String(run.id) === selectedId) ?? list[0];
  const columns = useMemo(() => buildColumns(selected?.id), [selected?.id]);
  const steps = useMemo(() => (selected?.steps ?? []).map((step, index) => ({ ...step, position: index + 1 })), [selected]);

  if (runs.isLoading) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (runs.isError) {
    return (
      <GlassCard>
        <EmptyState
          icon="⚠️"
          title="No pudimos cargar los procesos nocturnos"
          action={
            <Button variant="outline" onClick={() => runs.refetch()}>
              Reintentar
            </Button>
          }
        />
      </GlassCard>
    );
  }

  if (list.length === 0) {
    return (
      <GlassCard>
        <EmptyState icon="🌙" title="Todavía no hay noches registradas" description="Cuando corra el proceso de las 03:00 va a aparecer acá." />
      </GlassCard>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Select value={String(selected.id)} onValueChange={setSelectedId}>
            <SelectTrigger aria-label="Noche" className="w-72">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {list.map((run) => (
                <SelectItem key={run.id} value={String(run.id)}>
                  <NightOption run={run} />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <StatusPill tone={RUN_STATUS_TONES[selected.status] ?? 'neutral'}>{RUN_STATUS_LABELS[selected.status] ?? selected.status}</StatusPill>
          <span className="text-sm tabular-nums text-ink-soft">{formatRunDuration(selected.durationSeconds)}</span>
          <span className="text-sm text-ink-faint">{TRIGGER_LABELS[selected.trigger] ?? selected.trigger}</span>
        </div>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <button type="button" aria-label="Cómo funciona el proceso nocturno" className="rounded-full text-ink-faint hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                <Info className="size-4" />
              </button>
            </TooltipTrigger>
            <TooltipContent className="max-w-xs text-pretty">{SCHEDULE_HINT}</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>
      <GlassCard className="p-0">
        <DataTable
          columns={columns}
          data={steps}
          getRowId={(step) => String(step.id)}
          renderExpanded={(row) => <StepItemsTable nightlyRunId={selected.id} step={row.original.step} />}
          expandLabel="Ver omitidos y con error"
          emptyState={<EmptyState icon="🌙" title="Esta noche no registró pasos" />}
          minWidth={820}
        />
      </GlassCard>
    </div>
  );
}
