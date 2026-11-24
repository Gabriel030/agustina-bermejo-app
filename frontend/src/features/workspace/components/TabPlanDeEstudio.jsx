import { Fragment, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ListFilter } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { ProgressBar } from '@/components/ProgressBar';
import { Notice } from '@/components/Notice';
import { Button } from '@/components/ui/button';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell, TableCaption } from '@/components/ui/table';
import { AnioGroupHeaderRow } from '@/components/alumno/AnioGroupHeaderRow';
import { CurriculumSummaryBar } from '@/features/workspace/components/CurriculumSummaryBar';
import { CurriculumMap } from '@/features/workspace/components/CurriculumMap';
import { PlanViewSwitch } from '@/features/workspace/components/PlanViewSwitch';
import { PlanStatusFilter } from '@/features/workspace/components/PlanStatusFilter';
import { PlanStatusPill } from '@/features/workspace/components/PlanStatusPill';
import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';
import { PlanSubjectCard } from '@/features/workspace/components/PlanSubjectCard';
import { PlanBlockedHint } from '@/features/workspace/components/PlanBlockedHint';
import { ElectiveGroupsSummary } from '@/features/workspace/components/ElectiveGroupsSummary';
import { GraduationProjectionCard } from '@/features/workspace/components/GraduationProjectionCard';
import { useCurriculumProgressFor } from '@/features/enrollment';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import {
  buildPlanRows,
  describeCorequisite,
  filterPlanRows,
  formatEarnedCredits,
  normalizeStatusFilter,
  formatFinalScore,
} from '@/features/workspace/lib/curriculumPlan';

const COLUMN_COUNT = 5;

const WARNING_MESSAGES = {
  PLAN_WITHOUT_CREDITS: 'El plan no define créditos: el avance se calcula por materias aprobadas.',
  PLAN_CHANGED_AFTER_ENROLLMENT: {
    student: 'El plan de estudios cambió desde tu inscripción.',
    staff: 'El plan de estudios cambió desde la inscripción del alumno.',
  },
};

const EMPTY_PLAN_DESCRIPTION = {
  student: 'Cuando se carguen las vas a ver acá.',
  staff: 'Cuando se carguen van a aparecer acá.',
};

function describeWarning(warning, audience) {
  const message = WARNING_MESSAGES[warning.code];
  if (message && typeof message === 'object') return message[audience];
  return message ?? warning.message;
}

function PlanWarnings({ warnings, audience }) {
  return warnings.map((warning) => (
    <Notice key={warning.code} tone="warning">
      {describeWarning(warning, audience)}
    </Notice>
  ));
}

function PlanStatusCell({ row }) {
  return (
    <div className="flex flex-col items-start gap-0.5 py-1.5">
      <span className="inline-flex items-center gap-1">
        <PlanStatusPill status={row.estado} />
        {row.estado === 'bloqueada' && <PlanBlockedHint subjectName={row.subjectName} blockedBy={row.blockedBy} />}
      </span>
      {row.corequisites.map((corequisite) => (
        <span key={corequisite.subjectId} className="text-size-12 text-ink-faint">
          {describeCorequisite(corequisite)}
        </span>
      ))}
    </div>
  );
}

function PlanRowCells({ row }) {
  return (
    <>
      <TableCell className="font-semibold text-ink">
        <div className="truncate">{row.subjectName}</div>
        <div className="text-size-12 font-normal text-ink-faint">{row.subjectCode}</div>
      </TableCell>
      <TableCell className="text-right tabular-nums">
        {formatEarnedCredits(row)}
      </TableCell>
      <TableCell>
        <PlanStatusCell row={row} />
      </TableCell>
      <TableCell className="text-right tabular-nums">{formatFinalScore(row.finalScore)}</TableCell>
      <TableCell>
        {row.courseworkDone ? (
          <ProgressBar value={100} label="Cursada completa" tone="success" showValue={false} />
        ) : (
          <span className="text-ink-faint">—</span>
        )}
      </TableCell>
    </>
  );
}

