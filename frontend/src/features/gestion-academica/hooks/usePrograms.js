import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function usePrograms(status) {
  const qs = status ? `?status=${status}` : '';
  return useQuery({
    queryKey: ['programs', { status }],
    queryFn: () => apiFetch(`/programs${qs}`),
  });
}

export function useProgram(id) {
  return useQuery({
    queryKey: ['programs', id],
    queryFn: () => apiFetch(`/programs/${id}`),
    enabled: !!id,
  });
}

export function useCreateProgram() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch('/programs', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['programs'] }),
  });
}

export function useUpdateProgram(id) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch(`/programs/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['programs'] });
      queryClient.invalidateQueries({ queryKey: ['programs', id] });
    },
  });
}

export function useDeactivateProgram() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/programs/${id}/deactivate`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['programs'] }),
  });
}

export function useSetProgramCatalogVisibility() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, visible }) =>
      apiFetch(`/programs/${id}/catalog-visibility`, { method: 'PUT', body: JSON.stringify({ visible }) }),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['programs'] });
      queryClient.invalidateQueries({ queryKey: ['programs', id] });
    },
  });
}

export function useActivateProgram() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/programs/${id}/activate`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['programs'] }),
  });
}

export function useProgramSubjects(programId, status) {
  const qs = status ? `?status=${status}` : '';
  return useQuery({
    queryKey: ['program-subjects', { programId, status }],
    queryFn: () => apiFetch(`/programs/${programId}/subjects${qs}`),
    enabled: !!programId,
  });
}

export function useAddProgramSubject(programId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch(`/programs/${programId}/subjects`, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['program-subjects', { programId }] });
      queryClient.invalidateQueries({ queryKey: ['program-subjects'] });
    },
  });
}

export function useUpdateProgramSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }) => apiFetch(`/program-subjects/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['program-subjects'] }),
  });
}

export function useDeactivateProgramSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/program-subjects/${id}/deactivate`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['program-subjects'] }),
  });
}

export function useActivateProgramSubject() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/program-subjects/${id}/activate`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['program-subjects'] }),
  });
}

export function usePrerequisites(programSubjectId) {
  return useQuery({
    queryKey: ['prerequisites', programSubjectId],
    queryFn: () => apiFetch(`/program-subjects/${programSubjectId}/prerequisites`),
    enabled: !!programSubjectId,
  });
}

export function useAddPrerequisite(programSubjectId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch(`/program-subjects/${programSubjectId}/prerequisites`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prerequisites', programSubjectId] }),
  });
}

export function useRemovePrerequisite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/program-subjects/prerequisites/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prerequisites'] }),
  });
}

export function useCorequisites(programSubjectId) {
  return useQuery({
    queryKey: ['corequisites', programSubjectId],
    queryFn: () => apiFetch(`/program-subjects/${programSubjectId}/corequisites`),
    enabled: !!programSubjectId,
  });
}

export function useAddCorequisite(programSubjectId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch(`/program-subjects/${programSubjectId}/corequisites`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['corequisites', programSubjectId] }),
  });
}

export function useRemoveCorequisite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => apiFetch(`/program-subjects/corequisites/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['corequisites'] }),
  });
}

export function useElectiveGroups() {
  return useQuery({
    queryKey: ['elective-groups'],
    queryFn: () => apiFetch('/elective-groups'),
  });
}

export function useProgramElectiveGroups(programId) {
  return useQuery({
    queryKey: ['program-elective-groups', programId],
    queryFn: () => apiFetch(`/programs/${programId}/elective-groups`),
    enabled: !!programId,
  });
}

export function useCreateElectiveGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => apiFetch('/elective-groups', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['elective-groups'] }),
  });
}

export function useAssignElectiveGroup(programId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch(`/programs/${programId}/elective-groups`, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['program-elective-groups', programId] }),
  });
}

export function useRemoveElectiveGroup(programId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (groupId) =>
      apiFetch(`/programs/${programId}/elective-groups/${groupId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['program-elective-groups', programId] }),
  });
}
