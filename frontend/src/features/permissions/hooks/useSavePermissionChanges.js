import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { PERMISSION_GRID_KEY } from '@/features/permissions/hooks/usePermissionGrid';
import { chunkChanges } from '@/features/permissions/lib/permissionGrid';

export function useSavePermissionChanges() {
  const queryClient = useQueryClient();
  return useMutation({
    meta: { handlesOwnError: true },
    mutationFn: async ({ reason, changes, onChunkSaved }) => {
      for (const chunk of chunkChanges(changes)) {
        const grid = await apiFetch('/permissions/grid', { method: 'PUT', body: JSON.stringify({ reason, changes: chunk }) });
        queryClient.setQueryData(PERMISSION_GRID_KEY, grid);
        onChunkSaved(chunk);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: PERMISSION_GRID_KEY }),
  });
}
