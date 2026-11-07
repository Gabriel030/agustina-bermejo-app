import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function gradeAuditKey(gradeItemId) {
  return ['grade-item-audit', gradeItemId];
}

export function useGradeItemAudit(gradeItemId) {
  return useQuery({
    queryKey: gradeAuditKey(gradeItemId),
    queryFn: () => apiFetch(`/grade-items/${gradeItemId}/audit`),
    enabled: Boolean(gradeItemId),
  });
}

export function useCorrectGradeItem(sectionId, kind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ gradeItemId, newScore, reason }) =>
      apiFetch(`/grade-items/${gradeItemId}/corrections`, {
        method: 'POST',
        body: JSON.stringify({ newScore, reason }),
      }),
    onSettled: (_data, _error, variables) => {
      queryClient.invalidateQueries({ queryKey: ['teacher-grade-grid', kind, String(sectionId)] });
      queryClient.invalidateQueries({ queryKey: gradeAuditKey(variables.gradeItemId) });
    },
  });
}
