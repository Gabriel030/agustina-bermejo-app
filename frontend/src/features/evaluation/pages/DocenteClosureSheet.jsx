import { useCallback, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Notice } from '@/components/Notice';
import { LoadErrorState } from '@/components/LoadErrorState';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { formatearFecha } from '@/lib/comisiones';
import { useClosureSheet, useConfirmClosure } from '../hooks/useClosureSheet';
import { CLOSURE_STATUS, activeStudents, blockedMessage, columnLabel, toggleResultKpi, unpublishedColumns, withdrawnCount } from '../lib/closureSheet';
import { ClosurePrerequisitesSheet } from '../components/ClosurePrerequisitesSheet';
import { ClosureSheetTable } from '../components/ClosureSheetTable';
import { ClosureSummaryCards } from '../components/ClosureSummaryCards';

function ConfirmBar({ sheet, onConfirm, pending }) {
  const [open, setOpen] = useState(false);
  const reason = blockedMessage(sheet);
  return (
    <GlassCard className="flex flex-wrap items-center justify-between gap-3 p-4">
      <div className="min-w-0 flex-1 basis-64">
        <h2 className="font-heading text-size-15 font-semibold text-ink">Confirmar el cierre</h2>
        <p id="closure-confirm-hint" className="mt-1 text-xs text-ink-soft">
          {reason ?? 'Confirmás que revisaste notas, asistencia y resultado sugerido. El Administrativo aprueba y emite el acta.'}
        </p>
      </div>
      <Button type="button" variant="gradient" disabled={!sheet.availability.canConfirm} aria-describedby="closure-confirm-hint" onClick={() => setOpen(true)}>
        Confirmar cierre
      </Button>
      <ConfirmActionDialog
        open={open}
        onOpenChange={setOpen}
        title="Confirmar el cierre"
        description={`Confirmás que revisaste notas, asistencia y resultado sugerido de los ${activeStudents(sheet).length} alumnos.`}
        confirmLabel="Confirmar cierre"
        confirmVariant="gradient"
        pending={pending}
        onConfirm={async () => {
          const done = await onConfirm();
          if (done) setOpen(false);
        }}
      />
    </GlassCard>
  );
}

export function DocenteClosureSheet() {
  const { sectionId } = useParams();
  const [searchParams] = useSearchParams();
  const kind = searchParams.get('type') === 'COURSE' ? 'COURSE' : 'PROGRAM';
  const { data: sheet, isLoading, isError, error, refetch } = useClosureSheet(sectionId, kind);
  const confirm = useConfirmClosure(sectionId, kind);
  const [columnFilters, setColumnFilters] = useState([]);
  const [prerequisitesFor, setPrerequisitesFor] = useState(null);
  const closePrerequisites = useCallback(() => setPrerequisitesFor(null), []);
  const back = `/docente/mis-comisiones/${sectionId}${kind === 'COURSE' ? '?type=COURSE' : ''}`;

  async function confirmClosure() {
    try {
      await confirm.mutateAsync();
      toast.success('Entregaste la planilla de cierre.');
      return true;
    } catch (err) {
      toast.error(describeError(err).message);
      return false;
    }
  }

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
        <LoadErrorState error={error} onRetry={refetch} resource="el cierre de la comisión" backTo={back} backLabel="la comisión" />
      </PageContainer>
    );
  }

  const status = CLOSURE_STATUS[sheet.status] ?? { label: sheet.status, tone: 'neutral', description: '' };
  const pendingColumns = unpublishedColumns(sheet);
  const pendingCount = sheet.summary.pending;
  const bajas = withdrawnCount(sheet);
  const showDraftBanner = pendingColumns.length > 0 && sheet.status !== 'CLOSED';

  return (
    <PageContainer>
      <div className="flex flex-col gap-2">
        <Button asChild variant="ghost" className="w-fit px-0">
          <Link to={back}>
            <ArrowLeft className="size-4" /> Volver a la comisión
          </Link>
        </Button>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="font-heading text-2xl font-bold text-ink">Cierre de comisión</h1>
          <StatusPill tone={status.tone}>{status.label}</StatusPill>
        </div>
        <p className="text-sm text-ink-soft">
          {[sheet.subjectName, sheet.sectionName, sheet.programName, sheet.termLabel].filter(Boolean).join(' · ')}
          {sheet.termEndDate ? ` (cierra ${formatearFecha(sheet.termEndDate)})` : ''}
        </p>
        <p className="text-xs text-ink-faint">{status.description}</p>
      </div>

      {showDraftBanner && (
        <Notice tone="warning" title={`Falta publicar: ${pendingColumns.map(columnLabel).join(', ')}.`}>
          Hasta publicarlo, {pendingCount} {pendingCount === 1 ? 'alumno queda Pendiente' : 'alumnos quedan Pendientes'} y no se puede confirmar el cierre.
        </Notice>
      )}

      <div className="flex flex-col gap-2">
        <p className="text-xs text-ink-faint">
          Resumen sobre los {activeStudents(sheet).length} alumnos activos de la comisión ({bajas} {bajas === 1 ? 'baja no cuenta' : 'bajas no cuentan'}). Tocá una tarjeta para filtrar la planilla.
        </p>
        <ClosureSummaryCards
          summary={sheet.summary}
          columnFilters={columnFilters}
          onToggle={(key) => setColumnFilters((current) => toggleResultKpi(current, key))}
        />
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
        />
        <p className="text-xs text-ink-faint md:hidden">Deslizá para ver más columnas. El nombre queda fijo. El guion (—) en una nota significa sin publicar o sin recuperatorio.</p>
        <p className="text-xs text-ink-faint">
          Pagos y legajo se muestran solo como Sí o No. Para regularizar, el alumno consulta en Administración. Nunca se muestran importes.
        </p>
      </GlassCard>

      {sheet.status === 'DRAFT_AVAILABLE' || sheet.status === 'REOPENED' ? (
        <ConfirmBar sheet={sheet} onConfirm={confirmClosure} pending={confirm.isPending} />
      ) : null}

      <ClosurePrerequisitesSheet
        closureId={sheet.closureId}
        student={prerequisitesFor}
        subjectName={sheet.subjectName}
        onClose={closePrerequisites}
      />
    </PageContainer>
  );
}
