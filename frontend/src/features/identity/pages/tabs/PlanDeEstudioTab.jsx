import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { EnrollmentPicker } from '@/features/identity/components/EnrollmentPicker';
import { useEnrollmentOptions } from '@/features/identity/hooks/useStudentAcademicRecord';
import { parseEnrollmentKey } from '@/features/identity/lib/academicRecord';
import { TabPlanDeEstudio } from '@/features/workspace';

export function PlanDeEstudioTab({ studentId, enrollmentKey, onEnrollmentChange, onViewGrades }) {
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
        title="No se pudo cargar el plan de estudio"
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
      <EnrollmentPicker id="plan-enrollment" label="Inscripción" options={options} value={selected.key} onChange={onEnrollmentChange} />
      {kind === 'course' ? (
        <EmptyState icon="📚" title="Los cursos no tienen plan de estudio" />
      ) : (
        <TabPlanDeEstudio
          key={selected.key}
          carreraId={id}
          audience="staff"
          onViewEnrollment={(sectionEnrollmentId) => onViewGrades(selected.key, sectionEnrollmentId)}
        />
      )}
    </div>
  );
}
