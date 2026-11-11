import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const SECTION_PATH = { PROGRAM: 'program-sections', COURSE: 'course-sections' };

function sheetKey(sectionId, kind) {
  return ['closure-sheet', kind, String(sectionId)];
}

export function useClosureSheet(sectionId, kind) {
  return useQuery({
    queryKey: sheetKey(sectionId, kind),
    queryFn: () => apiFetch(`/${SECTION_PATH[kind]}/${sectionId}/closure-sheet`),
  });
}

export function useClosurePrerequisites(closureId, enrollmentId) {
  return useQuery({
    queryKey: ['closure-prerequisites', closureId, enrollmentId],
    queryFn: () => apiFetch(`/commission-closures/${closureId}/students/${enrollmentId}/prerequisites`),
    enabled: closureId != null && enrollmentId != null,
  });
}

export function useConfirmClosure(sectionId, kind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/${SECTION_PATH[kind]}/${sectionId}/closure/confirm`, { method: 'POST' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: sheetKey(sectionId, kind) }),
  });
}
