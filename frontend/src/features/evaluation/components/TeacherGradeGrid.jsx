import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useSaveGradeCells, useTeacherGradeGrid } from '../hooks/useTeacherGradeGrid';
import { collectPendingJobs, hasInvalidDraft, patchDraft, schemeSummary, settleDraft, summarizeSave } from '../lib/gradeGrid';
import { GradeGridTable } from './GradeGridTable';
import { GradeStudentSheet } from './GradeStudentSheet';

const UNSAVED_MESSAGE = 'Tenés notas sin guardar. Si salís las perdés.';

function useUnsavedChangesGuard(active) {
  useEffect(() => {
    if (!active) return undefined;
    const beforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    const interceptLink = (event) => {
      const anchor = event.target.closest?.('a[href]');
      if (!anchor || anchor.target === '_blank' || !anchor.getAttribute('href').startsWith('/')) return;
      if (!window.confirm(UNSAVED_MESSAGE)) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    window.addEventListener('beforeunload', beforeUnload);
    document.addEventListener('click', interceptLink, true);
    return () => {
      window.removeEventListener('beforeunload', beforeUnload);
      document.removeEventListener('click', interceptLink, true);
    };
  }, [active]);
}

export function TeacherGradeGrid({ sectionId, kind, onDirtyChange }) {
  const { data: grid, isLoading, isError, refetch } = useTeacherGradeGrid(sectionId, kind);
  const saveGradeCells = useSaveGradeCells(sectionId, kind);
  const [drafts, setDrafts] = useState({});
  const [statuses, setStatuses] = useState({});
  const [openEnrollmentId, setOpenEnrollmentId] = useState(null);
  const clearTimer = useRef(null);

  const students = useMemo(() => grid?.students ?? [], [grid]);
  const columns = useMemo(() => grid?.columns ?? [], [grid]);
  const jobs = useMemo(
    () => collectPendingJobs({ students, columns, drafts, statuses }),
    [students, columns, drafts, statuses],
  );
  const invalid = hasInvalidDraft(drafts);
  const dirty = Object.keys(drafts).length > 0;

  useUnsavedChangesGuard(dirty);
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => () => clearTimeout(clearTimer.current), []);

  const changeDraft = useCallback((key, saved, patch) => {
    setDrafts((current) => patchDraft(current, key, saved, patch));
    setStatuses((current) => (current[key] ? { ...current, [key]: undefined } : current));
  }, []);
  const settle = useCallback((key) => setDrafts((current) => settleDraft(current, key)), []);

  async function runJobs(toSave) {
    if (!toSave.length) return;
    setStatuses((current) => ({ ...current, ...Object.fromEntries(toSave.map((job) => [job.key, 'saving'])) }));
    const result = await saveGradeCells(toSave, (key, status) => setStatuses((current) => ({ ...current, [key]: status })));
    const failed = new Set(result.failedKeys);
    setDrafts((current) => {
      const rest = { ...current };
      toSave.forEach((job) => {
        if (!failed.has(job.key)) delete rest[job.key];
      });
      return rest;
    });
    if (result.failed) toast.error(summarizeSave(result.saved, result.failed));
    else toast.success(summarizeSave(result.saved, 0));
    clearTimeout(clearTimer.current);
    clearTimer.current = setTimeout(
      () => setStatuses((current) => Object.fromEntries(Object.entries(current).filter(([, status]) => status !== 'saved'))),
      1500,
    );
  }

  function retryCell(key) {
    const pending = collectPendingJobs({ students, columns, drafts, statuses: { ...statuses, [key]: undefined } });
    return runJobs(pending.filter((job) => job.key === key));
  }

  if (isLoading) {
    return (
      <GlassCard className="flex flex-col gap-2 p-4" aria-busy="true" aria-label="Cargando notas">
        <Skeleton className="h-4 w-1/2" />
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-11 w-full" />
        ))}
      </GlassCard>
    );
  }

  if (isError) {
    return (
      <GlassCard className="p-4">
        <EmptyState
          icon="⚠️"
          title="No pudimos cargar las notas"
          description="Revisá tu conexión e intentá de nuevo."
          action={
            <Button variant="outline" onClick={() => refetch()}>
              Reintentar
            </Button>
          }
        />
      </GlassCard>
    );
  }

  if (!students.length) {
    return (
      <GlassCard className="p-4">
        <EmptyState icon="👥" title="Todavía no hay alumnos inscriptos" description="Cuando se inscriban alumnos a la comisión, vas a poder cargar sus notas acá." />
      </GlassCard>
    );
  }

  if (!columns.length) {
    return (
      <GlassCard className="p-4">
        <EmptyState
          icon="📋"
          title="Esta comisión todavía no tiene un esquema de evaluación"
          description="Sin esquema no hay parciales, trabajos prácticos ni final para calificar. Pedile a Dirección que lo asigne."
        />
      </GlassCard>
    );
  }

  const openStudent = students.find((student) => student.enrollmentId === openEnrollmentId) ?? null;
  const count = jobs.length;
  const summary = schemeSummary(grid);

  return (
    <GlassCard className="flex min-w-0 flex-col gap-3 p-3 md:p-4">
      <p className="text-[12.5px] leading-relaxed text-ink-soft">
        {grid.evaluationSchemeName ? `${grid.evaluationSchemeName}. ` : ''}
        {summary ? `${summary}. ` : ''}El final solo se carga a alumnos regulares.
      </p>
      <div className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-dialog-bg py-2">
        <span className="text-[12.5px] text-ink-soft" aria-live="polite">
          {dirty ? (
            <>
              <b className="text-ink">{Object.keys(drafts).length}</b> sin guardar
            </>
          ) : (
            'Todo guardado'
          )}
          {invalid && <span className="text-danger"> · hay notas fuera de 1 a 10</span>}
        </span>
        <Button variant="gradient" disabled={!count || invalid} onClick={() => runJobs(jobs)}>
          {count ? `Guardar ${count} ${count === 1 ? 'nota' : 'notas'}` : 'Guardar notas'}
        </Button>
      </div>
      <GradeGridTable
        students={students}
        columns={columns}
        drafts={drafts}
        statuses={statuses}
        onChange={changeDraft}
        onSettle={settle}
        onRetry={retryCell}
        onOpenStudent={setOpenEnrollmentId}
      />
      <GradeStudentSheet student={openStudent} columns={columns} drafts={drafts} onPatch={changeDraft} onClose={() => setOpenEnrollmentId(null)} />
    </GlassCard>
  );
}
