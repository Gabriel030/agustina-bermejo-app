import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export const PENDING_SECTION_SCHEDULE_REQUESTS_KEY = ['section-modification-requests', 'pending'];

export function usePendingSectionScheduleRequests() {
  return useQuery({
    queryKey: PENDING_SECTION_SCHEDULE_REQUESTS_KEY,
    queryFn: () => apiFetch('/section-modification-requests/pending'),
  });
}

export function useSectionScheduleChangePreview(sectionId, slots, enabled) {
  return useQuery({
    queryKey: ['subject-section-modification-preview', sectionId, slots],
    queryFn: () =>
      apiFetch(`/subject-sections/${sectionId}/modification-request/preview`, {
        method: 'POST',
        body: JSON.stringify({ slots }),
      }),
    enabled,
    retry: false,
  });
}

function useSectionScheduleDecisionInvalidation() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ['section-modification-requests'] });
    queryClient.invalidateQueries({ queryKey: ['ciclo-secciones'] });
    queryClient.invalidateQueries({ queryKey: ['ciclos'] });
    queryClient.invalidateQueries({ queryKey: ['ciclo'] });
  };
}

export function useApproveSectionScheduleChange() {
  const invalidate = useSectionScheduleDecisionInvalidation();
  return useMutation({
    mutationFn: ({ sectionId }) =>
      apiFetch(`/subject-sections/${sectionId}/modification-request/approve`, { method: 'POST' }),
    onSettled: invalidate,
  });
}

export function useRejectSectionScheduleChange() {
  const invalidate = useSectionScheduleDecisionInvalidation();
  return useMutation({
    mutationFn: ({ sectionId, reason }) =>
      apiFetch(`/subject-sections/${sectionId}/modification-request/reject`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      }),
    onSettled: invalidate,
  });
}
