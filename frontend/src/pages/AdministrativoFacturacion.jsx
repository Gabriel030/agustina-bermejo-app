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
import { FEE_TYPE_LABELS, formatDate, formatPeriod, hasInvoiceDetail, invoiceConcept } from '@/lib/billing';
import { InvoiceAmountCell } from '@/components/billing/InvoiceAmountCell';
import { InvoiceDetail } from '@/components/billing/InvoiceDetail';
import { PaymentDateCell } from '@/components/billing/PaymentDateCell';
import { useExpandedRows } from '@/hooks/useExpandedRows';
import { formatMoney, moneyValue } from '@/lib/utils';
import { useAlumnos } from '@/features/gestion-personas/hooks/useAlumnos';
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

const INVOICE_STATUS_CONFIG = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
  PENDING_REVIEW: { label: 'En revisión', tone: 'neutral' },
  OVERDUE: { label: 'Vencida', tone: 'danger' },
  PAID: { label: 'Pagada', tone: 'success' },
  PARTIALLY_PAID: { label: 'Pago parcial', tone: 'warning' },
  CANCELLED: { label: 'Anulada', tone: 'neutral' },
  WAIVED: { label: 'Bonificada', tone: 'neutral' },
};

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
  PAYMENT_EXCEEDS_INVOICE_BALANCE: 'El monto supera el saldo de la cuota. Actualizá la pantalla y revisá los saldos.',
  INVOICE_NOT_PAYABLE: 'Una de las cuotas ya no admite pagos. Actualizá la pantalla y revisá su estado.',
};

const APPROVE_RECEIPT_ERRORS = {
  INVOICE_NOT_PAYABLE:
    'La cuota de este comprobante ya está saldada (por ejemplo, se cobró en mostrador). Rechazalo indicando el motivo.',
  INVALID_PAYMENT_RECEIPT_STATE: 'Este comprobante ya fue revisado. Actualizá la bandeja.',
};

function paymentAmountsError(payableInvoices, amounts) {
  for (const inv of payableInvoices) {
    const amount = parseFloat(amounts[inv.id] ?? '0');
    if (isNaN(amount)) continue;
    if (amount < 0) {
      return 'Los montos no pueden ser negativos.';
    }
    if (inv.remainingBalance != null && amount > moneyValue(inv.remainingBalance)) {
      return `El monto de ${invoiceConcept(inv.feeType, inv.period)} supera su saldo (${formatMonto(inv.remainingBalance)}).`;
    }
  }
  return null;
}

