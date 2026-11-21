import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { PERMISSION_GRID_KEY } from '@/features/permissions/hooks/usePermissionGrid';
import { chunkChanges } from '@/features/permissions/lib/permissionGrid';

export function useSavePermissionChanges() {
  const queryClient = useQueryClient();
  return useMutation({
    meta: { handlesOwnError: true },
    mutationFn: async ({ reason, changes, sensitiveConfirmed, isSensitive, onChunkSaved }) => {
      for (const chunk of chunkChanges(changes)) {
        const body = { reason, changes: chunk };
        if (sensitiveConfirmed && chunk.some(isSensitive)) body.sensitiveConfirmed = true;
        const grid = await apiFetch('/permissions/grid', { method: 'PUT', body: JSON.stringify(body) });
        queryClient.setQueryData(PERMISSION_GRID_KEY, grid);
        onChunkSaved(chunk);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: PERMISSION_GRID_KEY }),
  });
}
