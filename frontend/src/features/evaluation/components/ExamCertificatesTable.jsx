import { Ban } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { DataTable } from '@/components/table/DataTable';
import { candidateState, hasIssuedCertificate, isSelectable } from '@/features/evaluation/lib/examCertificates';

function columnsFor({ selected, busy, canRevoke, onToggle, onRevoke }) {
  return [
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (row) => row.studentName,
      enableSorting: false,
      meta: { cardTitle: true },
      cell: ({ row }) => {
        const candidate = row.original;
        const name = candidate.studentName;
        const details = (
          <span className="flex flex-col">
            <span className="font-heading text-size-14 font-semibold leading-tight text-ink">{name}</span>
            <span className="text-size-12 text-ink-soft">Doc. {candidate.maskedDocument}</span>
          </span>
        );
        if (!isSelectable(candidate)) return <div className="flex min-h-11 items-center gap-3 pl-7 md:min-h-0">{details}</div>;
        return (
          <label className="flex min-h-11 cursor-pointer items-center gap-3 md:min-h-0">
            <Checkbox
              checked={selected.has(candidate.registrationId)}
              disabled={busy}
              onCheckedChange={(checked) => onToggle(candidate.registrationId, checked === true)}
              aria-label={`Seleccionar a ${name}`}
            />
            {details}
          </label>
        );
      },
    },
    {
      id: 'status',
      header: 'Constancia de examen',
      accessorFn: (row) => candidateState(row).label,
      enableSorting: false,
      cell: ({ row }) => {
        const state = candidateState(row.original);
        return <StatusPill tone={state.tone}>{state.label}</StatusPill>;
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const candidate = row.original;
        if (!canRevoke || !hasIssuedCertificate(candidate)) return null;
        const name = candidate.studentName;
        return (
          <div className="flex items-center justify-end">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="min-h-11 text-danger md:min-h-8"
              aria-label={`Revocar constancia de ${name}`}
              onClick={() => onRevoke(candidate)}
            >
              <Ban aria-hidden="true" />
              Revocar
            </Button>
          </div>
        );
      },
    },
  ];
}

export function ExamCertificatesTable({ candidates, selected, busy, canRevoke, onToggle, onRevoke }) {
  const columns = columnsFor({ selected, busy, canRevoke, onToggle, onRevoke });
  return <DataTable columns={columns} data={candidates} getRowId={(row) => String(row.registrationId)} minWidth={520} />;
}
