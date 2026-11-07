import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { formatDate } from '@/lib/billing';
import { useMisPedidosDeAsistencia, useMisComisionesParaAsistencia } from '@/features/attendance/hooks/useDocenteAsistencia';
import {
  NO_REJECTION_REASON_TEXT,
  REQUEST_STATUS,
  STATUS_LABEL,
  reRequestHref,
  sectionLabelOf,
} from '@/features/attendance/lib/attendanceRequests';

const PAGE_SIZE = 10;

function statusLabelOf(status) {
  return STATUS_LABEL[status] ?? status;
}

function RequestDetail({ request, sections }) {
  const rejectionReason = request.rejectionReason?.trim();
  return (
    <div className="grid max-w-3xl gap-3 sm:grid-cols-2">
      <div>
        <div className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">Tu motivo</div>
        <p className="break-words text-[13px] text-ink">{request.reason}</p>
      </div>
      <div>
        <div className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">Pedido #{request.id}</div>
        <p className="text-[13px] text-ink">
          {formatDate(request.classDate)} · de {statusLabelOf(request.previousStatus)} a {statusLabelOf(request.requestedStatus)}
        </p>
      </div>
      {request.status === 'REJECTED' && (
        <div className="rounded-xl bg-danger-bg px-3 py-2.5 sm:col-span-2">
          <div className="text-[11px] font-bold uppercase tracking-wide text-danger">Motivo del rechazo</div>
          {rejectionReason ? (
            <p className="break-words text-[13px] text-ink">{rejectionReason}</p>
          ) : (
            <p className="text-[13px] italic text-ink-soft">{NO_REJECTION_REASON_TEXT}. Consultá con preceptoría.</p>
          )}
          <Link
            to={reRequestHref(request, sections)}
            className="mt-2 inline-block text-[13px] font-bold text-brand hover:underline"
          >
            Volver a pedir en Tomar asistencia · clase del {formatDate(request.classDate)}
          </Link>
        </div>
      )}
      {request.status === 'APPROVED' && (
        <div className="rounded-xl bg-success-bg px-3 py-2.5 text-[13px] font-semibold text-success sm:col-span-2">
          El cambio ya figura en la asistencia de esa clase.
        </div>
      )}
      {request.status === 'PENDING' && (
        <div className="rounded-xl bg-warning-bg px-3 py-2.5 text-[13px] font-semibold text-warning sm:col-span-2">
          Esperando la respuesta de preceptoría.
        </div>
      )}
    </div>
  );
}

function buildColumns() {
  return [
    {
      id: 'createdAt',
      header: 'Pedido el',
      accessorFn: (request) => request.createdAt,
      cell: ({ row }) => <span className="tabular-nums text-ink">{formatDate(row.original.createdAt)}</span>,
    },
    {
      id: 'section',
      header: 'Comisión',
      accessorFn: sectionLabelOf,
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="font-semibold text-ink">{row.original.subjectName}</span>
          <span className="text-xs text-ink-faint">{row.original.sectionName}</span>
        </div>
      ),
      meta: { filterable: true, filterTitle: 'comisión', cardTitle: true },
    },
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (request) => request.studentName,
      enableSorting: false,
      cell: ({ row }) => <span className="text-ink">{row.original.studentName}</span>,
      meta: { filterable: true, filterTitle: 'alumno' },
    },
    {
      id: 'classDate',
      header: 'Clase del',
      accessorFn: (request) => request.classDate,
      enableSorting: false,
      cell: ({ row }) => <span className="tabular-nums text-ink">{formatDate(row.original.classDate)}</span>,
    },
    {
      id: 'change',
      header: 'Cambio pedido',
      accessorFn: (request) => `${request.previousStatus}-${request.requestedStatus}`,
      enableSorting: false,
      cell: ({ row }) => (
        <span className="whitespace-nowrap text-ink">
          <span className="sr-only">De </span>
          {statusLabelOf(row.original.previousStatus)}
          <span aria-hidden="true" className="mx-1 text-ink-faint">→</span>
          <span className="sr-only"> a </span>
          {statusLabelOf(row.original.requestedStatus)}
        </span>
      ),
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (request) => request.status,
      enableSorting: false,
      cell: ({ row }) => {
        const status = REQUEST_STATUS[row.original.status];
        return <StatusPill tone={status?.tone ?? 'neutral'}>{status?.label ?? row.original.status}</StatusPill>;
      },
      meta: {
        filterable: true,
        filterTitle: 'estado',
        filterLabel: (value) => REQUEST_STATUS[value]?.label ?? value,
      },
    },
  ];
}

const COLUMNS = buildColumns();

export function MisPedidosDeAsistencia({ expandedRequestId }) {
  const { data, isLoading, isError, refetch } = useMisPedidosDeAsistencia();
  const { data: sections } = useMisComisionesParaAsistencia();

  return (
    <GlassCard className="p-2 sm:p-4">
      <DataTable
        columns={COLUMNS}
        data={data}
        getRowId={(request) => String(request.id)}
        isLoading={isLoading}
        isError={isError}
        onRetry={refetch}
        errorMessage="No pudimos cargar tus pedidos. Revisá tu conexión y probá de nuevo."
        emptyState={
          <EmptyState
            title="Todavía no pediste ningún cambio"
            description="Cuando pidas modificar una asistencia de un día anterior, vas a ver acá el estado y la respuesta de preceptoría."
          />
        }
        initialSorting={[{ id: 'createdAt', desc: true }]}
        initialExpanded={expandedRequestId ? { [String(expandedRequestId)]: true } : {}}
        renderExpanded={(row) => <RequestDetail request={row.original} sections={sections} />}
        expandLabel="Ver detalle del pedido"
        pageSize={PAGE_SIZE}
      />
    </GlassCard>
  );
}
