import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

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
