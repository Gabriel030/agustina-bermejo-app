import { useMemo } from 'react';
import { useCourses, usePrograms } from '@/features/academic-structure';

const ACTIVE = 'ACTIVE';

export function useOfferNames() {
  const programs = usePrograms();
  const courses = useCourses();
  return useMemo(() => {
    const programList = programs.data ?? [];
    const courseList = courses.data ?? [];
    const programNames = new Map(programList.map((program) => [program.id, program.name]));
    const courseNames = new Map(courseList.map((course) => [course.id, course.name]));
    return {
      isLoading: programs.isLoading || courses.isLoading,
      programs: programList.filter((program) => program.status === ACTIVE),
      courses: courseList.filter((course) => course.status === ACTIVE),
      programName: (id) => programNames.get(id) ?? `Carrera ${id}`,
      courseName: (id) => courseNames.get(id) ?? `Curso ${id}`,
    };
  }, [programs.data, programs.isLoading, courses.data, courses.isLoading]);
}

export function scopeLabel(entry, names) {
  if (entry.programId != null) return names.programName(entry.programId);
  if (entry.courseId != null) return names.courseName(entry.courseId);
  return 'Toda la institución';
}
