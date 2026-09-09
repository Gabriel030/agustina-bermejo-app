import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';

export function usePerfilPropio() {
  return useQuery({ queryKey: ['me'], queryFn: () => apiFetch('/me') });
}

export function useActualizarPerfil() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (datos) => apiFetch('/me', { method: 'PUT', body: JSON.stringify(datos) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me'] }),
  });
}

export function useCambiarContrasena() {
  return useMutation({
    mutationFn: (datos) => apiFetch('/me/password', { method: 'PUT', body: JSON.stringify(datos) }),
  });
}

export function useSesionesActivas() {
  return useQuery({ queryKey: ['me', 'sessions'], queryFn: () => apiFetch('/me/sessions') });
}

export function useRevocarTodasLasSesiones() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch('/me/sessions', { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'sessions'] }),
  });
}

export function usePreferenciasNotificaciones() {
  return useQuery({
    queryKey: ['me', 'notification-preferences'],
    queryFn: () => apiFetch('/me/notification-preferences'),
  });
}

export function useActualizarPreferenciasNotificaciones() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (prefs) =>
      apiFetch('/me/notification-preferences', { method: 'PUT', body: JSON.stringify(prefs) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'notification-preferences'] }),
  });
}

export function useAvatar(habilitado = true) {
  return useQuery({
    queryKey: ['me', 'avatar'],
    enabled: habilitado,
    staleTime: Infinity,
    queryFn: async () => {
      try {
        const { blob } = await apiFetchBlob('/me/avatar');
        return URL.createObjectURL(blob);
      } catch (err) {
        if (err.status === 404) return null;
        throw err;
      }
    },
  });
}

export function useSubirAvatar() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file) => {
      const form = new FormData();
      form.append('file', file);
      return apiFetch('/me/avatar', { method: 'POST', body: form });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['me', 'avatar'] });
      queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });
}