export function TabPlanDeEstudio({ carreraId, audience = 'student', onViewEnrollment }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const [searchParams, setSearchParams] = useSearchParams();
  const statusFilter = normalizeStatusFilter(searchParams.get('estado'));
  const view = searchParams.get('vista') === 'mapa' ? 'mapa' : 'lista';
  const forbiddenDistinguished = view === 'mapa' || audience === 'staff';
  const { data: progress, isLoading, isError, error, refetch } = useCurriculumProgressFor(audience, carreraId);

  const rows = useMemo(() => buildPlanRows(progress?.subjects, progress?.summary.requiredCredits > 0), [progress]);
  const visibleRows = useMemo(() => filterPlanRows(rows, statusFilter), [rows, statusFilter]);

  function changeStatusFilter(next) {
    if (next === 'todas') searchParams.delete('estado');
    else searchParams.set('estado', next);
    setSearchParams(searchParams, { replace: true });
  }

  function changeView(next) {
    searchParams.set('vista', next);
    setSearchParams(searchParams, { replace: true });
  }

  const filterEmptyState = (
    <EmptyState
      title="Ninguna materia coincide"
      description="Probá con otro estado."
      action={
        <Button variant="outline" className="h-11" onClick={() => changeStatusFilter('todas')}>
          <ListFilter className="h-4 w-4" aria-hidden="true" />
          Quitar filtro
        </Button>
      }
    />
  );

  let previousYear;

  return (
    <div className="flex flex-1 flex-col gap-5">
      {view === 'mapa' && progress && (
        <CurriculumSummaryBar audience={audience} summary={progress.summary} />
      )}
      <GraduationProjectionCard audience={audience} carreraId={carreraId} collapsible={view === 'mapa'} />
      {progress && <PlanWarnings warnings={progress.warnings} audience={audience} />}
      <PlanViewSwitch value={view} onChange={changeView} />
      {view === 'lista' && (
        <ul aria-label="Referencia de estados" className="flex flex-wrap gap-1.5">
          {Object.keys(PLAN_STATUS_PILL).map((status) => (
            <li key={status}>
              <PlanStatusPill status={status} />
            </li>
          ))}
        </ul>
      )}
      {view === 'mapa' ? (
        isLoading ? (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Skeleton className="h-64" />
            <Skeleton className="h-64" />
            <Skeleton className="h-64" />
          </div>
        ) : isError ? (
          <LoadErrorState error={error} onRetry={refetch} resource="el plan de estudio" bare distinguishForbidden={forbiddenDistinguished} />
        ) : rows.length === 0 ? (
          <EmptyState title="El plan todavía no tiene materias" description={EMPTY_PLAN_DESCRIPTION[audience]} />
        ) : (
          <CurriculumMap
            subjects={progress.subjects}
            statusFilter={statusFilter}
            onStatusFilterChange={changeStatusFilter}
            creditsDefined={progress.summary.requiredCredits > 0}
            audience={audience}
            onViewEnrollment={onViewEnrollment}
          />
        )
      ) : compact ? (
        <div className="flex flex-col gap-2.5">
          <PlanStatusFilter value={statusFilter} onChange={changeStatusFilter} className="w-full" />
          {isLoading ? (
            <>
              <Skeleton className="h-24" />
              <Skeleton className="h-24" />
            </>
          ) : isError ? (
            <LoadErrorState error={error} onRetry={refetch} resource="el plan de estudio" bare distinguishForbidden={forbiddenDistinguished} />
          ) : rows.length === 0 ? (
            <EmptyState title="El plan todavía no tiene materias" description={EMPTY_PLAN_DESCRIPTION[audience]} />
          ) : visibleRows.length === 0 ? (
            filterEmptyState
          ) : (
            visibleRows.map((row) => {
              const newGroup = row.curricularYear !== previousYear;
              previousYear = row.curricularYear;
              return (
                <Fragment key={row.key}>
                  {newGroup && (
                    <h2 className="pt-1 text-size-11 font-bold tracking-wide text-ink-faint uppercase">
                      {row.curricularYear != null ? `${row.curricularYear}° año` : 'Sin año asignado'}
                    </h2>
                  )}
                  <PlanSubjectCard row={row} />
                </Fragment>
              );
            })
          )}
        </div>
      ) : (
        <GlassCard className="flex flex-col p-1">
          <div className="overflow-x-auto">
            <TooltipProvider>
              <Table className="table-fixed min-w-[720px]">
                <colgroup>
                  <col className="w-[30%]" />
                  <col className="w-[11%]" />
                  <col className="w-[30%]" />
                  <col className="w-[8%]" />
                  <col className="w-[21%]" />
                </colgroup>
                <TableCaption className="sr-only">Plan de estudio por año</TableCaption>
                <TableHeader>
                  <TableRow>
                    <TableHead>Materia</TableHead>
                    <TableHead className="text-right">Créditos</TableHead>
                    <TableHead>
                      <div className="flex items-center gap-2">
                        <span>Estado</span>
                        <PlanStatusFilter value={statusFilter} onChange={changeStatusFilter} className="h-11 border-none bg-transparent px-2 text-xs font-semibold text-ink-faint shadow-none hover:text-ink" />
                      </div>
                    </TableHead>
                    <TableHead className="text-right">Nota</TableHead>
                    <TableHead>Cursada</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {isLoading ? (
                    <TableRow>
                      <TableCell colSpan={COLUMN_COUNT} className="p-4">
                        <div className="flex flex-col gap-2">
                          <Skeleton className="h-10" />
                          <Skeleton className="h-10" />
                          <Skeleton className="h-10" />
                        </div>
                      </TableCell>
                    </TableRow>
                  ) : isError ? (
                    <TableRow>
                      <TableCell colSpan={COLUMN_COUNT}>
                        <LoadErrorState error={error} onRetry={refetch} resource="el plan de estudio" bare distinguishForbidden={forbiddenDistinguished} />
                      </TableCell>
                    </TableRow>
                  ) : rows.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={COLUMN_COUNT}>
                        <EmptyState title="El plan todavía no tiene materias" description={EMPTY_PLAN_DESCRIPTION[audience]} />
                      </TableCell>
                    </TableRow>
                  ) : visibleRows.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={COLUMN_COUNT}>{filterEmptyState}</TableCell>
                    </TableRow>
                  ) : (
                    visibleRows.map((row) => {
                      const newGroup = row.curricularYear !== previousYear;
                      previousYear = row.curricularYear;
                      return (
                        <Fragment key={row.key}>
                          {newGroup && <AnioGroupHeaderRow anio={row.curricularYear} colSpan={COLUMN_COUNT} />}
                          <TableRow>
                            <PlanRowCells row={row} />
                          </TableRow>
                        </Fragment>
                      );
                    })
                  )}
                </TableBody>
              </Table>
            </TooltipProvider>
          </div>
        </GlassCard>
      )}
      {progress && <ElectiveGroupsSummary groups={progress.summary.electiveGroups} />}
    </div>
  );
}
