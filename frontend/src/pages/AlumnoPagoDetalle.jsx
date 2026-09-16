import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, X, Receipt } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { useFacturasDeCurso, useFacturasDeMatricula } from '@/hooks/useBilling';

const PAGE_SIZE = 10;

const FEE_TYPE_LABELS = {
  RECURRING_FEE: 'Cuota mensual',
  ENROLLMENT_FEE: 'Matrícula',
  SUBSCRIPTION_FEE: 'Cuota anual',
  EXAM_RIGHT: 'Derecho a examen',
  RETAKEN_SUBJECT_FEE: 'Recurse',
  DEGREE_ISSUANCE: 'Emisión de título',
  ADMINISTRATIVE_FEE: 'Gestión administrativa',
};

const STATUS_CONFIG = {
  PENDING: { label: 'Pendiente de pago', tone: 'warning' },
  OVERDUE: { label: 'Vencida', tone: 'danger' },
  PAID: { label: 'Pagada', tone: 'success' },
  WAIVED: { label: 'Bonificada', tone: 'neutral' },
  CANCELLED: { label: 'Anulada', tone: 'neutral' },
};

const PAYMENT_METHOD_LABELS = {
  CASH: 'Efectivo',
  TRANSFER: 'Transferencia bancaria',
  CARD: 'Tarjeta',
  MERCADO_PAGO: 'Mercado Pago',
  CREDIT_BALANCE: 'Crédito',
};

function formatPeriod(period) {
  if (!period) return '-';
  const [year, month] = period.split('-');
  const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  return `${MESES[parseInt(month, 10) - 1]} ${year}`;
}

function formatMonto(amount) {
  if (amount == null) return '-';
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 0 }).format(amount);
}

