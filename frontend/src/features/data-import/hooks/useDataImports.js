import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { pollWhenOnline } from '@/api/cachePolicy';

const RUNNING_STATUSES = ['VALIDATING', 'COMMITTING', 'ROLLING_BACK'];
const RUNNING_REFETCH_MS = 3000;
const LIST_KEY = ['data-imports', 'list'];

function detailKey(id) {
  return ['data-imports', 'detail', String(id)];
}

export function useDataImports() {
  return useQuery({ queryKey: LIST_KEY, queryFn: () => apiFetch('/data-imports') });
}

export function useDataImport(id) {
  return useQuery({
    queryKey: detailKey(id),
    queryFn: () => apiFetch(`/data-imports/${id}`),
    enabled: !!id,
    refetchInterval: (current) =>
      RUNNING_STATUSES.includes(current.state.data?.status) ? pollWhenOnline(RUNNING_REFETCH_MS)() : false,
    refetchIntervalInBackground: false,
  });
}

export function useImportRowErrors(id, { size, enabled }) {
  return useQuery({
    queryKey: ['data-imports', 'errors', String(id), size],
    queryFn: () => apiFetch(`/data-imports/${id}/errors?page=0&size=${size}`),
    enabled: !!id && enabled,
    placeholderData: keepPreviousData,
  });
}

function useBatchMutation(id, mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (batch) => {
      if (batch) queryClient.setQueryData(detailKey(id), batch);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['data-imports'] }),
  });
}

export function useCreateDataImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name) => apiFetch('/data-imports', { method: 'POST', body: JSON.stringify({ name }) }),
    onSuccess: (batch) => {
      queryClient.setQueryData(detailKey(batch.id), batch);
      return queryClient.invalidateQueries({ queryKey: LIST_KEY });
    },
  });
}

export function useUploadImportFile(id) {
  return useBatchMutation(id, ({ entityType, file }) => {
    const body = new FormData();
    body.append('file', file);
    return apiFetch(`/data-imports/${id}/files/${entityType}`, { method: 'POST', body });
  });
}

export function useValidateDataImport(id) {
  return useBatchMutation(id, () => apiFetch(`/data-imports/${id}/validate`, { method: 'POST' }));
}

export function useCommitDataImport(id) {
  return useBatchMutation(id, ({ onlyValidRows }) =>
    apiFetch(`/data-imports/${id}/commit`, { method: 'POST', body: JSON.stringify({ onlyValidRows }) }),
  );
}
