import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

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

function useSheetMutation(sectionId, kind, request) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (variables) => {
      const { path, options } = request(`/${SECTION_PATH[kind]}/${sectionId}/closure`, variables);
      return apiFetch(path, options);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: sheetKey(sectionId, kind) }),
  });
}

export function useApproveClosure(sectionId, kind) {
  return useSheetMutation(sectionId, kind, (base, body) => ({ path: `${base}/approve`, options: { method: 'POST', body: JSON.stringify(body) } }));
}

export function useReopenClosure(sectionId, kind) {
  return useSheetMutation(sectionId, kind, (base, { reason }) => ({ path: `${base}/reopen`, options: { method: 'POST', body: JSON.stringify({ reason }) } }));
}

export function useGrantClosureException(sectionId, kind) {
  return useSheetMutation(sectionId, kind, (base, { enrollmentId, reason }) => ({
    path: `${base}/students/${enrollmentId}/exception`,
    options: { method: 'PUT', body: JSON.stringify({ reason }) },
  }));
}

export function useRevokeClosureException(sectionId, kind) {
  return useSheetMutation(sectionId, kind, (base, { enrollmentId }) => ({
    path: `${base}/students/${enrollmentId}/exception`,
    options: { method: 'DELETE' },
  }));
}

export function useExportClosure(closureId) {
  return useMutation({
    mutationFn: async (format) => {
      const { blob, filename } = await apiFetchBlob(`/commission-closures/${closureId}/export?format=${format}`);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      return filename;
    },
  });
}
