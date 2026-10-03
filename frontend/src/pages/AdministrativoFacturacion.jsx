import { useState } from 'react';
import { useEffect } from 'react';
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

const FEE_TYPE_LABELS = {
  RECURRING_FEE: 'Cuota mensual',
  ENROLLMENT_FEE: 'Matrícula',
  SUBSCRIPTION_FEE: 'Cuota anual',
  EXAM_RIGHT: 'Derecho a examen',
  RETAKEN_SUBJECT_FEE: 'Recurse',
  DEGREE_ISSUANCE: 'Emisión de título',
  ADMINISTRATIVE_FEE: 'Gestión administrativa',
};

const INVOICE_STATUS_CONFIG = {
  PENDING: { label: 'Pendiente', tone: 'warning' },
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

function RegistrarPagoModal({ invoices, enrollmentName, onClose }) {
  const [amounts, setAmounts] = useState(() =>
    Object.fromEntries(
      invoices
        .filter((inv) => inv.status === 'PENDING' || inv.status === 'OVERDUE' || inv.status === 'PARTIALLY_PAID')
        .map((inv) => [inv.id, inv.remainingBalance?.toString() ?? '0'])
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
    const allocations = payableInvoices
      .map((inv) => ({ invoiceId: inv.id, amountApplied: parseFloat(amounts[inv.id] ?? '0') }))
      .filter((a) => a.amountApplied > 0);

    if (!allocations.length) {
      setError('Ingresá al menos un monto mayor a cero.');
      return;
    }

    mutate(
      { allocations, method, notes: notes || undefined, externalReference: externalRef || undefined },
      {
        onSuccess: () => onClose(true),
        onError: (e) => setError(e?.message ?? 'Error al registrar el pago.'),
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
                        step="0.01"
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
              <span className="font-mono font-semibold text-ink">{formatMonto(total)}</span>
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

function EnrollmentCard({ enrollment }) {
  const [expanded, setExpanded] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');

  const standingInfo = BILLING_STANDING_CONFIG[enrollment.billingStanding] ?? {
    label: enrollment.billingStanding,
    tone: 'neutral',
  };

  const invoices = enrollment.invoices ?? [];
  const hasPayable = invoices.some(
    (inv) => inv.status === 'PENDING' || inv.status === 'OVERDUE' || inv.status === 'PARTIALLY_PAID'
  );

  const productLabel = `${enrollment.enrollmentType === 'COURSE' ? 'Curso' : 'Carrera'}: ${enrollment.productName ?? enrollment.enrollmentId}`;

  function handleModalClose(paid) {
    setShowModal(false);
    if (paid) setSuccessMsg('Pago registrado correctamente.');
  }

  return (
    <div className="rounded-2xl border border-border bg-surface">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="flex w-full items-center gap-4 p-4 text-left"
      >
        <div className="flex-1 min-w-0">
          <p className="truncate font-semibold text-ink">{productLabel}</p>
          {enrollment.billingStandingUpdatedAt && (
            <p className="text-xs text-ink-faint">
              Desde {formatFecha(enrollment.billingStandingUpdatedAt)}
            </p>
          )}
        </div>
        <StatusPill tone={standingInfo.tone}>{standingInfo.label}</StatusPill>
        {expanded ? <ChevronUp size={16} className="shrink-0 text-ink-faint" /> : <ChevronDown size={16} className="shrink-0 text-ink-faint" />}
      </button>

      {expanded && (
        <div className="border-t border-border px-4 pb-4 pt-3">
          {successMsg && (
            <p className="mb-3 rounded-lg bg-success-bg px-3 py-2 text-sm text-success">{successMsg}</p>
          )}

          {invoices.length === 0 ? (
            <p className="py-4 text-center text-sm text-ink-faint">Sin cuotas generadas todavía.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Concepto</TableHead>
                  <TableHead>Período</TableHead>
                  <TableHead>Vencimiento</TableHead>
                  <TableHead className="text-right">Saldo</TableHead>
                  <TableHead>Estado</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {invoices.map((inv) => {
                  const statusInfo = INVOICE_STATUS_CONFIG[inv.status] ?? { label: inv.status, tone: 'neutral' };
                  return (
                    <TableRow key={inv.id}>
                      <TableCell className="font-medium">{FEE_TYPE_LABELS[inv.feeType] ?? inv.feeType}</TableCell>
                      <TableCell className="text-ink-faint">{formatPeriod(inv.period)}</TableCell>
                      <TableCell className="text-ink-faint">{formatFecha(inv.dueDate)}</TableCell>
                      <TableCell className="text-right font-mono font-semibold tabular-nums">
                        {formatMonto(inv.remainingBalance)}
                      </TableCell>
                      <TableCell>
                        <StatusPill tone={statusInfo.tone}>{statusInfo.label}</StatusPill>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
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
              <EnrollmentCard key={`${enrollment.enrollmentType}-${enrollment.enrollmentId}`} enrollment={enrollment} />
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
            Alumno: <span className="font-medium text-ink">{receipt.studentName ?? `ID ${receipt.invoiceId}`}</span>
          </div>
          <div className="text-sm text-ink-faint">
            Comprobante subido el {formatFecha(receipt.uploadedAt)}
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
              <div className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-sm text-ink-faint">
                {receipt.invoiceFeeType && (
                  <span>{FEE_TYPE_LABELS[receipt.invoiceFeeType] ?? receipt.invoiceFeeType}</span>
                )}
                {receipt.invoicePeriod && <span>{formatPeriod(receipt.invoicePeriod)}</span>}
                <span>Subido {formatFecha(receipt.uploadedAt)}</span>
              </div>
              {receipt.filePath && (
                <a
                  href={receipt.filePath}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-1.5 inline-flex items-center gap-1 text-sm text-brand hover:underline"
                >
                  <Receipt size={13} />
                  Ver comprobante
                </a>
              )}
            </div>
            <div className="flex shrink-0 gap-2">
              <Button
                variant="outline"
                className="border-success text-success hover:bg-success-bg"
                onClick={() => {
                  setPendingApproveId(receipt.id);
                  aprobar(receipt.id, {
                    onSuccess: () => setPendingApproveId(null),
                    onError: () => setPendingApproveId(null),
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

export function AdministrativoFacturacion() {
  const [tab, setTab] = useState('facturacion');

  return (
    <PageContainer>
      <Tabs value={tab} onValueChange={setTab}>
        <TabsList variant="segmented" className="w-full">
          <TabsTrigger value="facturacion" className="flex-1">
            Facturación
          </TabsTrigger>
          <TabsTrigger value="comprobantes" className="flex-1">
            Comprobantes
          </TabsTrigger>
        </TabsList>

        <TabsContent value="facturacion" className="mt-5">
          <TabFacturacion />
        </TabsContent>

        <TabsContent value="comprobantes" className="mt-5">
          <TabComprobantes />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}
