import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, ArrowLeft } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
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
  PARTIALLY_PAID: { label: 'Pago parcial', tone: 'warning' },
  PENDING_REVIEW: { label: 'En revisión', tone: 'neutral' },
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
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Detalle del pago</DialogTitle>
        </DialogHeader>
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
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cerrar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function InvoiceTable({ invoices, onDetalle }) {
  const navigate = useNavigate();

  if (!invoices?.length) {
    return <EmptyState title="No hay cuotas para esta inscripción." className="py-12" />;
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Concepto</TableHead>
          <TableHead>Período</TableHead>
          <TableHead>Vencimiento</TableHead>
          <TableHead>Fecha de pago</TableHead>
          <TableHead className="text-right">Importe</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead />
        </TableRow>
      </TableHeader>
      <TableBody>
        {invoices.map((inv) => {
          const statusInfo = STATUS_CONFIG[inv.status] ?? { label: inv.status, tone: 'neutral' };
          const pagable = inv.status === 'PENDING' || inv.status === 'OVERDUE' || inv.status === 'PARTIALLY_PAID';
          const paid = inv.status === 'PAID';
          return (
            <TableRow key={inv.id}>
              <TableCell className="font-medium">{FEE_TYPE_LABELS[inv.feeType] ?? inv.feeType}</TableCell>
              <TableCell className="text-ink-faint">{formatPeriod(inv.period)}</TableCell>
              <TableCell className="text-ink-faint">{formatFecha(inv.dueDate)}</TableCell>
              <TableCell className="text-ink-faint">
                {inv.paymentDate ? formatFecha(inv.paymentDate) : <span className="text-border">—</span>}
              </TableCell>
              <TableCell className="text-right font-mono font-semibold tabular-nums">
                {formatMonto(inv.amountDue)}
              </TableCell>
              <TableCell>
                <StatusPill tone={statusInfo.tone}>{statusInfo.label}</StatusPill>
              </TableCell>
              <TableCell>
                {pagable && (
                  <Button size="sm" variant="gradient" onClick={() => navigate(`/alumno/pagos/${inv.id}/seleccion`)}>
                    Pagar
                  </Button>
                )}
                {paid && (
                  <Button size="sm" variant="outline" onClick={() => onDetalle(inv)}>
                    Detalle
                  </Button>
                )}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

export function AlumnoPagoDetalle() {
  const { type, enrollmentId } = useParams();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [selectedInvoice, setSelectedInvoice] = useState(null);

  const isCurso = type === 'curso';

  const cursoQuery = useFacturasDeCurso(isCurso ? enrollmentId : null);
  const carreraQuery = useFacturasDeMatricula(!isCurso ? enrollmentId : null);
  const { data, isLoading } = isCurso ? cursoQuery : carreraQuery;

  const enrollmentLabel = isCurso ? 'Curso' : 'Carrera';
  const enrollmentName = data?.enrollmentName ?? data?.name ?? null;

  const sorted = [...(data?.invoices ?? [])].sort((a, b) => {
    if (!a.period) return 1;
    if (!b.period) return -1;
    if (b.period < a.period) return -1;
    if (b.period > a.period) return 1;
    return 0;
  });

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const paginated = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <PageContainer>
      <button
        type="button"
        onClick={() => navigate('/alumno/pagos')}
        className="mb-4 flex items-center gap-1.5 text-sm text-ink-faint transition-colors hover:text-ink"
      >
        <ArrowLeft size={16} />
        Mis pagos
      </button>

      {enrollmentName && (
        <h1 className="mb-4 font-heading text-xl font-bold text-ink">
          {enrollmentLabel}: {enrollmentName}
        </h1>
      )}

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