async function openReceiptFile(receiptId) {
  const preview = window.open('', '_blank');
  try {
    const { blob } = await apiFetchBlob(`/payment-receipts/${receiptId}/file`);
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

function RegistrarPagoModal({ invoices, enrollmentName, onClose }) {
  const [amounts, setAmounts] = useState(() =>
    Object.fromEntries(
      invoices
        .filter((inv) => inv.status === 'PENDING' || inv.status === 'OVERDUE' || inv.status === 'PARTIALLY_PAID')
        .map((inv) => [inv.id, inv.remainingBalance?.amount ?? '0'])
    )
  );
  const [method, setMethod] = useState('CASH');
  const [notes, setNotes] = useState('');
  const [externalRef, setExternalRef] = useState('');
  const [error, setError] = useState('');
  const { mutate, isPending } = useRegistrarPago();

  const payableInvoices = invoices.filter(
    (inv) => inv.status === 'PENDING' || inv.status === 'OVERDUE' || inv.status === 'PARTIALLY_PAID'
  );

  const total = payableInvoices.reduce((sum, inv) => {
    const a = parseFloat(amounts[inv.id] ?? '0');
    return sum + (isNaN(a) ? 0 : a);
  }, 0);

  function handleSubmit() {
    const amountsError = paymentAmountsError(payableInvoices, amounts);
    if (amountsError) {
      setError(amountsError);
      return;
    }

    const allocations = payableInvoices
      .map((inv) => ({ invoiceId: inv.id, amount: parseFloat(amounts[inv.id] ?? '0'), currency: inv.remainingBalance.currency }))
      .filter((a) => a.amount > 0)
      .map((a) => ({ invoiceId: a.invoiceId, amountApplied: { amount: a.amount.toFixed(2), currency: a.currency } }));

    if (!allocations.length) {
      setError('Ingresá al menos un monto mayor a cero.');
      return;
    }

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

        {payableInvoices.length === 0 ? (
          <p className="py-6 text-center text-sm text-ink-faint">No hay cuotas pendientes en esta inscripción.</p>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-3">
              {payableInvoices.map((inv) => {
                const statusInfo = INVOICE_STATUS_CONFIG[inv.status] ?? { label: inv.status, tone: 'neutral' };
                return (
                  <div key={inv.id} className="flex items-center gap-3 rounded-xl border border-border p-3">
                    <div className="flex-1 min-w-0">
                      <p className="truncate text-sm font-medium text-ink">
                        {FEE_TYPE_LABELS[inv.feeType] ?? inv.feeType}
                        {inv.period ? ` · ${formatPeriod(inv.period)}` : ''}
                      </p>
                      <p className="text-xs text-ink-faint">Saldo: {formatMonto(inv.remainingBalance)}</p>
                    </div>
                    <StatusPill tone={statusInfo.tone}>{statusInfo.label}</StatusPill>
                    <div className="w-28">
                      <input
                        type="number"
                        min="0"
                        max={inv.remainingBalance == null ? undefined : moneyValue(inv.remainingBalance)}
                        step="0.01"
                        aria-label={`Monto a imputar a ${invoiceConcept(inv.feeType, inv.period)}`}
                        value={amounts[inv.id] ?? ''}
                        onChange={(e) => setAmounts((prev) => ({ ...prev, [inv.id]: e.target.value }))}
                        className="w-full rounded-lg border border-border bg-surface px-2 py-1.5 text-right text-sm font-mono text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                      />
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="flex items-center justify-between rounded-xl bg-neutral-bg px-4 py-2">
              <span className="text-sm font-medium text-ink">Total</span>
              <span className="font-mono font-semibold text-ink">{formatMonto({ amount: total.toFixed(2), currency: payableInvoices[0]?.remainingBalance?.currency ?? 'ARS' })}</span>
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
          <Button variant="gradient" onClick={handleSubmit} disabled={isPending}>
            {isPending ? 'Registrando…' : 'Confirmar pago'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

const PAYABLE_STATUSES = ['PENDING', 'OVERDUE', 'PARTIALLY_PAID'];

function displayedStatus(invoice) {
  const underReview =
    PAYABLE_STATUSES.includes(invoice.status) && (invoice.receipts ?? []).some((r) => r.status === 'PENDING_REVIEW');
  return underReview ? 'PENDING_REVIEW' : invoice.status;
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
          const status = displayedStatus(inv);
          const statusInfo = INVOICE_STATUS_CONFIG[status] ?? { label: status, tone: 'neutral' };
          const expandable = hasInvoiceDetail(inv);
          const open = expandable && isExpanded(inv.id);
          const concept = invoiceConcept(inv.feeType, inv.period);
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
                    <InvoiceDetail invoice={inv} onViewReceipt={openReceiptFile} reviewLink />
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

  const standingInfo = BILLING_STANDING_CONFIG[enrollment.billingStanding] ?? {
    label: enrollment.billingStanding,
    tone: 'neutral',
  };

  const invoices = enrollment.invoices ?? [];
  const hasPayable = invoices.some((inv) => PAYABLE_STATUSES.includes(inv.status));

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
          {enrollment.billingStandingUpdatedAt && (
            <p className="text-xs text-ink-faint">
              Desde {formatDate(enrollment.billingStandingUpdatedAt)}
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
          <Button
            className="bg-danger text-white hover:bg-danger/90"
            onClick={handleSubmit}
            disabled={isPending}
          >
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
