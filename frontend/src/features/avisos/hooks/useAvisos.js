import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function rutaDeSeccion(tipo) {
  return tipo === 'COURSE' ? 'course-sections' : 'subject-sections';
}

export function useAvisos(sectionId, tipo) {
  const base = rutaDeSeccion(tipo);
  return useQuery({
    queryKey: [base, sectionId, 'announcements'],
    queryFn: () => apiFetch(`/${base}/${sectionId}/announcements`),
    enabled: !!sectionId,
  });
}

export function useDestinatariosDeAviso(announcementId) {
  return useQuery({
    queryKey: ['announcements', announcementId, 'recipients'],
    queryFn: () => apiFetch(`/announcements/${announcementId}/recipients`),
    enabled: !!announcementId,
  });
}
