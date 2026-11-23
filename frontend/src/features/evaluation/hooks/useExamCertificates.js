import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

function candidatesKey(boardId) {
  return ['exam-boards', boardId, 'exam-certificates'];
}

export function useExamCertificateCandidates(boardId) {
  return useQuery({
    queryKey: candidatesKey(boardId),
    queryFn: () => apiFetch(`/exam-boards/${boardId}/exam-certificates/candidates`),
    enabled: !!boardId,
  });
}

export function useIssueExamCertificateBatch(boardId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (registrationIds) =>
      apiFetch(`/exam-boards/${boardId}/exam-certificates/batch`, { method: 'POST', body: JSON.stringify({ registrationIds }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: candidatesKey(boardId) }),
  });
}

export function useRevokeExamCertificate(boardId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ certificateId, reason }) =>
      apiFetch(`/exam-certificates/${certificateId}/revoke`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: candidatesKey(boardId) }),
  });
}
