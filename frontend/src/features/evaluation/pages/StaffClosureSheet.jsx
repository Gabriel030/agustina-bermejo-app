import { useCallback, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Notice } from '@/components/Notice';
import { LoadErrorState } from '@/components/LoadErrorState';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { formatearFecha } from '@/lib/comisiones';
import {
  useApproveClosure,
  useExportClosure,
  useGrantClosureException,
  useClosureSheet,
  useReopenClosure,
  useRevokeClosureException,
} from '../hooks/useClosureSheet';
import { CLOSURE_STATUS, STAFF_STATUS_DESCRIPTION, activeStudents, columnLabel, toggleResultKpi, unpublishedColumns, withdrawnCount } from '../lib/closureSheet';
import { ClosureActionBar } from '../components/ClosureActionBar';
import { ClosureNoAbsenceLimitNotice } from '../components/ClosureNoAbsenceLimitNotice';
import { ClosureExportMenu } from '../components/ClosureExportMenu';
import { ClosurePrerequisitesSheet } from '../components/ClosurePrerequisitesSheet';
import { ClosureRowActions } from '../components/ClosureRowActions';
import { ClosureSheetTable } from '../components/ClosureSheetTable';
import { ClosureSummaryCards } from '../components/ClosureSummaryCards';

async function runWithToast(action, successMessage) {
  try {
    await action();
    toast.success(successMessage);
    return true;
  } catch (err) {
    toast.error(describeError(err).message);
    return false;
  }
}

function ReportLine({ sheet }) {
  if (sheet.status !== 'CLOSED' || !sheet.reportNumber) return null;
  return (
    <p className="text-sm font-semibold text-ink">
      Acta {sheet.reportNumber} · versión {sheet.reportVersion}
      {sheet.book ? ` · Libro ${sheet.book}` : ''}
      {sheet.folio ? ` · Folio ${sheet.folio}` : ''}
    </p>
  );
}

