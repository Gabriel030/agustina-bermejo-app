import { Fragment, useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
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
  invoiceDisplayStatus,
  invoiceStatusInfo,
  invoiceTitle,
  isInvoicePayable,
} from '@/lib/billing';
import { sumMoney } from '@/features/billing/dashboard/dashboardMetrics';
import { CashPaymentDialog } from '@/features/billing/components/CashPaymentDialog';
import { STUDENT_PAYMENTS_ANCHOR, StudentPaymentsCard } from '@/features/billing/payment-reversals/StudentPaymentsCard';
import { CancelInvoiceDialog } from '@/features/billing/credit-notes/CancelInvoiceDialog';
import { InvoiceActions } from '@/features/billing/credit-notes/InvoiceActions';
import { PendingAuthorizationBanner } from '@/features/billing/credit-notes/PendingAuthorizationBanner';
import { StudentCreditNotesSection } from '@/features/billing/credit-notes/StudentCreditNotesSection';
import { pendingAuthorizationOf } from '@/features/billing/credit-notes/creditNotes';
import { useStudentCreditBalance } from '@/features/billing/hooks/useCreditNotes';
import { InvoiceAmountCell } from '@/components/billing/InvoiceAmountCell';
import { InvoiceDetail } from '@/components/billing/InvoiceDetail';
import { PaymentDateCell } from '@/components/billing/PaymentDateCell';
import { useExpandedRows } from '@/hooks/useExpandedRows';
import { formatMoney, moneyValue } from '@/lib/utils';
import { useAlumnos } from '@/features/identity/hooks/useAlumnos';
import { useLegajoAlumno } from '@/features/identity/hooks/useLegajoAlumno';
import {
  useAdminBillingOverview,
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

function formatMonto(money) {
  return formatMoney(money) || '-';
}

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

function scrollToStudentPayments() {
  document.getElementById(STUDENT_PAYMENTS_ANCHOR)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function InvoicesTable({ invoices, billing }) {
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
          const open = isExpanded(inv.id);
          const awaitingAuthorization = pendingAuthorizationOf(inv) != null;
          const concept = invoiceTitle(inv);
          return (
            <Fragment key={inv.id}>
              <TableRow className="cursor-pointer" onClick={() => toggle(inv.id)}>
                <TableCell className="font-medium">{concept}</TableCell>
                <TableCell className="text-ink-faint">{formatDate(inv.dueDate)}</TableCell>
                <TableCell>
                  <PaymentDateCell invoice={inv} />
                </TableCell>
                <TableCell className="text-right">
                  <InvoiceAmountCell invoice={inv} />
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap items-center gap-1.5">
                    <StatusPill tone={statusInfo.tone}>
                      {inv.paymentStatus === 'CANCELLED' && inv.invoiceType === 'LATE_FEE' ? 'Recargo anulado' : statusInfo.label}
                    </StatusPill>
                    {awaitingAuthorization && (
                      <StatusPill tone="warning" dot>
                        Autorización pendiente
                      </StatusPill>
                    )}
                  </div>
                </TableCell>
                <TableCell>
                  <button
                    type="button"
                    aria-expanded={open}
                    aria-controls={`invoice-detail-${inv.id}`}
                    aria-label={`Ver detalle de ${concept}`}
                    className="rounded-lg p-1 text-ink-faint"
                  >
                    {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                </TableCell>
              </TableRow>
              {open && (
                <TableRow id={`invoice-detail-${inv.id}`} className="hover:bg-transparent">
                  <TableCell colSpan={6} className="bg-neutral-bg/40 px-4">
                    <InvoiceDetail
                      invoice={inv}
                      onViewReceipt={openReceiptFile}
                      onViewPayments={scrollToStudentPayments}
                      reviewLink
                      showDocumentStatus
                      actions={
                        <InvoiceActions
                          invoice={inv}
                          invoices={billing.invoices}
                          studentId={billing.studentId}
                          studentName={billing.studentName}
                          onCancel={billing.onCancel}
                        />
                      }
                    />
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

function EnrollmentCard({ enrollment, billing, defaultExpanded = false }) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  const standingInfo = BILLING_STANDING_CONFIG[enrollment.paymentStatus] ?? {
    label: enrollment.paymentStatus,
    tone: 'neutral',
  };

  const invoices = enrollment.invoices ?? [];

  const productLabel = `${enrollment.enrollmentType === 'COURSE' ? 'Curso' : 'Carrera'}: ${enrollment.productName ?? enrollment.enrollmentId}`;

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
            <InvoicesTable invoices={invoices} billing={billing} />
          )}

        </div>
      )}
    </div>
  );
}

function payableInvoicesOf(overview) {
  return (overview ?? []).flatMap((enrollment) => enrollment.invoices ?? []).filter(
    (invoice) => isInvoicePayable(invoice) && invoiceDisplayStatus(invoice) !== 'PAYMENT_UNDER_REVIEW',
  );
}

function cashCurrencyOf(overview) {
  return payableInvoicesOf(overview)[0]?.outstandingAmount?.currency ?? 'ARS';
}

function outstandingDebtOf(overview) {
  const invoices = payableInvoicesOf(overview);
  if (invoices.length === 0) return null;
  const currencies = new Set(invoices.map((invoice) => invoice.outstandingAmount?.currency));
  if (currencies.size !== 1) return null;
  return sumMoney(invoices.map((invoice) => invoice.outstandingAmount));
}

function TabFacturacion() {
  const [searchInput, setSearchInput] = useState('');
  const searchDebounced = useDebounce(searchInput, 300);
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const studentId = searchParams.get('alumno');
  const [pickedStudent, setPickedStudent] = useState(null);
  const [showCashPayment, setShowCashPayment] = useState(false);
  const [cancelTarget, setCancelTarget] = useState(null);

  const { data: legajo, isError: legajoFailed } = useLegajoAlumno(studentId);
  const selectedStudent = legajo ?? (pickedStudent && String(pickedStudent.id) === studentId ? pickedStudent : null);

  const { data: alumnos, isLoading: loadingAlumnos } = useAlumnos({ search: searchDebounced });
  const {
    data: overview,
    isLoading: loadingOverview,
    isFetching: fetchingOverview,
    isError: overviewFailed,
    refetch: refetchOverview,
  } = useAdminBillingOverview(studentId);
  const { data: creditBalance } = useStudentCreditBalance(studentId);
  const debtIsCurrent = !fetchingOverview && !overviewFailed;

  const allInvoices = (overview ?? []).flatMap((enrollment) => enrollment.invoices ?? []);
  const showResults = searchDebounced.length >= 2;
  const debt = debtIsCurrent ? outstandingDebtOf(overview) : null;
  const availableCredit = creditBalance?.availableCredit;
  const studentName = selectedStudent ? `${selectedStudent.apellido}, ${selectedStudent.nombre}` : undefined;
  const requestedCancelId = location.state?.cancelInvoiceId;

  useEffect(() => {
    if (!requestedCancelId || !overview) return;
    const target = overview.flatMap((enrollment) => enrollment.invoices ?? []).find((invoice) => invoice.id === requestedCancelId);
    if (target) setCancelTarget(target);
    navigate(`${location.pathname}${location.search}`, { replace: true, state: null });
  }, [requestedCancelId, overview, location.pathname, location.search, navigate]);

  function selectStudent(id) {
    const next = new URLSearchParams(searchParams);
    if (id) next.set('alumno', String(id));
    else next.delete('alumno');
    setSearchParams(next);
  }

  function handleSelect(alumno) {
    setPickedStudent(alumno);
    selectStudent(alumno.id);
    setSearchInput('');
  }

  const billing = {
    invoices: allInvoices,
    studentId,
    studentName,
    onCancel: (invoice) => setCancelTarget(invoice),
  };

  return (
    <div className="flex flex-col gap-5">
      {selectedStudent ? (
        <GlassCard className="p-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <p className="font-semibold text-ink">
                {selectedStudent.apellido}, {selectedStudent.nombre}
              </p>
              <p className="text-sm text-ink-faint">DNI {selectedStudent.dni}</p>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:gap-4">
              {availableCredit && moneyValue(availableCredit) > 0 && (
                <div className="sm:text-right">
                  <p className="text-xs text-ink-faint">Saldo a favor</p>
                  <p className="text-lg font-semibold tabular-nums text-success">{formatMonto(availableCredit)}</p>
                </div>
              )}
              {debt && (
                <div className="sm:text-right">
                  <p className="text-xs text-ink-faint">Deuda</p>
                  <p className="text-lg font-semibold tabular-nums text-ink">{formatMonto(debt)}</p>
                </div>
              )}
              <div className="flex flex-col gap-2 sm:flex-row">
                <Button variant="gradient" className="w-full sm:w-auto" onClick={() => setShowCashPayment(true)} disabled={!debtIsCurrent}>
                  <CreditCard size={14} className="mr-1.5" />
                  Cobrar en efectivo
                </Button>
                <Button variant="outline" className="w-full sm:w-auto" onClick={() => selectStudent(null)}>
                  Cambiar alumno
                </Button>
              </div>
            </div>
          </div>
        </GlassCard>
      ) : studentId && !legajoFailed ? (
        <Skeleton className="h-20 w-full rounded-2xl" />
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

      {studentId && overviewFailed && (
        <div role="alert" className="flex flex-col gap-2 rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger sm:flex-row sm:items-center sm:justify-between">
          <span>No pudimos actualizar la deuda del alumno. Hasta recargarla no se puede cobrar.</span>
          <Button variant="outline" onClick={() => refetchOverview()}>
            Reintentar
          </Button>
        </div>
      )}

      {selectedStudent && showCashPayment && (
        <CashPaymentDialog
          student={selectedStudent}
          currency={cashCurrencyOf(overview)}
          totalDebt={debt}
          onClose={() => setShowCashPayment(false)}
          onStudentGone={() => {
            setShowCashPayment(false);
            selectStudent(null);
          }}
        />
      )}

      {studentId && (
        <>
          <PendingAuthorizationBanner invoices={allInvoices} studentName={studentName} />
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
                billing={billing}
                defaultExpanded={overview.length === 1}
              />
            ))}
          <StudentCreditNotesSection studentId={studentId} invoices={allInvoices} />
          <StudentPaymentsCard studentId={studentId} invoices={allInvoices} />
        </>
      )}

      {cancelTarget && (
        <CancelInvoiceDialog key={cancelTarget.id} invoice={cancelTarget} studentId={studentId} onClose={() => setCancelTarget(null)} />
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
    const next = new URLSearchParams();
    const student = searchParams.get('alumno');
    if (student) next.set('alumno', student);
    if (value !== TAB_ACCOUNT) next.set('tab', value);
    setSearchParams(next);
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
