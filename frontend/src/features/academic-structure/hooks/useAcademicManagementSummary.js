import { useQueries } from '@tanstack/react-query';
import { usePrograms } from '@/features/academic-structure/hooks/usePrograms';
import { useCourses } from '@/features/academic-structure/hooks/useCourses';
import { useSubjects } from '@/features/academic-structure/hooks/useSubjects';
import { programCohortsQueryOptions } from '@/features/academic-structure/hooks/useCohorts';
import { buildAcademicManagementSummary } from '@/features/academic-structure/lib/academicManagementSummary';

export function useAcademicManagementSummary() {
  const programs = usePrograms();
  const courses = useCourses();
  const subjects = useSubjects('ACTIVE');
  const activePrograms = (programs.data ?? []).filter((program) => program.status === 'ACTIVE');
  const cohortQueries = useQueries({ queries: activePrograms.map((program) => programCohortsQueryOptions(program.id)) });

  const allQueries = [programs, courses, subjects, ...cohortQueries];
  const isLoading = allQueries.some((query) => query.isLoading);
  const isError = !isLoading && allQueries.some((query) => query.isError);

  function refetch() {
    allQueries.filter((query) => query.isError).forEach((query) => query.refetch());
  }

  if (isLoading || isError) return { summary: null, isLoading, isError, refetch };

  const cohortsByProgramId = Object.fromEntries(
    activePrograms.map((program, index) => [program.id, cohortQueries[index].data ?? []]),
  );
  const summary = buildAcademicManagementSummary({
    programs: programs.data ?? [],
    courses: courses.data ?? [],
    subjects: subjects.data ?? [],
    cohortsByProgramId,
    today: new Date(),
  });
  return { summary, isLoading: false, isError: false, refetch };
}
