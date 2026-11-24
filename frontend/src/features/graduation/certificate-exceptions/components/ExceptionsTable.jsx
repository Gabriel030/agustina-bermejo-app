import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { DataTable } from '@/components/table/DataTable';
import { ExceptionStatusPill } from '@/features/graduation/certificate-exceptions/components/ExceptionStatusPill';
import { formatFullDate, organismLabelOf } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

function buildColumns({ detailPath, showRequester }) {
  const columns = [
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (row) => row.studentName,
      enableSorting: false,
      meta: { cardTitle: true },
      cell: ({ row }) => (
        <div className="flex flex-col">
          <Link className="font-heading text-size-14 font-semibold leading-tight text-ink hover:underline" to={detailPath(row.original.id)}>
            {row.original.studentName}
          </Link>
          <span className="text-size-12 text-ink-soft">{row.original.programName}</span>
        </div>
      ),
    },
    {
      id: 'organism',
      header: 'Organismo',
      accessorFn: (row) => organismLabelOf(row.organismKind),
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="text-size-13 text-ink">{organismLabelOf(row.original.organismKind)}</span>
          <span className="text-size-12 text-ink-soft">{row.original.organismReference}</span>
        </div>
      ),
    },
  ];
  if (showRequester) {
    columns.push({
      id: 'requester',
      header: 'Pidió',
      accessorFn: (row) => row.requesterName,
      enableSorting: false,
      cell: ({ row }) => <span className="text-size-13 text-ink">{row.original.requesterName}</span>,
    });
  }
  columns.push(
    {
      id: 'requestedAt',
      header: 'Pedida',
      accessorFn: (row) => row.requestedAt,
      meta: { headerClassName: 'whitespace-nowrap' },
      cell: ({ row }) => <span className="text-size-13 tabular-nums text-ink">{formatFullDate(row.original.requestedAt)}</span>,
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (row) => row.status,
      enableSorting: false,
      cell: ({ row }) => <ExceptionStatusPill status={row.original.status} validUntil={row.original.validUntil} />,
    },
  );
  return columns;
}

export function ExceptionsTable({ rows, query, detailPath, showRequester = false, emptyState, errorMessage }) {
  const navigate = useNavigate();
  const columns = useMemo(() => buildColumns({ detailPath, showRequester }), [detailPath, showRequester]);
  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(row) => String(row.id)}
      initialSorting={[{ id: 'requestedAt', desc: true }]}
      isLoading={query.isLoading}
      isError={query.isError}
      onRetry={query.refetch}
      errorMessage={errorMessage}
      onRowClick={(row) => navigate(detailPath(row.id))}
      emptyState={emptyState}
    />
  );
}
