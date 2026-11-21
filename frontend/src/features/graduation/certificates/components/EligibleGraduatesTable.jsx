import { useMemo } from 'react';
import { Eye } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { StatusPill } from '@/components/StatusPill';
import { EmptyState } from '@/components/EmptyState';
import { DataTable } from '@/components/table/DataTable';
import { formatearFecha } from '@/lib/comisiones';
import { eligibleKey, eligibleStatusOf, isSelectable } from '@/features/graduation/certificates/lib/staffGraduationCertificates';

const DASH = '—';

function eligibleColumns({ selected, canSelectMore, onToggle, onPreview, onIssue }) {
  return [
    {
      id: 'select',
      header: '',
      enableSorting: false,
      meta: { headerClassName: 'w-12' },
      cell: ({ row }) => {
        const name = row.original.studentFullName;
        const checked = selected.has(eligibleKey(row.original));
        return (
          <Checkbox
            checked={checked}
            disabled={!isSelectable(row.original) || (!checked && !canSelectMore)}
            onCheckedChange={() => onToggle(row.original)}
            aria-label={`Seleccionar a ${name}`}
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
      cell: ({ row }) => <span className="font-heading text-size-14 font-semibold leading-tight text-ink">{row.original.studentFullName}</span>,
    },
    {
      id: 'title',
      header: 'Título',
      accessorFn: (row) => row.titleName,
      enableSorting: false,
      cell: ({ row }) => <span className="text-size-13 text-ink">{row.original.titleName}</span>,
    },
    {
      id: 'graduationDate',
      header: 'Egreso',
      accessorFn: (row) => row.graduationDate,
      meta: { headerClassName: 'whitespace-nowrap' },
      cell: ({ row }) => <span className="text-size-13 text-ink">{formatearFecha(row.original.graduationDate) ?? DASH}</span>,
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (row) => eligibleStatusOf(row.status).label,
      enableSorting: false,
      cell: ({ row }) => {
        const status = eligibleStatusOf(row.original.status);
        return <StatusPill tone={status.tone}>{status.label}</StatusPill>;
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const name = row.original.studentFullName;
        const blocked = !isSelectable(row.original);
        return (
          <div className="flex items-center justify-end gap-2">
            <Button size="sm" variant="outline" aria-label={`Vista previa de ${name}`} onClick={() => onPreview(row.original)}>
              <Eye aria-hidden="true" />
              Vista previa
            </Button>
            <Button size="sm" variant="primary" disabled={blocked} aria-label={`Emitir certificado de ${name}`} onClick={() => onIssue(row.original)}>
              Emitir
            </Button>
          </div>
        );
      },
    },
  ];
}

export function EligibleGraduatesTable({ rows, query, isLoading, isError, onRetry, selected, canSelectMore, onToggle, onPreview, onIssue }) {
  const columns = useMemo(
    () => eligibleColumns({ selected, canSelectMore, onToggle, onPreview, onIssue }),
    [selected, canSelectMore, onToggle, onPreview, onIssue],
  );
  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={eligibleKey}
      isLoading={isLoading}
      isError={isError}
      onRetry={onRetry}
      errorMessage="No pudimos cargar los egresados"
      emptyState={
        <EmptyState
          title="No hay egresados para mostrar"
          description={query ? 'Probá con otro nombre o documento.' : 'Cuando un alumno cumpla los requisitos de egreso, aparece acá.'}
        />
      }
    />
  );
}
