import { useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, CircleCheck, TriangleAlert } from 'lucide-react';
import { toast } from 'sonner';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { ModalMotivo } from '@/components/ModalMotivo';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useApproveDraftInvoices, useDiscardDraftInvoices, useDraftInvoiceGroups } from '@/features/billing/hooks/useDraftInvoices';
import { useMonthlyRuns } from '@/features/billing/hooks/useMonthlyRuns';
import { useMyModules } from '@/hooks/useMyModules';
import { newIdempotencyKey } from '@/api/idempotencyKey';
import { FEE_TYPE_LABELS, formatMoney } from '@/lib/billing';
import { isUnknownOutcome } from '../idempotency';
import { differenceLabel, draftErrorMessage, groupKey, pruneSelection, sharedInvoicesWarning, summarizeDraftSelection } from './draftSelection';
import { ENROLLMENT_TYPE_LABELS, formatPeriod, periodOf } from './monthlyRunLabels';
import { useStaffBasePath } from './useStaffBasePath';

const UNKNOWN_APPROVAL_MESSAGE = 'No pudimos confirmar si se emitieron. Volvé a confirmar: no se emiten dos veces.';
const UNKNOWN_DISCARD_MESSAGE = 'No pudimos confirmar el descarte. Actualizamos la lista para que lo verifiques.';
const MAX_REASON_LENGTH = 500;

function SharedInvoicesNotice({ summary }) {
  const warning = sharedInvoicesWarning(summary);
  if (!warning) return null;
  return (
    <div role="status" className="flex items-start gap-3 rounded-2xl border border-warning/40 bg-warning-bg px-4 py-3 text-sm text-ink">
      <TriangleAlert className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
      <span>{warning}</span>
    </div>
  );
}

function totalText(summary) {
  return summary.hasOutsideGroups ? `${formatMoney(summary.total)} o más` : formatMoney(summary.total);
}

function countText(count) {
  return count === 1 ? '1 factura' : `${count} facturas`;
}

