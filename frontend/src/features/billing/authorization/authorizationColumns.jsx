import { formatDate, formatMoney } from '@/lib/billing';
import { ActionsCell, DueCell, RequesterCell, StatusCell, SubjectCell, TypeCell } from './AuthorizationCells';
import { daysUntil, displayStatusKey, familyLabel, REQUEST_STATUS } from './authorizationRequests';

export function buildAuthorizationColumns({ isDirector, history, user, roleBase, onWithdraw }) {
  const columns = [
    {
      id: 'type',
      header: 'Tipo',
      accessorFn: (request) => request.family,
      enableSorting: false,
      cell: ({ row }) => <TypeCell request={row.original} />,
      meta: { filterable: true, filterTitle: 'tipo', filterLabel: (family) => familyLabel(family) },
    },
    {
      id: 'subject',
      header: 'Qué se pide',
      accessorFn: (request) => request.studentName ?? request.summary,
      enableSorting: false,
      cell: ({ row }) => (
        <SubjectCell request={row.original} roleBase={roleBase} />
      ),
    },
    {
      id: 'amount',
      header: 'Monto',
      accessorFn: (request) => (request.amount ? Number(request.amount.amount) : undefined),
      sortUndefined: 'last',
      cell: ({ row }) =>
        row.original.amount ? (
          <span className="font-semibold tabular-nums text-ink">{formatMoney(row.original.amount)}</span>
        ) : (
          <span className="text-ink-faint">—</span>
        ),
      meta: { align: 'right' },
    },
  ];

  if (isDirector) {
    columns.push({
      id: 'requestedBy',
      header: 'Pedido por',
      accessorFn: (request) => request.requestedBy?.name ?? '—',
      enableSorting: false,
      cell: ({ row }) => <RequesterCell request={row.original} />,
      meta: { filterable: true, filterTitle: 'quién lo pidió' },
    });
  }

  columns.push({
    id: 'requestedAt',
    header: 'Recibido',
    accessorFn: (request) => request.requestedAt,
    cell: ({ row }) => <span className="text-ink-soft">{formatDate(row.original.requestedAt)}</span>,
  });

  if (history) {
    columns.push({
      id: 'status',
      header: 'Estado',
      accessorFn: (request) => displayStatusKey(request),
      enableSorting: false,
      cell: ({ row }) => <StatusCell request={row.original} />,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: (key) => REQUEST_STATUS[key]?.label ?? key },
    });
  } else {
    columns.push({
      id: 'expiresOn',
      header: 'Vence',
      accessorFn: (request) => (request.expiresOn ? daysUntil(request.expiresOn) : undefined),
      sortUndefined: 'last',
      cell: ({ row }) => <DueCell request={row.original} />,
    });
  }

  columns.push({
    id: 'actions',
    header: '',
    enableSorting: false,
    cell: ({ row }) => <ActionsCell request={row.original} user={user} onWithdraw={onWithdraw} />,
    meta: { headerClassName: 'w-24' },
  });

  return columns;
}
