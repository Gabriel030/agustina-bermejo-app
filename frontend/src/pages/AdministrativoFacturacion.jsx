import { Fragment, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, CheckCircle, XCircle, Receipt, ChevronDown, ChevronUp, CreditCard } from 'lucide-react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { toast } from 'sonner';
import { apiFetchBlob } from '@/api/client';
import {
  FEE_TYPE_LABELS,
  formatDate,
  formatPeriod,
  hasInvoiceDetail,
  invoiceDisplayStatus,
  invoiceStatusInfo,
  invoiceTitle,
  isInvoicePayable,
} from '@/lib/billing';
import { InvoiceAmountCell } from '@/components/billing/InvoiceAmountCell';
import { InvoiceDetail, InvoiceLinesBreakdown } from '@/components/billing/InvoiceDetail';
import { PaymentDateCell } from '@/components/billing/PaymentDateCell';
import { useExpandedRows } from '@/hooks/useExpandedRows';
import { formatMoney, moneyValue } from '@/lib/utils';
import { useAlumnos } from '@/features/identity/hooks/useAlumnos';
import {
  useAdminBillingOverview,
  useRegistrarPago,
  useComprobantesAdmin,
  useAprobarComprobante,
  useRechazarComprobante,
} from '@/hooks/useBilling';

function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

const BILLING_STANDING_CONFIG = {
  UP_TO_DATE: { label: 'Al día', tone: 'success' },
  OVERDUE: { label: 'En mora', tone: 'danger' },
  SUSPENDED: { label: 'Suspendido', tone: 'danger' },
  WITHDRAWN_FOR_NONPAYMENT: { label: 'Dado de baja', tone: 'neutral' },
};

const PAYMENT_METHODS = [
  { value: 'CASH', label: 'Efectivo' },
  { value: 'TRANSFER', label: 'Transferencia bancaria' },
  { value: 'CARD', label: 'Tarjeta' },
  { value: 'MERCADO_PAGO', label: 'Mercado Pago' },
];

function formatMonto(money) {
  return formatMoney(money) || '-';
}

const REGISTER_PAYMENT_ERRORS = {
  PARTIAL_PAYMENT_NOT_ALLOWED: 'Las cuotas se cobran completas: el monto cambió. Actualizá la pantalla y volvé a intentar.',
  PAYMENT_EXCEEDS_INVOICE_BALANCE: 'El monto supera el saldo de la cuota. Actualizá la pantalla y revisá los saldos.',
  INVOICE_NOT_PAYABLE: 'Una de las cuotas ya no admite pagos. Actualizá la pantalla y revisá su estado.',
};

const APPROVE_RECEIPT_ERRORS = {
  INVOICE_NOT_PAYABLE:
    'La cuota de este comprobante ya está saldada (por ejemplo, se cobró en mostrador). Rechazalo indicando el motivo.',
  INVALID_PAYMENT_RECEIPT_STATE: 'Este comprobante ya fue revisado. Actualizá la bandeja.',
};

async function openReceiptFile(receiptId) {
  const preview = window.open('', '_blank');
  try {
    const { blob } = await apiFetchBlob(`/bank-transfer-receipts/${receiptId}/file`);
    const url = URL.createObjectURL(blob);
    if (preview) {
      preview.location.href = url;
    } else {
      window.location.assign(url);
    }
  } catch {
    preview?.close();
    toast.error('No pudimos abrir el comprobante. Probá de nuevo.');
  }
}

function nearestDueInvoiceId(invoices) {
  const sorted = [...invoices].sort((a, b) => String(a.dueDate ?? '').localeCompare(String(b.dueDate ?? '')));
  return sorted[0]?.id;
}

