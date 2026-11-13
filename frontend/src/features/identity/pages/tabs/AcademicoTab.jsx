import { useMemo, useState } from 'react';
import { GlassCard } from '@/components/GlassCard';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { ProgressBar } from '@/components/ProgressBar';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { DataTable } from '@/components/table/DataTable';
import { FaltasDeMateria } from '@/features/attendance';
import { EnrollmentPicker } from '@/features/identity/components/EnrollmentPicker';
import {
  useCourseProgress,
  useEnrollmentOptions,
  useProgramAttendanceSummary,
  useProgramProgress,
  useProgramSectionEnrollments,
} from '@/features/identity/hooks/useStudentAcademicRecord';
import {
  buildSubjectRows,
  describeCurricularPeriod,
  describeSectionStatus,
  formatAttendancePercent,
  formatDecimal,
  parseEnrollmentKey,
} from '@/features/identity/lib/academicRecord';

const BIG_NUMBER = 'font-heading text-2xl font-bold text-ink';

function LoadError({ onRetry }) {
  return (
    <EmptyState
      icon="⚠️"
      title="No se pudo cargar el avance académico"
      description="Revisá tu conexión e intentá de nuevo."
      action={
        <Button type="button" variant="outline" onClick={onRetry}>
          Reintentar
        </Button>
      }
    />
  );
}

function SubjectDetail({ row, onViewGrades, absences }) {
  return (
    <div className="sticky left-0 flex w-fit max-w-[calc(100vw-4rem)] flex-col gap-1 p-3 text-size-13 text-ink-soft">
      {row.schedule && <span>{row.schedule}</span>}
      <span>
        {row.instructorTitular ? `Titular ${row.instructorTitular}` : 'Sin docente titular'}
        {row.instructorAdjunto ? ` · Adjunto ${row.instructorAdjunto}` : ''}
      </span>
      <Button type="button" variant="outline" className="mt-1 h-10 w-fit" onClick={() => onViewGrades(row.id)}>
        Ver notas →
      </Button>
      <FaltasDeMateria
        sectionEnrollmentId={row.id}
        studentName={absences.studentName}
        subjectLabel={`${row.subjectName} · Com. ${row.sectionName}`}
        canJustify={absences.canJustify}
        authorName={absences.authorName}
        roleBase={absences.roleBase}
      />
    </div>
  );
}

function buildSubjectColumns() {
  return [
    {
      id: 'subject',
      header: 'Materia',
      accessorFn: (row) => row.subjectName,
      enableSorting: false,
      meta: { sticky: true },
      cell: ({ row }) => (
        <div className="flex min-w-[8.5rem] max-w-[10rem] flex-col whitespace-normal">
          <span className="text-size-13 font-semibold leading-tight text-ink">{row.original.subjectName}</span>
          <span className="text-size-12 text-ink-faint">Com. {row.original.sectionName}</span>
        </div>
      ),
    },
    {
      id: 'year',
      header: 'Año',
      accessorFn: (row) => row.curricularYear ?? 0,
      enableSorting: false,
      meta: { filterable: true, filterTitle: 'Año', filterLabel: (value) => (value ? `${value}.º año` : 'Sin año') },
      cell: ({ row }) => (
        <span className="text-ink-faint">{describeCurricularPeriod(row.original.curricularYear, row.original.curricularTerm)}</span>
      ),
    },
    {
      id: 'status',
      header: 'Estado',
      accessorFn: (row) => row.status,
      enableSorting: false,
      meta: { filterable: true, filterTitle: 'Estado', filterLabel: (value) => describeSectionStatus(value).label },
      cell: ({ row }) => {
        const status = describeSectionStatus(row.original.status);
        return <StatusPill tone={status.tone}>{status.label}</StatusPill>;
      },
    },
    {
      id: 'attempt',
      header: 'Intento',
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => row.original.attemptNumber,
    },
    {
      id: 'finalScore',
      header: 'Nota final',
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => <b>{formatDecimal(row.original.finalScore)}</b>,
    },
    {
      id: 'attendance',
      header: 'Asist.',
      enableSorting: false,
      meta: { align: 'right' },
      cell: ({ row }) => formatAttendancePercent(row.original.attendancePercent),
    },
  ];
}

function PendingList({ title, items }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-heading text-size-15 font-semibold text-ink">
          {title} · {items.length}
        </h2>
        {items.length > 0 && (
          <Button type="button" variant="ghost" onClick={() => setExpanded((current) => !current)}>
            {expanded ? 'Ocultar' : 'Ver lista'}
          </Button>
        )}
      </div>
      {expanded && (
        <div className="flex flex-wrap gap-2">
          {items.map((name) => (
            <StatusPill key={name}>{name}</StatusPill>
          ))}
        </div>
      )}
    </div>
  );
}