function formatFecha(date) {
  if (!date) return '-';
  const d = new Date(date.includes('T') ? date : date + 'T00:00:00');
  return d.toLocaleDateString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function PaymentDetailModal({ invoice, onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center">
      <div className="w-full max-w-md rounded-2xl bg-surface p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-bold text-ink">Detalle del pago</h2>
          <button type="button" onClick={onClose} className="text-ink-faint hover:text-ink">
            <X size={20} />
          </button>
        </div>

        <div className="flex flex-col gap-3 text-sm">
          <div className="flex justify-between">
            <span className="text-ink-faint">Concepto</span>
            <span className="font-medium text-ink">{FEE_TYPE_LABELS[invoice.feeType] ?? invoice.feeType}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-ink-faint">Período</span>
            <span className="font-medium text-ink">{formatPeriod(invoice.period)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-ink-faint">Importe pagado</span>
            <span className="font-mono font-semibold text-ink">{formatMonto(invoice.amountDue)}</span>
          </div>
          <div className="my-1 border-t border-border" />
          <div className="flex justify-between">
            <span className="text-ink-faint">Fecha de pago</span>
            <span className="font-medium text-ink">{invoice.paymentDate ? formatFecha(invoice.paymentDate) : '-'}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-ink-faint">Método</span>
            <span className="font-medium text-ink">
              {invoice.paymentMethod ? (PAYMENT_METHOD_LABELS[invoice.paymentMethod] ?? invoice.paymentMethod) : '-'}
            </span>
          </div>
        </div>

        <button
          type="button"
          onClick={onClose}
          className="mt-6 w-full rounded-xl border border-border px-4 py-2.5 text-sm font-semibold text-ink transition-colors hover:border-brand hover:text-brand"
        >
          Cerrar
        </button>
      </div>
    </div>
  );
}

function InvoiceTable({ invoices, onDetalle }) {
  const navigate = useNavigate();

  if (!invoices?.length) {
    return (
      <div className="flex flex-col items-center gap-2 py-12 text-center">
        <Receipt size={28} className="text-ink-faint" />
        <p className="text-sm text-ink-faint">No hay cuotas para esta inscripción.</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-[11px] font-semibold uppercase tracking-wide text-ink-faint">
            <th className="pb-2.5 pr-4 pt-0">Concepto</th>
            <th className="pb-2.5 pr-4 pt-0">Período</th>
            <th className="pb-2.5 pr-4 pt-0">Vencimiento</th>
            <th className="pb-2.5 pr-4 pt-0">Fecha de pago</th>
            <th className="pb-2.5 pr-4 pt-0 text-right">Importe</th>
            <th className="pb-2.5 pr-4 pt-0">Estado</th>
            <th className="pb-2.5 pt-0" />
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {invoices.map((inv) => {
            const statusInfo = STATUS_CONFIG[inv.status] ?? { label: inv.status, tone: 'neutral' };
            const pagable = inv.status === 'PENDING' || inv.status === 'OVERDUE';
            const paid = inv.status === 'PAID';
            return (
              <tr key={inv.id} className="hover:bg-neutral-bg/40">
                <td className="py-3 pr-4 font-medium">{FEE_TYPE_LABELS[inv.feeType] ?? inv.feeType}</td>
                <td className="py-3 pr-4 text-ink-faint">{formatPeriod(inv.period)}</td>
                <td className="py-3 pr-4 text-ink-faint">{formatFecha(inv.dueDate)}</td>
                <td className="py-3 pr-4 text-ink-faint">
                  {inv.paymentDate ? formatFecha(inv.paymentDate) : <span className="text-border">—</span>}
                </td>
                <td className="py-3 pr-4 text-right font-mono font-semibold tabular-nums">
                  {formatMonto(inv.remainingBalance)}
                </td>
                <td className="py-3 pr-4">
                  <StatusPill tone={statusInfo.tone}>{statusInfo.label}</StatusPill>
                </td>
                <td className="py-3">
                  {pagable && (
                    <button
                      type="button"
                      onClick={() => navigate(`/alumno/pagos/${inv.id}/seleccion`)}
                      className="rounded-lg bg-gradient-to-r from-brand to-accent-2 px-3 py-1.5 text-[12px] font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
                    >
                      Pagar
                    </button>
                  )}
                  {paid && (
                    <button
                      type="button"
                      onClick={() => onDetalle(inv)}
                      className="rounded-lg border border-border px-3 py-1.5 text-[12px] font-semibold text-ink transition-colors hover:border-brand hover:text-brand"
                    >
                      Detalle
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function AlumnoPagoDetalle() {
  const { type, enrollmentId } = useParams();
  const [page, setPage] = useState(1);
  const [selectedInvoice, setSelectedInvoice] = useState(null);

  const isCurso = type === 'curso';

  const cursoQuery = useFacturasDeCurso(isCurso ? enrollmentId : null);
  const carreraQuery = useFacturasDeMatricula(!isCurso ? enrollmentId : null);
  const { data, isLoading } = isCurso ? cursoQuery : carreraQuery;

  const sorted = [...(data?.invoices ?? [])].sort((a, b) => {
    if (b.period < a.period) return -1;
    if (b.period > a.period) return 1;
    return 0;
  });

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const paginated = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <PageContainer>
      {isLoading && <Skeleton className="h-40 w-full rounded-2xl" />}

      {!isLoading && (
        <GlassCard className="p-5">
          <InvoiceTable
            invoices={paginated}
            onDetalle={(inv) => setSelectedInvoice(inv)}
          />

          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between border-t border-border pt-4 text-sm">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page === 1}
                className="flex items-center gap-1 rounded-lg px-3 py-1.5 text-ink-faint transition-colors hover:text-ink disabled:opacity-40"
              >
                <ChevronLeft size={15} />
                Anterior
              </button>
              <span className="text-ink-faint">
                {page} / {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={page === totalPages}
                className="flex items-center gap-1 rounded-lg px-3 py-1.5 text-ink-faint transition-colors hover:text-ink disabled:opacity-40"
              >
                Siguiente
                <ChevronRight size={15} />
              </button>
            </div>
          )}
        </GlassCard>
      )}

      {selectedInvoice && (
        <PaymentDetailModal invoice={selectedInvoice} onClose={() => setSelectedInvoice(null)} />
      )}
    </PageContainer>
  );
}
