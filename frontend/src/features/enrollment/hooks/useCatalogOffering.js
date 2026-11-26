import { useState } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import {
  admissionBadge,
  buildOfferingItems,
  COURSE_LEVEL,
  nextInformativeSession,
  todayIsoDate,
} from '@/features/enrollment/lib/catalogOffering';

function catalogUrl(path, filters) {
  const params = new URLSearchParams({ status: 'ACTIVE', visibleInCatalog: 'true' });
  Object.entries(filters).forEach(([name, value]) => {
    if (value) params.set(name, value);
  });
  return `${path}?${params.toString()}`;
}

export function useCatalogOffering({ area, level }) {
  const includesPrograms = level !== COURSE_LEVEL;
  const includesCourses = !level || level === COURSE_LEVEL;
  const programs = useQuery({
    queryKey: ['catalog', 'programs', { area, level }],
    queryFn: () => apiFetch(catalogUrl('/programs', { area, level })),
    enabled: includesPrograms,
  });
  const courses = useQuery({
    queryKey: ['catalog', 'courses', { area }],
    queryFn: () => apiFetch(catalogUrl('/courses', { area })),
    enabled: includesCourses,
  });
  return {
    items: buildOfferingItems(includesPrograms ? programs.data : [], includesCourses ? courses.data : []),
    isLoading: (includesPrograms && programs.isLoading) || (includesCourses && courses.isLoading),
    isError: (includesPrograms && programs.isError) || (includesCourses && courses.isError),
    error: (includesPrograms && programs.error) || (includesCourses && courses.error) || null,
    refetch: () => {
      if (includesPrograms) programs.refetch();
      if (includesCourses) courses.refetch();
    },
  };
}

export function useProgramAdmissionBadges(items) {
  const [todayIso] = useState(() => todayIsoDate());
  const programIds = items.filter((item) => item.kind === 'program').map((item) => item.id);
  const results = useQueries({
    queries: programIds.map((id) => ({
      queryKey: ['programa', id, 'cohorts'],
      queryFn: () => apiFetch(`/programs/${id}/cohorts`),
    })),
  });
  return Object.fromEntries(
    programIds.map((id, index) => {
      const result = results[index];
      return [`program-${id}`, result.isLoading ? null : admissionBadge(result.data, todayIso)];
    }),
  );
}

export function useNextInformativeSession() {
  const [clock] = useState(() => new Date());
  const query = useQuery({
    queryKey: ['informative-sessions', 'upcoming', todayIsoDate(clock)],
    queryFn: () => apiFetch(`/informative-sessions?status=SCHEDULED&from=${todayIsoDate(clock)}`),
    retry: false,
  });
  return query.isSuccess ? nextInformativeSession(query.data, clock) : null;
}