function ProgramRecord({ programEnrollmentId, onViewGrades, absences }) {
  const progress = useProgramProgress(programEnrollmentId);
  const sections = useProgramSectionEnrollments(programEnrollmentId);
  const attendance = useProgramAttendanceSummary(programEnrollmentId);
  const columns = useMemo(() => buildSubjectColumns(), []);
  const rows = useMemo(() => buildSubjectRows(sections.data, attendance.data), [sections.data, attendance.data]);

  if (progress.isError || sections.isError) {
    return (
      <LoadError
        onRetry={() => {
          progress.refetch();
          sections.refetch();
        }}
      />
    );
  }

  const data = progress.data;
  const creditsPercent = data && data.creditosRequeridos > 0 ? (data.creditosAcumulados / data.creditosRequeridos) * 100 : 0;

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {progress.isLoading ? (
          [1, 2, 3, 4].map((item) => <Skeleton key={item} className="h-24 w-full rounded-xl" />)
        ) : (
          <>
            <StatCard label="Créditos">
              <span className={BIG_NUMBER}>{data.creditosAcumulados}</span>
              <span className="text-size-13 text-ink-faint"> / {data.creditosRequeridos}</span>
              <ProgressBar value={creditsPercent} showValue={false} className="mt-2" label="Avance de créditos" />
            </StatCard>
            <StatCard label="Promedio general">
              <span className={BIG_NUMBER}>{formatDecimal(data.promedioGeneral)}</span>
            </StatCard>
            <StatCard label="Condición">
              <StatusPill tone={data.esRegular ? 'success' : 'neutral'}>{data.esRegular ? 'Con cursada activa' : 'Sin cursada activa'}</StatusPill>
            </StatCard>
            <StatCard label="Elegible para egresar">
              <StatusPill tone={data.elegibleParaCompletar ? 'success' : 'neutral'}>
                {data.elegibleParaCompletar ? 'Sí' : 'Todavía no'}
              </StatusPill>
            </StatCard>
          </>
        )}
      </div>

      <GlassCard className="flex min-w-0 flex-col gap-2 p-3 sm:p-5">
        <h2 className="font-heading text-size-15 font-semibold text-ink">Cursadas</h2>
        <DataTable
          columns={columns}
          data={rows}
          getRowId={(row) => String(row.id)}
          isLoading={sections.isLoading}
          mobileLayout="table"
          minWidth={0}
          expandLabel="Ver detalle de la cursada"
          emptyState={<EmptyState icon="📚" title="Sin cursadas" description="Todavía no se inscribió a ninguna materia." />}
          renderExpanded={(row) => <SubjectDetail row={row.original} onViewGrades={onViewGrades} absences={absences} />}
        />
      </GlassCard>

      {data && (
        <GlassCard className="flex flex-col gap-4 p-5">
          <PendingList title="Obligatorias pendientes" items={data.obligatoriasPendientes ?? []} />
          {(data.gruposElectivos ?? []).length > 0 && (
            <div className="flex flex-col gap-2">
              <h2 className="font-heading text-size-15 font-semibold text-ink">Grupos de electivas</h2>
              {data.gruposElectivos.map((group) => (
                <div key={group.electiveGroupId} className="flex items-center gap-2 text-size-13">
                  <span className="min-w-0 flex-1 text-ink">{group.electiveGroupName}</span>
                  <span className="text-ink-faint">
                    {group.cantidadAprobada} de {group.minRequired}
                  </span>
                  <StatusPill tone={group.cumplido ? 'success' : 'warning'}>{group.cumplido ? 'Cumplido' : 'Falta'}</StatusPill>
                </div>
              ))}
            </div>
          )}
        </GlassCard>
      )}
    </div>
  );
}

function CourseRecord({ courseEnrollmentId }) {
  const progress = useCourseProgress(courseEnrollmentId);

  if (progress.isError) return <LoadError onRetry={() => progress.refetch()} />;
  if (progress.isLoading) {
    return (
      <div className="grid grid-cols-2 gap-3">
        <Skeleton className="h-24 w-full rounded-xl" />
        <Skeleton className="h-24 w-full rounded-xl" />
      </div>
    );
  }
  const data = progress.data;
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3">
        <StatCard label="Módulos pendientes">
          <span className={BIG_NUMBER}>{data.modulosPendientes.length}</span>
        </StatCard>
        <StatCard label="Elegible para completar">
          <StatusPill tone={data.elegibleParaCompletar ? 'success' : 'neutral'}>
            {data.elegibleParaCompletar ? 'Sí' : 'Todavía no'}
          </StatusPill>
        </StatCard>
      </div>
      <GlassCard className="flex flex-col gap-4 p-5">
        <PendingList title="Módulos pendientes" items={data.modulosPendientes} />
      </GlassCard>
    </div>
  );
}

export function AcademicoTab({ studentId, enrollmentKey, onEnrollmentChange, onViewGrades, absences }) {
  const { options, isLoading, isError, refetch } = useEnrollmentOptions(studentId);

  if (isLoading) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando">
        <Skeleton className="h-10 w-full max-w-md" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }
  if (isError) return <LoadError onRetry={() => refetch()} />;
  if (!options.length) {
    return (
      <EmptyState icon="🎓" title="Sin inscripciones" description="Este alumno todavía no se inscribió a una carrera ni a un curso." />
    );
  }

  const selected = options.find((option) => option.key === enrollmentKey) ?? options[0];
  const { kind, id } = parseEnrollmentKey(selected.key);

  return (
    <div className="flex flex-col gap-4">
      <EnrollmentPicker id="academic-enrollment" label="Inscripción" options={options} value={selected.key} onChange={onEnrollmentChange} />
      {kind === 'program' ? (
        <ProgramRecord
          key={selected.key}
          programEnrollmentId={id}
          absences={absences}
          onViewGrades={(sectionEnrollmentId) => onViewGrades(selected.key, sectionEnrollmentId)}
        />
      ) : (
        <CourseRecord key={selected.key} courseEnrollmentId={id} />
      )}
    </div>
  );
}
