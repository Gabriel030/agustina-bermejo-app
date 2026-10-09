import { Fragment, useState } from 'react';
import { useLocation, useMatch, useParams, useNavigate } from 'react-router-dom';
import { paymentOriginState } from '@/hooks/usePaymentReturn';
import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp, ArrowLeft } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { InvoiceAmountCell } from '@/components/billing/InvoiceAmountCell';
import { InvoiceDetail } from '@/components/billing/InvoiceDetail';
import { PaymentDateCell } from '@/components/billing/PaymentDateCell';
import { useExpandedRows } from '@/hooks/useExpandedRows';
import { useFacturasDeCurso, useFacturasDeMatricula, useMisComprobantes } from '@/hooks/useBilling';
import {
  formatDate,
  hasInvoiceDetail,
  invoiceDisplayStatus,
  invoicePrimaryPeriod,
  invoiceStatusInfo,
  invoiceTitle,
  isInvoicePayable,
} from '@/lib/billing';

const PAGE_SIZE = 10;

function latestReceiptByInvoice(receipts) {
  const latest = new Map();
  for (const receipt of receipts ?? []) {
    const current = latest.get(receipt.invoiceId);
    if (!current || receipt.uploadedAt > current.uploadedAt) {
      latest.set(receipt.invoiceId, receipt);
    }
  }
  return latest;
}

function InvoiceTable({ invoices, receiptsByInvoice }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const originState = paymentOriginState(pathname, 'Volver a las cuotas');
  const { isExpanded, toggle } = useExpandedRows();

  if (!invoices?.length) {
    return <EmptyState title="No hay cuotas para esta inscripción." className="py-12" />;
  }

  return (
    <Table className="min-w-[760px]">
      <TableHeader>
        <TableRow>
          <TableHead>Concepto</TableHead>
          <TableHead>Vencimiento</TableHead>
          <TableHead>Fecha de pago</TableHead>
          <TableHead className="text-right">Importe</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead />
        </TableRow>
      </TableHeader>
      <TableBody>
        {invoices.map((inv) => {
          const receipt = receiptsByInvoice.get(inv.id);
          const unpaid = isInvoicePayable(inv);
          const displayStatus = invoiceDisplayStatus(inv, receipt);
          const underReview = displayStatus === 'PAYMENT_UNDER_REVIEW';
          const rejectedReceipt = unpaid && receipt?.status === 'REJECTED' ? receipt : null;
          const statusInfo = invoiceStatusInfo(displayStatus);
          const pagable = unpaid && !underReview;
          const expandable = hasInvoiceDetail(inv);
          const open = expandable && isExpanded(inv.id);
          const concept = invoiceTitle(inv);
          return (
            <Fragment key={inv.id}>
              <TableRow
                className={expandable ? 'cursor-pointer' : undefined}
                onClick={expandable ? () => toggle(inv.id) : undefined}
              >
                <TableCell className="font-medium">{concept}</TableCell>
                <TableCell className="text-ink-faint">{formatDate(inv.dueDate)}</TableCell>
                <TableCell>
                  <PaymentDateCell invoice={inv} />
                </TableCell>
                <TableCell className="text-right">
                  <InvoiceAmountCell invoice={inv} />
                </TableCell>
                <TableCell>
                  <StatusPill tone={statusInfo.tone}>{statusInfo.label}</StatusPill>
                  {rejectedReceipt && (
                    <p className="mt-1 max-w-56 text-xs text-danger">
                      Comprobante rechazado: {rejectedReceipt.rejectionReason}
                    </p>
                  )}
                </TableCell>
                <TableCell>
                  <div className="flex items-center justify-end gap-2">
                    {pagable && (
                      <Button
                        size="sm"
                        variant="gradient"
                        onClick={(event) => {
                          event.stopPropagation();
                          navigate(`/alumno/pagos/${inv.id}/seleccion`, { state: originState });
                        }}
                      >
                        Pagar
                      </Button>
                    )}
                    {underReview && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={(event) => {
                          event.stopPropagation();
                          navigate(`/alumno/pagos/${inv.id}/transferencia`, { state: originState });
                        }}
                      >
                        Ver estado
                      </Button>
                    )}
                    {expandable && (
                      <button
                        type="button"
                        aria-expanded={open}
                        aria-controls={`invoice-detail-${inv.id}`}
                        aria-label={`Ver detalle de ${concept}`}
                        className="rounded-lg p-1 text-ink-faint"
                      >
                        {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                      </button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
              {open && (
                <TableRow id={`invoice-detail-${inv.id}`} className="hover:bg-transparent">
                  <TableCell colSpan={6} className="bg-neutral-bg/40 px-4">
                    <InvoiceDetail invoice={inv} />
                  </TableCell>
                </TableRow>
              )}
            </Fragment>
          );
        })}
      </TableBody>
    </Table>
  );
}

export function AlumnoPagoDetalle() {
  const { enrollmentId } = useParams();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);

  const isCurso = useMatch('/alumno/pagos/curso/:enrollmentId') != null;

  const cursoQuery = useFacturasDeCurso(isCurso ? enrollmentId : null);
  const carreraQuery = useFacturasDeMatricula(!isCurso ? enrollmentId : null);
  const { data, isLoading, isError } = isCurso ? cursoQuery : carreraQuery;
  const { data: receipts } = useMisComprobantes();
  const receiptsByInvoice = latestReceiptByInvoice(receipts);

  const enrollmentLabel = isCurso ? 'Curso' : 'Carrera';
  const enrollmentName = data?.productName ?? null;

  const sorted = [...(data?.invoices ?? [])].sort((a, b) => {
    const periodA = invoicePrimaryPeriod(a);
    const periodB = invoicePrimaryPeriod(b);
    if (!periodA) return 1;
    if (!periodB) return -1;
    if (periodB < periodA) return -1;
    if (periodB > periodA) return 1;
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

      {isError && (
        <GlassCard className="p-5">
          <EmptyState
            title="No pudimos cargar las cuotas de esta inscripción."
            description="Volvé a Mis pagos y probá de nuevo."
          />
        </GlassCard>
      )}

      {!isLoading && !isError && (
        <GlassCard className="p-5">
          <InvoiceTable invoices={paginated} receiptsByInvoice={receiptsByInvoice} />

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

    </PageContainer>
  );
}
