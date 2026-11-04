import { useMemo } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/table/DataTable';
import { EnrollmentPicker } from '@/features/identity/components/EnrollmentPicker';
import {
  useCourseSectionEnrollments,
  useEnrollmentOptions,
  useProgramSectionEnrollments,
  useSectionGradeItems,
} from '@/features/identity/hooks/useStudentAcademicRecord';
import {
  describeCurricularPeriod,
  describeSectionStatus,
  formatDecimal,
  formatGradeDate,
  gradeItemLabel,
  parseEnrollmentKey,
} from '@/features/identity/lib/academicRecord';

const GRADE_COLUMNS = [
  {
    id: 'item',
    header: 'Ítem',
    enableSorting: false,
    cell: ({ row }) => <b className="font-semibold text-ink">{gradeItemLabel(row.original.type)}</b>,
  },
  { id: 'sequence', header: 'N.º', enableSorting: false, meta: { align: 'right' }, cell: ({ row }) => row.original.sequenceNumber },
  {
    id: 'score',
    header: 'Nota',
    enableSorting: false,
    meta: { align: 'right' },
    cell: ({ row }) => formatDecimal(row.original.score),
  },
  {
    id: 'date',
    header: 'Fecha',
    enableSorting: false,
    cell: ({ row }) => <span className="text-ink-faint">{formatGradeDate(row.original.date)}</span>,
  },
];

function describeSectionOption(kind, section) {
  if (kind === 'course') {
    return { id: section.id, label: section.courseSubjectName, status: section.status, finalScore: null };
  }
  const period = describeCurricularPeriod(section.curricularYear, section.curricularTerm);
  return {
    id: section.id,
    label: `${section.subjectName} · Com. ${section.sectionName} · ${period}`,
    status: section.status,
    finalScore: section.finalScore,
  };
}

function GradesPanel({ kind, section }) {
  const grades = useSectionGradeItems(kind, section.id);
  const status = describeSectionStatus(section.status);

  return (
    <GlassCard className="flex min-w-0 flex-col gap-3 p-3 sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <StatusPill tone={status.tone}>{status.label}</StatusPill>
        {section.finalScore != null && (
          <span className="text-[13px] text-ink-faint">
            Nota final <b className="text-ink">{formatDecimal(section.finalScore)}</b>
          </span>
        )}
      </div>
      <DataTable
        columns={GRADE_COLUMNS}
        data={grades.data}
        getRowId={(row) => String(row.id)}
        isLoading={grades.isLoading}
        isError={grades.isError}
        onRetry={() => grades.refetch()}
        errorMessage="No se pudieron cargar las notas."
        mobileLayout="table"
        minWidth={0}
        emptyState={<EmptyState icon="📝" title="Sin notas cargadas" description="Esta cursada todavía no tiene notas." />}
      />
    </GlassCard>
  );
}

function SectionGrades({ kind, enrollmentId, sectionEnrollmentId, onSectionChange }) {
  const programSections = useProgramSectionEnrollments(kind === 'program' ? enrollmentId : null);
  const courseSections = useCourseSectionEnrollments(kind === 'course' ? enrollmentId : null);
  const query = kind === 'program' ? programSections : courseSections;
  const sections = useMemo(() => (query.data ?? []).map((section) => describeSectionOption(kind, section)), [query.data, kind]);

  if (query.isError) {
    return (
      <EmptyState
        icon="⚠️"
        title="No se pudieron cargar las notas"
        description="Revisá tu conexión e intentá de nuevo."
        action={
          <Button type="button" variant="outline" onClick={() => query.refetch()}>
            Reintentar
          </Button>
        }
      />
    );
  }
  if (query.isLoading) return <Skeleton className="h-40 w-full" />;
  if (!sections.length) {
    return <EmptyState icon="📝" title="Sin cursadas" description="Todavía no se inscribió a ninguna materia." />;
  }

  const selected = sections.find((section) => section.id === sectionEnrollmentId) ?? sections[0];
  const options = sections.map((section) => ({ key: String(section.id), label: section.label }));

  return (
    <div className="flex flex-col gap-4">
      <EnrollmentPicker
        id="evaluation-section"
        label="Cursada"
        options={options}
        value={String(selected.id)}
        onChange={(value) => onSectionChange(Number(value))}
      />
      <GradesPanel key={`${kind}-${selected.id}`} kind={kind} section={selected} />
    </div>
  );
}

export function EvaluacionTab({ studentId, enrollmentKey, onEnrollmentChange, sectionEnrollmentId, onSectionChange }) {
  const { options, isLoading, isError, refetch } = useEnrollmentOptions(studentId);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando">
        <Skeleton className="h-10 w-full max-w-md" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  if (isError) {
    return (
      <EmptyState
        icon="⚠️"
        title="No se pudieron cargar las notas"
        description="Revisá tu conexión e intentá de nuevo."
        action={
          <Button type="button" variant="outline" onClick={() => refetch()}>
            Reintentar
          </Button>
        }
      />
    );
  }
  if (!options.length) {
    return (
      <EmptyState icon="🎓" title="Sin inscripciones" description="Este alumno todavía no se inscribió a una carrera ni a un curso." />
    );
  }

  const selected = options.find((option) => option.key === enrollmentKey) ?? options[0];
  const { kind, id } = parseEnrollmentKey(selected.key);

  return (
    <div className="flex flex-col gap-4">
      <EnrollmentPicker id="evaluation-enrollment" label="Inscripción" options={options} value={selected.key} onChange={onEnrollmentChange} />
      <SectionGrades
        key={selected.key}
        kind={kind}
        enrollmentId={id}
        sectionEnrollmentId={sectionEnrollmentId}
        onSectionChange={onSectionChange}
      />
    </div>
  );
}
