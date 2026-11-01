import { Link, useSearchParams } from 'react-router-dom';
import { CalendarDays, Plus } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { ProgressBar } from '@/components/ProgressBar';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { TabCount } from '@/components/TabCount';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useMisCursos } from '@/features/enrollment/hooks/useStudentEnrollment';
import {
  describeCourseEnrollmentStatus,
  isCourseEnrollmentInProgress,
} from '@/features/enrollment/lib/courseEnrollmentStatus';

const TABS = ['en-curso', 'terminados'];
const CATALOG_COURSES_PATH = '/catalogo?tab=cursos';

function formatEnrollmentDate(value) {
  return value ? new Date(value).toLocaleDateString('es-AR') : null;
}

function CourseCard({ enrollment }) {
  const { label, tone } = describeCourseEnrollmentStatus(enrollment.status);
  const enrolledOn = formatEnrollmentDate(enrollment.enrollmentDate);

  return (
    <GlassCard className="flex flex-col gap-4 p-5">
      <div className="flex items-center justify-between gap-3">
        <div className="flex size-10 items-center justify-center rounded-xl bg-accent-soft text-brand">
          <CalendarDays className="size-5" aria-hidden="true" />
        </div>
        <StatusPill tone={tone}>{label}</StatusPill>
      </div>
      <div>
        <div className="font-heading text-[15px] font-semibold text-ink">{enrollment.courseName}</div>
        {enrolledOn && <div className="mt-0.5 text-xs text-ink-faint">Inscripto el {enrolledOn}</div>}
      </div>
      {enrollment.progressPercent != null && <ProgressBar label="Progreso" value={enrollment.progressPercent} />}
    </GlassCard>
  );
}

function CatalogCard() {
  return (
    <Link
      to={CATALOG_COURSES_PATH}
      className="flex min-h-40 flex-col items-center justify-center gap-2.5 rounded-xl border border-dashed border-border bg-surface/60 p-5 text-ink-faint transition-colors hover:border-brand/40 hover:text-ink"
    >
      <div className="flex size-10 items-center justify-center rounded-xl bg-neutral-bg">
        <Plus className="size-5" aria-hidden="true" />
      </div>
      <span className="text-[13px] font-semibold">Ver oferta de cursos</span>
    </Link>
  );
}

export function AlumnoMisCursos() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const tab = TABS.includes(requestedTab) ? requestedTab : 'en-curso';
  const { data: enrollments, isLoading } = useMisCursos();

  const inProgress = (enrollments ?? []).filter(isCourseEnrollmentInProgress);
  const finished = (enrollments ?? []).filter((enrollment) => !isCourseEnrollmentInProgress(enrollment));
  const visible = tab === 'en-curso' ? inProgress : finished;

  function changeTab(nextTab) {
    setSearchParams({ tab: nextTab }, { replace: true });
  }

  return (
    <PageContainer title="Mis cursos">
      <Tabs value={tab} onValueChange={changeTab}>
        <TabsList variant="segmented">
          <TabsTrigger value="en-curso">
            En curso <TabCount count={inProgress.length} />
          </TabsTrigger>
          <TabsTrigger value="terminados">
            Terminados <TabCount count={finished.length} />
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-44" />
          <Skeleton className="h-44" />
          <Skeleton className="h-44" />
        </div>
      ) : visible.length === 0 ? (
        <GlassCard className="flex flex-1 flex-col p-12">
          {tab === 'en-curso' ? (
            <EmptyState
              title="No estás cursando ningún curso"
              description="Explorá la oferta de cursos para inscribirte."
              action={
                <Button asChild variant="gradient">
                  <Link to={CATALOG_COURSES_PATH}>Ver oferta de cursos</Link>
                </Button>
              }
            />
          ) : (
            <EmptyState title="Todavía no terminaste ningún curso" />
          )}
        </GlassCard>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {visible.map((enrollment) => (
            <CourseCard key={enrollment.id} enrollment={enrollment} />
          ))}
          {tab === 'en-curso' && <CatalogCard />}
        </div>
      )}
    </PageContainer>
  );
}
