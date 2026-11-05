import { useState } from 'react';
import { Lock, WalletCards } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { StatusPill } from '@/components/StatusPill';
import { DataTable } from '@/components/table/DataTable';
import { useAuth } from '@/context/AuthContext';
import { formatDate, formatMoney, invoiceTitle } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';
import { useStudentPayments } from '../hooks/usePaymentReversals';
import { refundMethodLabel } from '../refunds/refundRequests';
import { PaymentActionsMenu } from './PaymentActionsMenu';
import { ReversalDialog } from './ReversalDialog';
import { PAYMENT_STATUS } from './paymentReversals';

export const STUDENT_PAYMENTS_ANCHOR = 'student-payments';

function PaymentStatusCell({ payment }) {
  const status = PAYMENT_STATUS[payment.status] ?? { label: payment.status, tone: 'neutral' };
  const pendingCount = payment.pendingRefunds?.count ?? 0;
  const chargeback = payment.chargeback;
  return (
    <div className="flex flex-col items-start gap-1">
      <StatusPill tone={status.tone}>{status.label}</StatusPill>
      {pendingCount > 0 && (
        <span className="text-xs text-ink-faint">
          Reembolso en curso ({pendingCount}) · {formatMoney(payment.pendingRefunds.amount)}
        </span>
      )}
      {payment.status === 'APPROVED' && moneyValue(payment.refundedAmount) > 0 && (
        <span className="text-xs text-ink-faint">Con devolución parcial</span>
      )}
      {chargeback && !chargeback.reversedOn && (
        <span className="text-xs text-ink-faint">
          Contracargo {chargeback.reference} · {formatDate(chargeback.chargedBackOn)}
        </span>
      )}
      {chargeback?.reversedOn && (
        <span className="text-xs text-ink-faint">Contracargo revertido el {formatDate(chargeback.reversedOn)}</span>
      )}
    </div>
  );
}

function buildColumns({ isDirector, onSelect }) {
  return [
    {
      id: 'paidOn',
      header: 'Fecha',
      accessorFn: (payment) => payment.paidOn,
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span className="font-medium text-ink">{formatDate(row.original.paidOn)}</span>
          <span className="text-xs text-ink-faint">Pago #{row.original.id}</span>
        </div>
      ),
    },
    {
      id: 'method',
      header: 'Método',
      accessorFn: (payment) => payment.methodType,
      enableSorting: false,
      cell: ({ row }) => <span className="text-ink-soft">{refundMethodLabel(row.original.methodType)}</span>,
    },
    {
      id: 'amount',
      header: 'Monto',
      accessorFn: (payment) => moneyValue(payment.amount),
      cell: ({ row }) => (
        <div className="flex flex-col items-end">
          <span className="font-semibold tabular-nums text-ink">{formatMoney(row.original.amount)}</span>
          {moneyValue(row.original.refundedAmount) > 0 && (
            <span className="text-xs tabular-nums text-ink-faint">Devuelto {formatMoney(row.original.refundedAmount)}</span>
          )}
        </div>
      ),
      meta: { align: 'right' },
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (payment) => payment.status,
      enableSorting: false,
      cell: ({ row }) => <PaymentStatusCell payment={row.original} />,
      meta: { filterable: true, filterTitle: 'estado', filterLabel: (key) => PAYMENT_STATUS[key]?.label ?? key },
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">Acciones</span>,
      enableSorting: false,
      cell: ({ row }) => <PaymentActionsMenu payment={row.original} isDirector={isDirector} onSelect={(kind) => onSelect(kind, row.original)} />,
      meta: { align: 'right', headerClassName: 'w-14' },
    },
  ];
}

export function StudentPaymentsCard({ studentId, invoices = [] }) {
  const { usuario } = useAuth();
  const isDirector = usuario?.roles?.includes('DIRECTOR') ?? false;
  const { data, isLoading, isError, refetch } = useStudentPayments(studentId);
  const [selection, setSelection] = useState(null);

  const selectedPayment = selection ? data?.find((payment) => payment.id === selection.paymentId) : null;

  function invoiceLabel(invoiceId) {
    const invoice = invoices.find((candidate) => candidate.id === invoiceId);
    return invoice ? invoiceTitle(invoice) : `Cuota #${invoiceId}`;
  }

  return (
    <section id={STUDENT_PAYMENTS_ANCHOR} aria-labelledby="student-payments-heading">
      <GlassCard className="overflow-hidden">
        <div className="flex flex-col gap-0.5 px-5 pt-5 pb-3">
          <h2 id="student-payments-heading" className="font-heading text-base font-semibold text-ink">
            Pagos del alumno
          </h2>
          <p className="text-xs text-ink-faint">Contracargos y revocaciones se hacen desde acá. Las cuotas se actualizan solas.</p>
        </div>
        <DataTable
          columns={buildColumns({ isDirector, onSelect: (kind, payment) => setSelection({ kind, paymentId: payment.id }) })}
          data={data}
          getRowId={(payment) => String(payment.id)}
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
          errorMessage="No pudimos cargar los pagos."
          initialSorting={[{ id: 'paidOn', desc: true }]}
          minWidth={640}
          emptyState={
            <EmptyState
              icon={<WalletCards className="mx-auto size-6 text-ink-faint" />}
              title="Sin pagos registrados"
              description="Cuando el alumno pague, lo ves acá con su estado."
            />
          }
        />
        {!isDirector && (
          <p className="flex items-center gap-2 px-5 py-3 text-xs text-ink-faint">
            <Lock className="size-3.5" aria-hidden="true" />
            La revocación de aprobaciones la hace el director.
          </p>
        )}
      </GlassCard>

      {selection && selectedPayment && (
        <ReversalDialog
          key={`${selection.kind}-${selection.paymentId}`}
          kind={selection.kind}
          payment={selectedPayment}
          studentId={studentId}
          invoiceLabel={invoiceLabel}
          onClose={() => setSelection(null)}
        />
      )}
    </section>
  );
}