export function StaffClosureSheet({ role }) {
  const { sectionId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const kind = searchParams.get('type') === 'COURSE' ? 'COURSE' : 'PROGRAM';
  const { data: sheet, isLoading, isError, error, refetch } = useClosureSheet(sectionId, kind);
  const approve = useApproveClosure(sectionId, kind);
  const reopen = useReopenClosure(sectionId, kind);
  const grant = useGrantClosureException(sectionId, kind);
  const revoke = useRevokeClosureException(sectionId, kind);
  const exportClosure = useExportClosure(sheet?.closureId);
  const [columnFilters, setColumnFilters] = useState([]);
  const [prerequisitesFor, setPrerequisitesFor] = useState(null);
  const closePrerequisites = useCallback(() => setPrerequisitesFor(null), []);
  const goBack = useCallback(() => navigate(-1), [navigate]);
  const isDirector = role === 'director';
  const mutationPending = approve.isPending || reopen.isPending || grant.isPending || revoke.isPending;

  const renderRowActions = useCallback(
    (row) => (
      <ClosureRowActions
        row={row}
        sheet={sheet}
        pending={grant.isPending || revoke.isPending}
        onGrant={(target, reason) => runWithToast(() => grant.mutateAsync({ enrollmentId: target.enrollmentId, reason }), 'Registramos la excepción.')}
        onRevoke={(target) => runWithToast(() => revoke.mutateAsync({ enrollmentId: target.enrollmentId }), 'Quitamos la excepción.')}
      />
    ),
    [sheet, grant, revoke],
  );
  const rowActions = isDirector ? renderRowActions : null;

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-10" />
        <Skeleton className="h-64" />
      </PageContainer>
    );
  }

  if (isError) {
    return (
      <PageContainer>
        <LoadErrorState error={error} onRetry={refetch} resource="el cierre de la comisión" />
      </PageContainer>
    );
  }

  const status = CLOSURE_STATUS[sheet.status] ?? { label: sheet.status, tone: 'neutral' };
  const pendingColumns = unpublishedColumns(sheet);
  const pendingCount = sheet.summary.pending;
  const bajas = withdrawnCount(sheet);
  const showDraftBanner = pendingColumns.length > 0 && sheet.status !== 'CLOSED';

  return (
    <PageContainer>
      <div className="flex flex-col gap-2">
        <Button type="button" variant="ghost" className="w-fit px-0" onClick={goBack}>
          <ArrowLeft className="size-4" /> Volver
        </Button>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-heading text-2xl font-bold text-ink">Cierre de comisión</h1>
            <StatusPill tone={status.tone}>{status.label}</StatusPill>
          </div>
          <ClosureExportMenu
            closed={sheet.status === 'CLOSED'}
            pending={exportClosure.isPending}
            onExport={(format) => runWithToast(async () => exportClosure.mutateAsync(format), 'Exportamos el archivo.')}
          />
        </div>
        <p className="text-sm text-ink-soft">
          {[sheet.subjectName, sheet.sectionName, sheet.programName, sheet.termLabel].filter(Boolean).join(' · ')}
          {sheet.termEndDate ? ` (cierra ${formatearFecha(sheet.termEndDate)})` : ''}
        </p>
        <p className="text-xs text-ink-faint">{STAFF_STATUS_DESCRIPTION[sheet.status]}</p>
        <ReportLine sheet={sheet} />
      </div>

      {showDraftBanner && (
        <Notice tone="warning" title={`Falta publicar: ${pendingColumns.map(columnLabel).join(', ')}.`}>
          Hasta publicarlo, {pendingCount} {pendingCount === 1 ? 'alumno queda Pendiente' : 'alumnos quedan Pendientes'} y no se puede cerrar el acta.
        </Notice>
      )}

      {sheet.status !== 'CLOSED' && <ClosureNoAbsenceLimitNotice sheet={sheet} />}

      <div className="flex flex-col gap-2">
        <p className="text-xs text-ink-faint">
          Resumen sobre los {activeStudents(sheet).length} alumnos activos de la comisión ({bajas} {bajas === 1 ? 'baja no cuenta' : 'bajas no cuentan'}). Tocá una tarjeta para filtrar la planilla.
        </p>
        <ClosureSummaryCards summary={sheet.summary} columnFilters={columnFilters} onToggle={(key) => setColumnFilters((current) => toggleResultKpi(current, key))} />
      </div>

      <GlassCard className="flex flex-col gap-3 p-4">
        <h2 className="font-heading text-size-15 font-semibold text-ink">
          Alumnos <span className="font-normal text-ink-faint">{activeStudents(sheet).length}</span>
        </h2>
        <ClosureSheetTable
          sheet={sheet}
          columnFilters={columnFilters}
          onColumnFiltersChange={setColumnFilters}
          onOpenPrerequisites={setPrerequisitesFor}
          detail
          rowActions={rowActions}
        />
        <p className="text-xs text-ink-faint md:hidden">Deslizá para ver más columnas. El nombre queda fijo. El guion (—) en una nota significa sin publicar o sin recuperatorio.</p>
        <p className="text-xs text-ink-faint">Vista con detalle: el legajo muestra qué documento falta. Nunca se muestran importes: el detalle de pagos está en Facturación.</p>
      </GlassCard>

      <ClosureActionBar
        role={role}
        sheet={sheet}
        pending={mutationPending}
        onApprove={(body) =>
          runWithToast(
            () => approve.mutateAsync(body),
            'Cerraste el acta.',
          )
        }
        onReopen={(reason) => runWithToast(() => reopen.mutateAsync({ reason }), 'Reabriste el cierre.')}
      />

      <ClosurePrerequisitesSheet closureId={sheet.closureId} student={prerequisitesFor} subjectName={sheet.subjectName} onClose={closePrerequisites} />
    </PageContainer>
  );
}
