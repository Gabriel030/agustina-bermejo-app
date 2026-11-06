import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '@/api/client'
import { offlineQuery } from '@/api/offlineQuery'

export function toScheduleView(response) {
  return {
    generatedAt: response.generatedAt,
    items: (response.items ?? []).map((item) => ({
      sectionEnrollmentId: item.sectionEnrollmentId,
      courseName: item.courseName,
      sectionName: item.sectionName,
      courseSubjectName: item.courseSubjectName,
      schedule: item.schedule,
      instructorTitular: item.instructorTitular,
    })),
  }
}

export function useMySchedule() {
  return useQuery(
    offlineQuery('N1', {
      queryKey: ['me', 'schedule'],
      queryFn: async () => toScheduleView(await apiFetch('/me/schedule')),
      staleTime: 60_000,
      refetchInterval: () => (navigator.onLine ? 5 * 60_000 : false),
    }),
  )
}
