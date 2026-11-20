import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const ROOT = 'staff-enrollment';

export function useStaffPrograms() {
  return useQuery({
    queryKey: [ROOT, 'programs'],
    queryFn: () => apiFetch('/programs?status=ACTIVE'),
  });
}

export function useStaffCourses() {
  return useQuery({
    queryKey: [ROOT, 'courses'],
    queryFn: () => apiFetch('/courses?status=ACTIVE'),
    select: (courses) => courses.filter((course) => course.registrationOpen === true),
  });
}

export function useStaffOpenCohorts(programId) {
  return useQuery({
    queryKey: [ROOT, 'programs', programId, 'cohorts'],
    queryFn: () => apiFetch(`/programs/${programId}/cohorts`),
    select: (cohorts) => cohorts.filter((cohort) => cohort.admissionOpen === true),
    enabled: !!programId,
  });
}

function sortModules(modules) {
  return [...modules].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
}

export function useStaffCourseModules(course) {
  const courseId = course?.id;
  const termId = course?.termId;

  const subjectsQuery = useQuery({
    queryKey: [ROOT, 'courses', courseId, 'subjects'],
    queryFn: () => apiFetch(`/courses/${courseId}/subjects?status=ACTIVE`),
    enabled: !!courseId,
  });
  const subjects = subjectsQuery.data;

  const sectionQueries = useQueries({
    queries: (subjects ?? []).map((subject) => ({
      queryKey: [ROOT, 'course-subjects', subject.id, 'sections'],
      queryFn: () => apiFetch(`/course-subjects/${subject.id}/sections?status=ACTIVE`),
    })),
  });

  const cyclesQuery = useQuery({
    queryKey: [ROOT, 'courses', courseId, 'cycles'],
    queryFn: () => apiFetch(`/academic-offering-cycles?courseId=${courseId}&status=APPROVED`),
    enabled: !!courseId,
  });
  const cycles = cyclesQuery.data;

  const occupancyQueries = useQueries({
    queries: (cycles ?? []).map((cycle) => ({
      queryKey: [ROOT, 'cycles', cycle.id, 'occupancy'],
      queryFn: () => apiFetch(`/academic-offering-cycles/${cycle.id}/occupancy`),
    })),
  });

  const allQueries = [subjectsQuery, cyclesQuery, ...sectionQueries, ...occupancyQueries];
  const isLoading = !!courseId && allQueries.some((query) => query.isLoading);
  const failed = allQueries.find((query) => query.isError);

  const occupancyBySection = new Map();
  occupancyQueries.forEach((query) => {
    (query.data?.sections ?? []).forEach((entry) => occupancyBySection.set(entry.sectionId, entry));
  });

  const modules = sortModules(subjects ?? []).map((subject) => {
    const index = (subjects ?? []).findIndex((item) => item.id === subject.id);
    const sections = (sectionQueries[index]?.data ?? [])
      .filter((section) => termId == null || section.termId === termId)
      .map((section) => {
        const occupancy = occupancyBySection.get(section.id);
        const capacity = (occupancy ? occupancy.capacity : section.capacity) ?? null;
        const enrolled = occupancy?.enrolled ?? 0;
        const available = capacity == null ? null : (occupancy?.available ?? Math.max(capacity - enrolled, 0));
        return { ...section, enrolled, capacity, available, full: available != null && available <= 0 };
      });
    return { ...subject, sections };
  });

  function refetch() {
    allQueries.forEach((query) => query.refetch());
  }

  return { modules, isLoading, isError: !!failed, error: failed?.error, refetch };
}

export function useRefreshCourseOccupancy() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: [ROOT, 'cycles'] });
}

function recordEnrollment(queryClient, studentId, listKey, enrollment) {
  if (enrollment?.id != null) {
    queryClient.setQueryData(['alumno', studentId, listKey], (list) =>
      list ? [...list.filter((item) => item.id !== enrollment.id), enrollment] : list,
    );
  }
  queryClient.invalidateQueries({ queryKey: ['alumno', studentId], refetchType: 'none' });
}

function reloadStudent(queryClient, studentId) {
  queryClient.invalidateQueries({ queryKey: ['alumno', studentId] });
}

export function useEnrollStudentInProgram(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ programId, cohortId }) =>
      apiFetch(`/students/${studentId}/program-enrollments`, {
        method: 'POST',
        body: JSON.stringify({ programId, cohortId }),
      }),
    onSuccess: (enrollment) => recordEnrollment(queryClient, studentId, 'program-enrollments', enrollment),
    onError: () => reloadStudent(queryClient, studentId),
  });
}

export function useEnrollStudentInCourse(studentId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ courseId, sectionOverrides }) =>
      apiFetch(`/students/${studentId}/course-enrollments`, {
        method: 'POST',
        body: JSON.stringify({ courseId, sectionOverrides }),
      }),
    onSuccess: (enrollment) => {
      recordEnrollment(queryClient, studentId, 'course-enrollments', enrollment);
      queryClient.invalidateQueries({ queryKey: [ROOT, 'cycles'] });
    },
    onError: () => reloadStudent(queryClient, studentId),
  });
}