function PayableInvoiceRow({ invoice, checked, onToggle }) {
  const [showLines, setShowLines] = useState(false);
  const statusInfo = invoiceStatusInfo(invoiceDisplayStatus(invoice));
  const inputId = `register-payment-${invoice.id}`;
  const multiLine = (invoice.lines ?? []).length > 1;
  return (
    <div className="rounded-xl border border-border p-3">
      <label htmlFor={inputId} className="flex cursor-pointer items-center gap-3">
        <input
          id={inputId}
          type="checkbox"
          checked={checked}
          onChange={onToggle}
          aria-label={`Cobrar ${invoiceTitle(invoice)}`}
          className="h-4 w-4 accent-brand"
        />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-ink">{invoiceTitle(invoice)}</p>
          <p className="text-xs text-ink-faint">Vence el {formatDate(invoice.dueDate)}</p>
        </div>
        <StatusPill tone={statusInfo.tone}>{statusInfo.label}</StatusPill>
        <div className="flex w-28 flex-col items-end">
          <span className="font-mono text-sm font-semibold text-ink">{formatMonto(invoice.outstandingAmount)}</span>
          <span className="text-xs text-ink-faint">Saldo a cobrar</span>
        </div>
      </label>
      {multiLine && (
        <div className="mt-2 pl-7">
          <button
            type="button"
            aria-expanded={showLines}
            onClick={() => setShowLines((value) => !value)}
            className="text-xs font-semibold text-brand hover:underline"
          >
            {showLines ? 'Ocultar desglose' : 'Ver desglose'}
          </button>
          {showLines && (
            <div className="mt-2">
              <InvoiceLinesBreakdown invoice={invoice} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function RegistrarPagoModal({ invoices, enrollmentName, onClose }) {
  const underReviewInvoices = invoices.filter((inv) => invoiceDisplayStatus(inv) === 'PAYMENT_UNDER_REVIEW');
  const payableInvoices = invoices.filter((inv) => isInvoicePayable(inv) && invoiceDisplayStatus(inv) !== 'PAYMENT_UNDER_REVIEW');
  const [selectedIds, setSelectedIds] = useState(() => new Set([nearestDueInvoiceId(payableInvoices)]));
  const [method, setMethod] = useState('CASH');
  const [notes, setNotes] = useState('');
  const [externalRef, setExternalRef] = useState('');
  const [error, setError] = useState('');
  const { mutate, isPending } = useRegistrarPago();

  const selectedInvoices = payableInvoices.filter((inv) => selectedIds.has(inv.id));
  const total = selectedInvoices.reduce((sum, inv) => sum + moneyValue(inv.outstandingAmount), 0);
  const totalCurrency = (selectedInvoices[0] ?? payableInvoices[0])?.outstandingAmount?.currency ?? 'ARS';

  function toggleInvoice(id) {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function handleSubmit() {
    if (!selectedInvoices.length) {
      setError('Elegí al menos una cuota para cobrar.');
      return;
    }

    const allocations = selectedInvoices.map((inv) => ({ invoiceId: inv.id, amountApplied: inv.outstandingAmount }));

    mutate(
      { allocations, method, notes: notes || undefined, externalReference: externalRef || undefined },
      {
        onSuccess: () => onClose(true),
        onError: (e) => setError(REGISTER_PAYMENT_ERRORS[e?.code] ?? e?.message ?? 'Error al registrar el pago.'),
      }
    );
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(false); }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Registrar pago</DialogTitle>
          <p className="text-sm text-ink-faint">{enrollmentName}</p>
        </DialogHeader>

        {underReviewInvoices.length > 0 && (
          <div className="rounded-xl bg-warning-bg px-3 py-2 text-sm text-warning">
            <p className="font-semibold">En revisión — resolvelas en Comprobantes</p>
            <ul className="mt-1 text-xs">
              {underReviewInvoices.map((inv) => (
                <li key={inv.id}>
                  {invoiceTitle(inv)} · vence el {formatDate(inv.dueDate)}
                </li>
              ))}
            </ul>
          </div>
        )}

        {payableInvoices.length === 0 ? (
          <p className="py-6 text-center text-sm text-ink-faint">No hay cuotas para cobrar en esta inscripción.</p>
        ) : (
          <div className="flex flex-col gap-4">
            <p className="text-sm text-ink-soft">Las cuotas se cobran completas: no se admiten pagos parciales.</p>
            <div className="flex flex-col gap-3">
              {payableInvoices.map((inv) => (
                <PayableInvoiceRow
                  key={inv.id}
                  invoice={inv}
                  checked={selectedIds.has(inv.id)}
                  onToggle={() => toggleInvoice(inv.id)}
                />
              ))}
            </div>

            <div className="flex items-center justify-between rounded-xl bg-neutral-bg px-4 py-2">
              <span className="text-sm font-medium text-ink">Total</span>
              <span className="font-mono font-semibold text-ink">{formatMonto({ amount: total.toFixed(2), currency: totalCurrency })}</span>
            </div>

            <div className="flex flex-col gap-3">
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-faint">
                  Método de pago
                </label>
                <select
                  value={method}
                  onChange={(e) => setMethod(e.target.value)}
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                >
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m.value} value={m.value}>
                      {m.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-faint">
                  Referencia externa (opcional)
                </label>
                <input
                  type="text"
                  value={externalRef}
                  onChange={(e) => setExternalRef(e.target.value)}
                  placeholder="N° de transferencia, recibo, etc."
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-brand"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-faint">
                  Observaciones (opcional)
                </label>
                <textarea
                  rows={2}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  className="w-full resize-none rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-brand"
                />
              </div>
            </div>

            {error && <p className="text-sm text-danger">{error}</p>}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onClose(false)} disabled={isPending}>
            Cancelar
          </Button>
          <Button variant="gradient" onClick={handleSubmit} loading={isPending}>
            {isPending ? 'Registrando…' : 'Confirmar pago'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function InvoicesTable({ invoices }) {
  const { isExpanded, toggle } = useExpandedRows();

  return (
    <Table className="min-w-[760px]">
      <TableHeader>
        <TableRow>
          <TableHead>Concepto</TableHead>
          <TableHead>Vencimiento</TableHead>
          <TableHead>Fecha de pago</TableHead>
          <TableHead className="text-right">Importe</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead className="w-10" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {invoices.map((inv) => {
          const statusInfo = invoiceStatusInfo(invoiceDisplayStatus(inv));
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
                </TableCell>
                <TableCell>
                  {expandable && (
                    <button
                      type="button"
                      aria-expanded={open}
                      aria-controls={`invoice-detail-${inv.id}`}
                      aria-label={`Ver pagos de ${concept}`}
                      className="rounded-lg p-1 text-ink-faint"
                    >
                      {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </button>
                  )}
                </TableCell>
              </TableRow>
              {open && (
                <TableRow id={`invoice-detail-${inv.id}`} className="hover:bg-transparent">
                  <TableCell colSpan={6} className="bg-neutral-bg/40 px-4">
                    <InvoiceDetail invoice={inv} onViewReceipt={openReceiptFile} reviewLink showDocumentStatus />
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

function EnrollmentCard({ enrollment, defaultExpanded = false }) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const [showModal, setShowModal] = useState(false);

  const standingInfo = BILLING_STANDING_CONFIG[enrollment.paymentStatus] ?? {
    label: enrollment.paymentStatus,
    tone: 'neutral',
  };

  const invoices = enrollment.invoices ?? [];
  const hasPayable = invoices.some(isInvoicePayable);

  const productLabel = `${enrollment.enrollmentType === 'COURSE' ? 'Curso' : 'Carrera'}: ${enrollment.productName ?? enrollment.enrollmentId}`;

  function handleModalClose(paid) {
    setShowModal(false);
    if (paid) toast.success('Pago registrado correctamente.');
  }

  return (
    <div className="rounded-2xl border border-border bg-surface">
      <button
        type="button"
        aria-expanded={expanded}
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center gap-4 p-4 text-left"
      >
        <div className="flex-1 min-w-0">
          <p className="truncate font-semibold text-ink">{productLabel}</p>
          {enrollment.paymentStatusUpdatedAt && (
            <p className="text-xs text-ink-faint">
              Desde {formatDate(enrollment.paymentStatusUpdatedAt)}
            </p>
          )}
        </div>
        <StatusPill tone={standingInfo.tone}>{standingInfo.label}</StatusPill>
        {expanded ? <ChevronUp size={16} className="shrink-0 text-ink-faint" /> : <ChevronDown size={16} className="shrink-0 text-ink-faint" />}
      </button>

      {expanded && (
        <div className="border-t border-border px-4 pb-4 pt-3">
          {invoices.length === 0 ? (
            <p className="py-4 text-center text-sm text-ink-faint">Sin cuotas generadas todavía.</p>
          ) : (
            <InvoicesTable invoices={invoices} />
          )}

          {hasPayable && (
            <div className="mt-4 flex justify-end">
              <Button variant="gradient" onClick={() => setShowModal(true)}>
                <CreditCard size={14} className="mr-1.5" />
                Registrar pago
              </Button>
            </div>
          )}
        </div>
      )}

      {showModal && (
        <RegistrarPagoModal
          invoices={invoices}
          enrollmentName={productLabel}
          onClose={handleModalClose}
        />
      )}
    </div>
  );
}

function TabFacturacion() {
  const [searchInput, setSearchInput] = useState('');
  const searchDebounced = useDebounce(searchInput, 300);
  const [selectedStudent, setSelectedStudent] = useState(null);

  const { data: alumnos, isLoading: loadingAlumnos } = useAlumnos({ search: searchDebounced });
  const { data: overview, isLoading: loadingOverview } = useAdminBillingOverview(selectedStudent?.id);

  const showResults = searchDebounced.length >= 2;

  function handleSelect(alumno) {
    setSelectedStudent(alumno);
    setSearchInput('');
  }

  return (
    <div className="flex flex-col gap-5">
      {selectedStudent ? (
        <GlassCard className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-semibold text-ink">
                {selectedStudent.apellido}, {selectedStudent.nombre}
              </p>
              <p className="text-sm text-ink-faint">DNI {selectedStudent.dni}</p>
            </div>
            <Button variant="outline" onClick={() => setSelectedStudent(null)}>
              Cambiar alumno
            </Button>
          </div>
        </GlassCard>
      ) : (
        <GlassCard className="p-4">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
            <input
              type="text"
              placeholder="Buscar alumno por nombre o DNI…"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="w-full rounded-xl border border-border bg-surface py-2.5 pl-9 pr-3 text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-brand"
            />
          </div>

          {showResults && (
            <div className="mt-2 flex flex-col gap-1">
              {loadingAlumnos && <Skeleton className="h-10 w-full rounded-xl" />}
              {!loadingAlumnos && (!alumnos || alumnos.length === 0) && (
                <p className="px-3 py-2 text-sm text-ink-faint">Sin resultados.</p>
              )}
              {!loadingAlumnos &&
                alumnos?.map((a) => (
                  <button
                    key={a.id}
                    type="button"
                    onClick={() => handleSelect(a)}
                    className="flex items-center justify-between rounded-xl px-3 py-2.5 text-left text-sm hover:bg-neutral-bg"
                  >
                    <span className="font-medium text-ink">
                      {a.apellido}, {a.nombre}
                    </span>
                    <span className="text-ink-faint">DNI {a.dni}</span>
                  </button>
                ))}
            </div>
          )}
        </GlassCard>
      )}

      {selectedStudent && (
        <>
          {loadingOverview && (
            <div className="flex flex-col gap-3">
              <Skeleton className="h-16 w-full rounded-2xl" />
              <Skeleton className="h-16 w-full rounded-2xl" />
            </div>
          )}
          {!loadingOverview && (!overview || overview.length === 0) && (
            <EmptyState
              title="Sin inscripciones"
              description="Este alumno no tiene inscripciones a cursos ni carreras."
            />
          )}
          {!loadingOverview &&
            overview?.map((enrollment) => (
              <EnrollmentCard
                key={`${enrollment.enrollmentType}-${enrollment.enrollmentId}`}
                enrollment={enrollment}
                defaultExpanded={overview.length === 1}
              />
            ))}
        </>
      )}
    </div>
  );
}

function RechazarModal({ receipt, onClose }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const { mutate, isPending } = useRechazarComprobante();

  function handleSubmit() {
    if (!reason.trim()) {
      setError('El motivo es obligatorio.');
      return;
    }
    mutate(
      { id: receipt.id, rejectionReason: reason.trim() },
      {
        onSuccess: () => onClose(true),
        onError: (e) => setError(e?.message ?? 'Error al rechazar el comprobante.'),
      }
    );
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(false); }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Rechazar comprobante</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <div className="text-sm text-ink-faint">
            Alumno: <span className="font-medium text-ink">{receipt.studentName ?? 'Sin datos del alumno'}</span>
          </div>
          <div className="text-sm text-ink-faint">
            Comprobante subido el {formatDate(receipt.uploadedAt)}
          </div>
          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-ink-faint">
              Motivo del rechazo
            </label>
            <textarea
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Indicá por qué se rechaza el comprobante…"
              className="w-full resize-none rounded-xl border border-border bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-brand"
            />
          </div>
          {error && <p className="text-sm text-danger">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onClose(false)} disabled={isPending}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={handleSubmit} loading={isPending}>
            {isPending ? 'Rechazando…' : 'Rechazar'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function TabComprobantes() {
  const { data: comprobantes, isLoading } = useComprobantesAdmin();
  const { mutate: aprobar, isPending: aprobando } = useAprobarComprobante();
  const [pendingApproveId, setPendingApproveId] = useState(null);
  const [rechazarModal, setRechazarModal] = useState(null);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-3">
        {[1, 2, 3].map((i) => <Skeleton key={i} className="h-20 w-full rounded-2xl" />)}
      </div>
    );
  }

  if (!comprobantes || comprobantes.length === 0) {
    return (
      <EmptyState
        title="Sin comprobantes pendientes"
        description="Cuando un alumno suba un comprobante, aparecerá acá para revisión."
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {comprobantes.map((receipt) => (
        <GlassCard key={receipt.id} className="p-4">
          <div className="flex items-start gap-4">
            <div className="flex-1 min-w-0">
              <p className="font-semibold text-ink">{receipt.studentName ?? `Comprobante #${receipt.id}`}</p>
              {receipt.studentDni && (
                <p className="text-sm text-ink-faint">DNI {receipt.studentDni}</p>
              )}
              {receipt.productName && <p className="text-sm text-ink-soft">{receipt.productName}</p>}
              <div className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-sm text-ink-faint">
                {receipt.invoiceFeeType && (
                  <span>{FEE_TYPE_LABELS[receipt.invoiceFeeType] ?? receipt.invoiceFeeType}</span>
                )}
                {receipt.invoicePeriod && <span>{formatPeriod(receipt.invoicePeriod)}</span>}
                <span>Subido {formatDate(receipt.uploadedAt)}</span>
              </div>
              {receipt.filePath && (
                <button
                  type="button"
                  onClick={() => openReceiptFile(receipt.id)}
                  className="mt-1.5 inline-flex items-center gap-1 text-sm text-brand hover:underline"
                >
                  <Receipt size={13} />
                  Ver comprobante
                </button>
              )}
            </div>
            <div className="flex shrink-0 gap-2">
              <Button
                variant="outline"
                className="border-success text-success hover:bg-success-bg"
                onClick={() => {
                  setPendingApproveId(receipt.id);
                  aprobar(receipt.id, {
                    onSuccess: () => {
                      setPendingApproveId(null);
                      toast.success('Comprobante aprobado. El pago quedó acreditado.');
                    },
                    onError: (error) => {
                      setPendingApproveId(null);
                      toast.error(APPROVE_RECEIPT_ERRORS[error?.code] ?? error?.message ?? 'No pudimos aprobar el comprobante.');
                    },
                  });
                }}
                disabled={aprobando && pendingApproveId === receipt.id}
              >
                <CheckCircle size={14} className="mr-1" />
                Aprobar
              </Button>
              <Button
                variant="outline"
                className="border-danger text-danger hover:bg-danger-bg"
                onClick={() => setRechazarModal(receipt)}
              >
                <XCircle size={14} className="mr-1" />
                Rechazar
              </Button>
            </div>
          </div>
        </GlassCard>
      ))}

      {rechazarModal && (
        <RechazarModal receipt={rechazarModal} onClose={() => setRechazarModal(null)} />
      )}
    </div>
  );
}

const TAB_ACCOUNT = 'estado-de-cuenta';
const TAB_RECEIPTS = 'comprobantes';

export function AdministrativoFacturacion() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === TAB_RECEIPTS ? TAB_RECEIPTS : TAB_ACCOUNT;
  const { data: pendingReceipts } = useComprobantesAdmin();
  const pendingCount = pendingReceipts?.length ?? 0;

  function handleTabChange(value) {
    setSearchParams(value === TAB_ACCOUNT ? {} : { tab: value });
  }

  return (
    <PageContainer title="Facturación">
      <Tabs value={tab} onValueChange={handleTabChange}>
        <TabsList variant="segmented">
          <TabsTrigger value={TAB_ACCOUNT}>Estado de cuenta</TabsTrigger>
          <TabsTrigger value={TAB_RECEIPTS}>
            {pendingCount > 0 ? `Comprobantes · ${pendingCount}` : 'Comprobantes'}
          </TabsTrigger>
        </TabsList>

        <TabsContent value={TAB_ACCOUNT} className="mt-5">
          <TabFacturacion />
        </TabsContent>

        <TabsContent value={TAB_RECEIPTS} className="mt-5">
          <TabComprobantes />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
