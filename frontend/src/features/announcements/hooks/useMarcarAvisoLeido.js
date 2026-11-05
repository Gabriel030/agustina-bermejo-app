import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const NOVEDADES_KEY = ['me', 'announcements'];
const idsMarcados = new Set();

export function reiniciarAvisosMarcados() {
  idsMarcados.clear();
}

export function useMarcarAvisoLeido() {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: (id) => apiFetch(`/me/announcements/${id}/read`, { method: 'POST' }),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: NOVEDADES_KEY });
      const ahora = new Date().toISOString();
      queryClient.setQueryData(NOVEDADES_KEY, (lista) =>
        lista?.map((aviso) => (aviso.id === id && !aviso.readAt ? { ...aviso, readAt: ahora } : aviso)),
      );
    },
    onError: (_error, id) => {
      idsMarcados.delete(id);
      queryClient.invalidateQueries({ queryKey: NOVEDADES_KEY });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'notifications'] }),
  });

  return function marcarLeido(aviso) {
    if (aviso.readAt || idsMarcados.has(aviso.id)) return;
    idsMarcados.add(aviso.id);
    mutation.mutate(aviso.id);
  };
}
