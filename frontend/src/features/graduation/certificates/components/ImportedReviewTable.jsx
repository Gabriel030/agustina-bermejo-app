import { useMemo } from 'react';
import { Lock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { DataTable } from '@/components/table/DataTable';
import {
  LOCKED_EXPLANATION,
  LOCKED_LABEL,
  canDisable,
  canEnable,
  reviewStatusOf,
} from '@/features/graduation/certificates/lib/importedReview';

const DASH = '—';

function reviewColumns({ selected, lockedIds, canSelectMore, onToggle, onEnable, onDisable }) {
  return [
    {
      id: 'select',
      header: '',
      enableSorting: false,
      meta: { headerClassName: 'w-12' },
      cell: ({ row }) => {
        const { graduationRequestId, studentFullName } = row.original;
        const checked = selected.has(graduationRequestId);
        return (
          <Checkbox
            checked={checked}
            disabled={lockedIds.has(graduationRequestId) || (!checked && !canSelectMore)}
            onCheckedChange={() => onToggle(row.original)}
            aria-label={`Seleccionar a ${studentFullName}`}
          />
        );
      },
    },
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (row) => row.studentFullName,
      enableSorting: false,
      meta: { cardTitle: true },
      cell: ({ row }) => (
        <div className="flex flex-col gap-0.5">
          <span className="font-heading text-size-14 font-semibold leading-tight text-ink">{row.original.studentFullName}</span>
          <span className="text-size-12 tabular-nums text-ink-faint">{row.original.documentNumber}</span>
        </div>
      ),
    },
    {
      id: 'title',
      header: 'Título',
      accessorFn: (row) => row.titleName,
      enableSorting: false,
      cell: ({ row }) => <span className="text-size-13 text-ink">{row.original.titleName}</span>,
    },
    {
      id: 'legacyTitleNumber',
      header: 'N.º de título',
      accessorFn: (row) => row.legacyTitleNumber,
      enableSorting: false,
      meta: { headerClassName: 'whitespace-nowrap' },
      cell: ({ row }) => <span className="text-size-13 tabular-nums text-ink">{row.original.legacyTitleNumber ?? DASH}</span>,
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (row) => reviewStatusOf(row.reviewStatus).label,
      enableSorting: false,
      cell: ({ row }) => {
        const locked = lockedIds.has(row.original.graduationRequestId);
        const status = reviewStatusOf(row.original.reviewStatus);
        return (
          <div className="flex flex-col items-start gap-1">
            <StatusPill tone={status.tone}>{status.label}</StatusPill>
            {locked && (
              <span className="inline-flex items-center gap-1 text-size-12 font-semibold text-ink-faint" title={LOCKED_EXPLANATION}>
                <Lock aria-hidden="true" className="size-3.5" />
                {LOCKED_LABEL}
              </span>
            )}
            {locked && <span className="max-w-64 text-size-12 text-ink-faint">{LOCKED_EXPLANATION}</span>}
          </div>
        );
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const { graduationRequestId, studentFullName } = row.original;
        const locked = lockedIds.has(graduationRequestId);
        return (
          <div className="flex items-center justify-end gap-2">
            {canDisable(row.original) && (
              <Button
                size="sm"
                variant="outline"
                disabled={locked}
                aria-label={`Deshabilitar certificado de ${studentFullName}`}
                onClick={() => onDisable(row.original)}
              >
                Deshabilitar
              </Button>
            )}
            {canEnable(row.original) && (
              <Button
                size="sm"
                variant="primary"
                disabled={locked}
                aria-label={`Habilitar certificado de ${studentFullName}`}
                onClick={() => onEnable(row.original)}
              >
                Habilitar
              </Button>
            )}
          </div>
        );
      },
    },
  ];
}

export function ImportedReviewTable({ rows, query, isLoading, isError, onRetry, selected, lockedIds, canSelectMore, onToggle, onEnable, onDisable }) {
  const columns = useMemo(
    () => reviewColumns({ selected, lockedIds, canSelectMore, onToggle, onEnable, onDisable }),
    [selected, lockedIds, canSelectMore, onToggle, onEnable, onDisable],
  );
  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(row) => String(row.graduationRequestId)}
      isLoading={isLoading}
      isError={isError}
      onRetry={onRetry}
      errorMessage="No pudimos cargar los egresos importados"
      emptyState={
        <EmptyState
          title="No hay egresos para revisar"
          description={query ? 'Probá con otro nombre o documento.' : 'Cuando haya egresos importados en este estado, aparecen acá.'}
        />
      }
    />
  );
}