export function DraftInvoicesPage() {
  const { runId } = useParams();
  const base = useStaffBasePath();
  const backPath = `${base}/emision-mensual`;
  const groupsQuery = useDraftInvoiceGroups(runId);
  const runs = useMonthlyRuns();
  const modules = useMyModules();
  const approve = useApproveDraftInvoices(runId);
  const discard = useDiscardDraftInvoices(runId);
  const [selected, setSelected] = useState(() => new Set());
  const [approveOpen, setApproveOpen] = useState(false);
  const [discardOpen, setDiscardOpen] = useState(false);
  const [approveError, setApproveError] = useState(null);
  const attempt = useRef(null);

  const groups = groupsQuery.data ?? [];
  const run = runs.data?.items?.find((candidate) => String(candidate.id) === String(runId));
  const canAct = modules.billingMode === 'FULL';
  const summary = summarizeDraftSelection(groups, selected);
  const allSelected = groups.length > 0 && groups.every((group) => selected.has(groupKey(group)));

  function toggleGroup(group) {
    const key = groupKey(group);
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function toggleAll() {
    setSelected(allSelected ? new Set() : new Set(groups.map(groupKey)));
  }

  async function refreshList() {
    const refreshed = await groupsQuery.refetch();
    setSelected((current) => pruneSelection(refreshed.data ?? [], current));
  }

  async function handleApprove() {
    setApproveError(null);
    const signature = JSON.stringify([...summary.invoiceIds].sort((left, right) => left - right));
    if (attempt.current?.signature !== signature) attempt.current = { signature, key: newIdempotencyKey() };
    try {
      const result = await approve.mutateAsync({ invoiceIds: summary.invoiceIds, idempotencyKey: attempt.current.key });
      attempt.current = null;
      setApproveOpen(false);
      setSelected(new Set());
      toast.success(`Se emitieron ${countText(result.count)}.`);
      await refreshList();
    } catch (error) {
      if (isUnknownOutcome(error)) {
        setApproveError(UNKNOWN_APPROVAL_MESSAGE);
        return;
      }
      attempt.current = null;
      const message = draftErrorMessage(error, 'No pudimos emitir las facturas. Intentá de nuevo.');
      toast.error(message);
      if (error.code === 'IDEMPOTENCY_KEY_CONFLICT') {
        setApproveError(message);
        return;
      }
      setApproveOpen(false);
      await refreshList();
    }
  }

  async function handleDiscard(reason) {
    try {
      const result = await discard.mutateAsync({ invoiceIds: summary.invoiceIds, reason });
      setDiscardOpen(false);
      setSelected(new Set());
      toast.success(`Se descartaron ${countText(result.count)}.`);
      await refreshList();
      return true;
    } catch (error) {
      if (isUnknownOutcome(error)) {
        toast.error(UNKNOWN_DISCARD_MESSAGE);
        setDiscardOpen(false);
        await refreshList();
        return false;
      }
      toast.error(draftErrorMessage(error, 'No pudimos descartar los borradores. Intentá de nuevo.'));
      if (error.code === 'DRAFT_NOT_PENDING' || error.code === 'MONTHLY_INVOICE_RUN_NOT_AWAITING_REVIEW') {
        setDiscardOpen(false);
        await refreshList();
      }
      return false;
    }
  }

  const title = run ? `Borradores de ${formatPeriod(periodOf(run))}` : 'Borradores';
  const hasSelection = summary.count > 0;

  return (
    <PageContainer
      title={title}
      description="Los alumnos no los ven hasta que se aprueben."
      action={
        <Button asChild variant="outline">
          <Link to={backPath}>
            <ArrowLeft aria-hidden="true" />
            Volver a Emisión mensual
          </Link>
        </Button>
      }
    >
      {groupsQuery.isLoading && <Skeleton className="h-40 w-full rounded-2xl" />}
      {groupsQuery.isError && (
        <LoadErrorState resource="los borradores" error={groupsQuery.error} onRetry={groupsQuery.refetch} backTo={backPath} backLabel="Emisión mensual" />
      )}
      {groupsQuery.isSuccess && groups.length === 0 && (
        <GlassCard>
          <EmptyState
            icon="✅"
            title="No quedan borradores por revisar"
            description="Las facturas aprobadas ya las ven los alumnos en su cuenta corriente."
            action={
              <Button asChild variant="outline">
                <Link to={backPath}>Volver a Emisión mensual</Link>
              </Button>
            }
          />
        </GlassCard>
      )}
      {groupsQuery.isSuccess && groups.length > 0 && (
        <>
          {modules.billingMode && !canAct && (
            <div role="status" className="rounded-2xl border border-warning/40 bg-warning-bg px-4 py-3 text-sm text-ink">
              Facturación está en modo espejo: podés ver los borradores, pero no aprobarlos ni descartarlos.
            </div>
          )}
          <GlassCard className="p-0">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-10 pl-4">
                      <Checkbox checked={allSelected} onCheckedChange={toggleAll} disabled={!canAct} aria-label="Seleccionar todos" />
                    </TableHead>
                    <TableHead>Carrera o curso</TableHead>
                    <TableHead>Tipo</TableHead>
                    <TableHead className="text-right">Cantidad</TableHead>
                    <TableHead className="text-right">Contra el mes anterior</TableHead>
                    <TableHead className="pr-4 text-right">Total</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {groups.map((group) => {
                    const difference = differenceLabel(group);
                    return (
                      <TableRow key={groupKey(group)} data-state={selected.has(groupKey(group)) ? 'selected' : undefined}>
                        <TableCell className="pl-4">
                          <Checkbox
                            checked={selected.has(groupKey(group))}
                            onCheckedChange={() => toggleGroup(group)}
                            disabled={!canAct}
                            aria-label={`Seleccionar ${group.name} · ${FEE_TYPE_LABELS[group.feeType] ?? group.feeType}`}
                          />
                        </TableCell>
                        <TableCell>
                          <div className="font-semibold text-ink">{group.name}</div>
                          <div className="text-xs text-ink-faint">{ENROLLMENT_TYPE_LABELS[group.enrollmentType] ?? group.enrollmentType}</div>
                        </TableCell>
                        <TableCell>{FEE_TYPE_LABELS[group.feeType] ?? group.feeType}</TableCell>
                        <TableCell className="text-right tabular-nums">{group.draftCount}</TableCell>
                        <TableCell className="text-right">
                          <StatusPill tone={difference.tone}>{difference.text}</StatusPill>
                        </TableCell>
                        <TableCell className="pr-4 text-right font-semibold tabular-nums">{formatMoney(group.draftTotal)}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          </GlassCard>
          <SharedInvoicesNotice summary={summary} />
          <GlassCard className="sticky bottom-4 flex flex-wrap items-center justify-between gap-3 p-4">
            <div className="flex flex-col">
              <span className="font-semibold text-ink">{hasSelection ? `${countText(summary.count)} seleccionadas` : 'Sin selección'}</span>
              <span className="text-sm text-ink-soft">
                {hasSelection ? `${totalText(summary)} · ${summary.groupCount} de ${groups.length} grupos` : 'Elegí uno o más grupos para aprobar o descartar.'}
              </span>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="ghost" disabled={!hasSelection} onClick={() => setSelected(new Set())}>
                Deseleccionar todo
              </Button>
              <Button type="button" variant="outline" disabled={!hasSelection || !canAct} onClick={() => setDiscardOpen(true)}>
                Descartar seleccionadas
              </Button>
              <Button
                type="button"
                variant="gradient"
                disabled={!hasSelection || !canAct}
                onClick={() => {
                  setApproveError(null);
                  setApproveOpen(true);
                }}
              >
                <CircleCheck aria-hidden="true" />
                Aprobar y emitir {hasSelection ? summary.count : ''}
              </Button>
            </div>
          </GlassCard>
        </>
      )}
      <ConfirmActionDialog
        open={approveOpen}
        onOpenChange={setApproveOpen}
        title={`¿Emitir ${countText(summary.count)}?`}
        description="Se emiten con la fecha de hoy y los alumnos las ven en su cuenta corriente. No se puede deshacer: una corrección posterior es una nota de crédito."
        confirmLabel="Emitir"
        confirmVariant="gradient"
        pending={approve.isPending}
        onConfirm={handleApprove}
      >
        <dl className="grid grid-cols-2 gap-3 rounded-xl bg-neutral-bg px-4 py-3 text-sm">
          <div className="flex flex-col gap-0.5">
            <dt className="text-xs text-ink-faint">Facturas</dt>
            <dd className="text-lg font-semibold tabular-nums text-ink">{summary.count}</dd>
          </div>
          <div className="flex flex-col gap-0.5">
            <dt className="text-xs text-ink-faint">Total</dt>
            <dd className="text-lg font-semibold tabular-nums text-ink">{totalText(summary)}</dd>
          </div>
        </dl>
        <SharedInvoicesNotice summary={summary} />
        {approveError && (
          <div role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger">
            {approveError}
          </div>
        )}
      </ConfirmActionDialog>
      <ModalMotivo
        open={discardOpen}
        onClose={() => setDiscardOpen(false)}
        onConfirmar={handleDiscard}
        confirmando={discard.isPending}
        requerido
        showCounter
        maxLength={MAX_REASON_LENGTH}
        titulo={`Descartar ${countText(summary.count)}`}
        descripcion={`No se emiten. ${sharedInvoicesWarning(summary) ?? ''} Contá por qué se descartan.`.replace('  ', ' ')}
        placeholder="Ej.: el precio de Radiología está mal cargado."
        confirmarLabel="Descartar"
        mensajeRequerido="El motivo del descarte es obligatorio."
      />
    </PageContainer>
  );
}
