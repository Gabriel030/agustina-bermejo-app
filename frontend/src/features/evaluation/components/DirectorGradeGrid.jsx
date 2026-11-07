import { useMemo, useState } from 'react';
import { TriangleAlert } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useTeacherGradeGrid } from '../hooks/useTeacherGradeGrid';
import { gradeColumnKey } from '../lib/gradeGrid';
import { GradeCorrectionSheet } from './GradeCorrectionSheet';
import { GradeGridTable } from './GradeGridTable';

function formatDeadline(isoDate) {
  const [year, month, day] = isoDate.split('-');
  return `${day}/${month}/${year}`;
}

export function DirectorGradeGrid({ sectionId, kind }) {
  const { data: grid, isLoading, isError, refetch } = useTeacherGradeGrid(sectionId, kind, { staff: true });
  const [open, setOpen] = useState(null);
  const [ended409, setEnded409] = useState(false);
  const deadline = grid?.correctionDeadline ?? null;
  const today = new Date().toLocaleDateString('sv-SE');
  const periodEnded = ended409 || (deadline !== null && today > deadline);
  const students = useMemo(() => grid?.students ?? [], [grid]);
  const columns = useMemo(() => grid?.columns ?? [], [grid]);

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
        <EmptyState icon="👥" title="Todavía no hay alumnos inscriptos" description="Cuando se inscriba el primero vas a ver sus notas acá." />
      </GlassCard>
    );
  }

  if (!columns.length) {
    return (
      <GlassCard className="p-4">
        <EmptyState icon="📋" title="Esta comisión todavía no tiene un esquema de evaluación" description="Sin esquema no hay notas para ver ni corregir." />
      </GlassCard>
    );
  }

  const openStudent = open ? (students.find((student) => student.enrollmentId === open.enrollmentId) ?? null) : null;

  return (
    <GlassCard className="flex min-w-0 flex-col gap-3 p-3 md:p-4">
      {periodEnded && (
        <div role="alert" className="flex items-start gap-2.5 rounded-xl bg-danger-bg p-3 text-[13px] text-danger">
          <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <div>
            <b>{deadline ? `El plazo de corrección terminó el ${formatDeadline(deadline)}.` : 'El plazo de corrección terminó.'}</b>
            <br />
            Las notas publicadas de este cuatrimestre ya no se pueden corregir.
          </div>
        </div>
      )}
      <p className="text-[12.5px] text-ink-faint">Tocá una nota para ver el detalle y corregirla.</p>
      <GradeGridTable
        students={students}
        columns={columns}
        readOnly
        onOpenStudent={(enrollmentId) => setOpen({ enrollmentId, anchorKey: null })}
        onOpenCell={(enrollmentId, column) => setOpen({ enrollmentId, anchorKey: gradeColumnKey(column.type, column.sequenceNumber) })}
      />
      <GradeCorrectionSheet
        key={open ? `${open.enrollmentId}:${open.anchorKey}` : 'closed'}
        student={openStudent}
        columns={columns}
        anchorKey={open?.anchorKey ?? null}
        sectionId={sectionId}
        kind={kind}
        periodEnded={periodEnded}
        onPeriodEnded={() => setEnded409(true)}
        onClose={() => setOpen(null)}
      />
    </GlassCard>
  );
}
