import { Ban, Download } from 'lucide-react';
import { formatearFechaCorta } from '@/lib/avisos';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { DataTable } from '@/components/table/DataTable';
import { candidateState, hasIssuedCertificate, isRevoked, isSelectable, issuedAtOf, revocationDetail } from '@/features/evaluation/lib/examCertificates';

function columnsFor({ selected, busy, canRevoke, downloadingId, onToggle, onRevoke, onDownload }) {
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
        const candidate = row.original;
        const issuedAt = issuedAtOf(candidate);
        const revocation = revocationDetail(candidate);
        return (
          <div className="flex flex-col items-start gap-1">
            <StatusPill tone={state.tone}>
              {isRevoked(candidate) && <Ban aria-hidden="true" className="size-3" />}
              {state.label}
            </StatusPill>
            {issuedAt && <span className="text-size-12 text-ink-soft">Emitida el {formatearFechaCorta(issuedAt)}</span>}
            {revocation && (
              <span className="text-size-12 text-ink-soft">
                {revocation.reason ? `${revocation.reason}. ` : ''}Revocada el {formatearFechaCorta(revocation.revokedAt)}
              </span>
            )}
          </div>
        );
      },
    },
    {
      id: 'actions',
      header: '',
      enableSorting: false,
      cell: ({ row }) => {
        const candidate = row.original;
        if (!hasIssuedCertificate(candidate)) return null;
        const name = candidate.studentName;
        return (
          <div className="flex items-center justify-end gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="min-h-11 md:min-h-8"
              aria-label={`Descargar PDF de ${name}`}
              loading={downloadingId === candidate.certificateId}
              disabled={downloadingId != null}
              onClick={() => onDownload(candidate)}
            >
              <Download aria-hidden="true" />
              PDF
            </Button>
            {canRevoke && (
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
            )}
          </div>
        );
      },
    },
  ];
}

export function ExamCertificatesTable({ candidates, selected, busy, canRevoke, downloadingId, onToggle, onRevoke, onDownload }) {
  const columns = columnsFor({ selected, busy, canRevoke, downloadingId, onToggle, onRevoke, onDownload });
  return <DataTable columns={columns} data={candidates} getRowId={(row) => String(row.registrationId)} minWidth={520} />;
}
