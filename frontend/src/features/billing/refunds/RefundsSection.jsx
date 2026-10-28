import { useMemo, useState } from 'react';
import { Plus, Undo2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/table/DataTable';
import { formatDate, formatMoney } from '@/lib/billing';
import { useRefundEligibility, useStudentRefunds } from '../hooks/useRefunds';
import { CompleteRefundDialog } from './CompleteRefundDialog';
import { FailRefundDialog } from './FailRefundDialog';
import { RefundBlockAlert } from './RefundBlockAlert';
import { RefundPartsList } from './RefundPartsList';
import { describeRefundBlock, refundableNowCap, toCents } from './refundForm';
import { REFUND_REQUEST_STATUS, groupRefundsByRequest } from './refundRequests';

const columns = [
  {
    id: 'requestedOn',
    header: 'Pedido',
    accessorFn: (request) => request.requestedOn,
    cell: ({ row }) => (
      <div className="flex flex-col">
        <span className="font-medium text-ink">{formatDate(row.original.requestedOn)}</span>
        <span className="text-xs text-ink-faint">Pedido #{row.original.id}</span>
      </div>
    ),
  },
  {
    id: 'total',
    header: 'Monto',
    accessorFn: (request) => Number(request.total.amount),
    cell: ({ row }) => <span className="font-semibold tabular-nums text-ink">{formatMoney(row.original.total)}</span>,
    meta: { align: 'right' },
  },
  {
    id: 'split',
    header: 'Reparto',
    accessorFn: (request) => request.parts.length,
    enableSorting: false,
    cell: ({ row }) => {
      const count = row.original.parts.length;
      return <span className="text-ink-soft">{count} {count === 1 ? 'pago' : 'pagos'}</span>;
    },
  },
  {
    id: 'status',
    header: 'Estado',
    accessorFn: (request) => request.statusKey,
    enableSorting: false,
    cell: ({ row }) => <StatusPill tone={row.original.status.tone}>{row.original.status.label}</StatusPill>,
    meta: { filterable: true, filterTitle: 'estado', filterLabel: (key) => REFUND_REQUEST_STATUS[key]?.label ?? key },
  },
];

export function RefundsSection({ studentId }) {
  const { data, isLoading, isError, refetch } = useStudentRefunds(studentId);
  const { data: eligibility } = useRefundEligibility(studentId);
  const [dialog, setDialog] = useState(null);
  const block = eligibility ? describeRefundBlock(eligibility) : null;
  const requests = useMemo(() => (data ? groupRefundsByRequest(data) : []), [data]);

  return (
    <section className="flex flex-col gap-3" aria-labelledby="refunds-heading">
      {block && <RefundBlockAlert id="refund-block" block={block} />}
      {eligibility && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <StatCard label="Saldo a favor">
            <span className="text-2xl font-bold text-success">{formatMoney(eligibility.unallocatedCredit)}</span>
            {toCents(eligibility.reservedByPendingRefunds) > 0 && (
              <p className="text-xs font-normal text-ink-faint">
                Incluye {formatMoney(eligibility.reservedByPendingRefunds)} reservados en reembolsos sin transferir
              </p>
            )}
          </StatCard>
          <StatCard label="Reembolsable ahora">
            <span className="text-2xl font-bold text-ink">{formatMoney(refundableNowCap(eligibility))}</span>
          </StatCard>
        </div>
      )}
      <GlassCard className="overflow-hidden">
        <div className="flex items-center justify-between gap-3 px-5 pt-5 pb-3">
          <h2 id="refunds-heading" className="font-heading text-base font-semibold text-ink">
            Reembolsos
            {requests.length > 0 && (
              <span className="ml-2 text-xs font-normal text-ink-faint">
                {requests.length} {requests.length === 1 ? 'pedido' : 'pedidos'}
              </span>
            )}
          </h2>
          {eligibility && !block ? (
            <Button asChild variant="gradient" size="sm">
              <Link to="reembolsos/nuevo">
                <Plus aria-hidden="true" />
                Pedir reembolso
              </Link>
            </Button>
          ) : (
            <Button type="button" variant="outline" size="sm" disabled aria-describedby={block ? 'refund-block' : undefined}>
              <Plus aria-hidden="true" />
              Pedir reembolso
            </Button>
          )}
        </div>
        <DataTable
          columns={columns}
          data={requests}
          getRowId={(request) => String(request.id)}
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
          errorMessage="No pudimos cargar los reembolsos."
          initialSorting={[{ id: 'requestedOn', desc: true }]}
          expandLabel="Ver partes"
          minWidth={560}
          emptyState={
            <EmptyState
              icon={<Undo2 className="mx-auto size-6 text-ink-faint" />}
              title="Sin reembolsos"
              description="Cuando se pida uno, lo ves acá con el estado de cada pago."
            />
          }
          renderExpanded={(row) => (
            <RefundPartsList
              parts={row.original.parts}
              onComplete={(refund) => setDialog({ type: 'complete', refund })}
              onFail={(refund) => setDialog({ type: 'fail', refund })}
            />
          )}
        />
        <p className="px-5 py-3 text-xs text-ink-faint">Cada reembolso se hace por el mismo método del pago original.</p>
      </GlassCard>

      {dialog?.type === 'complete' && <CompleteRefundDialog refund={dialog.refund} onClose={() => setDialog(null)} />}
      {dialog?.type === 'fail' && <FailRefundDialog refund={dialog.refund} onClose={() => setDialog(null)} />}
    </section>
  );
}
