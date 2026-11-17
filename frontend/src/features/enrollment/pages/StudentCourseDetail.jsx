import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Info } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { LoadErrorState } from '@/components/LoadErrorState';
import { PageContainer } from '@/components/PageContainer';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { CourseSubjectPanel } from '@/features/enrollment/components/CourseSubjectPanel';
import { useMyCourseEnrollment } from '@/features/enrollment/hooks/useStudentEnrollment';
import { describeCourseEnrollmentStatus } from '@/features/enrollment/lib/courseEnrollmentStatus';
import { formatIsoDate } from '@/features/enrollment/lib/courseSubjectDetail';

const MY_COURSES_PATH = '/alumno/mis-cursos';

function buildSubtitle(enrollment) {
  const parts = [`Inscripto el ${formatIsoDate(enrollment.enrollmentDate)}`];
  if (enrollment.completionDate) parts.push(`Finalizado el ${formatIsoDate(enrollment.completionDate)}`);
  return parts.join(' · ');
}

function CourseBody({ enrollment }) {
  const [openId, setOpenId] = useState(enrollment.subjects[0]?.sectionEnrollment.id ?? null);

  if (enrollment.status === 'WAITLISTED') {
    return (
      <GlassCard className="flex flex-col p-10">
        <EmptyState
          icon="⏳"
          title="Estás en lista de espera"
          description="Cuando se libere un lugar te inscribimos y te avisamos. Hasta entonces no hay asistencia ni notas."
        />
      </GlassCard>
    );
  }

  return (
    <>
      {enrollment.status === 'WITHDRAWN' && (
        <GlassCard className="flex items-center gap-2.5 bg-neutral-bg px-4 py-3 text-size-13 text-ink-soft">
          <Info className="size-4 shrink-0" aria-hidden="true" />
          <span>Te diste de baja de este curso. Ves tus datos hasta la baja, sin cambios.</span>
        </GlassCard>
      )}
      {enrollment.subjects.length === 0 ? (
        <GlassCard className="flex flex-col p-10">
          <EmptyState title="Todavía no hay materias" description="Cuando se asignen materias a tu curso, las vas a ver acá." />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-3">
          {enrollment.subjects.map((subject) => {
            const id = subject.sectionEnrollment.id;
            return (
              <CourseSubjectPanel key={id} subject={subject} open={openId === id} onOpenChange={(next) => setOpenId(next ? id : null)} />
            );
          })}
        </div>
      )}
    </>
  );
}

export function StudentCourseDetail() {
  const { id } = useParams();
  const { data: enrollment, isLoading, isError, error, refetch } = useMyCourseEnrollment(id);

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="h-8 w-72" />
        <Skeleton className="h-16" />
        <Skeleton className="h-16" />
      </PageContainer>
    );
  }

  if (isError || !enrollment) {
    return (
      <PageContainer>
        <LoadErrorState error={error} onRetry={refetch} resource="tu curso" backTo={MY_COURSES_PATH} backLabel="Mis cursos" />
      </PageContainer>
    );
  }

  const { label, tone } = describeCourseEnrollmentStatus(enrollment.status);

  return (
    <PageContainer title={enrollment.courseName} description={buildSubtitle(enrollment)} action={<StatusPill tone={tone}>{label}</StatusPill>}>
      <CourseBody key={enrollment.id} enrollment={enrollment} />
    </PageContainer>
  );
}
