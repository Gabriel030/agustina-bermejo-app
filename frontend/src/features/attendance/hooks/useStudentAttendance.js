import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useMiAsistencia(sectionEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'section-enrollments', sectionEnrollmentId, 'attendance'],
    queryFn: () => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/attendance`),
    enabled: !!sectionEnrollmentId,
  });
}

export function useMiRiesgoDeAsistencia(sectionEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'section-enrollments', sectionEnrollmentId, 'attendance', 'risk'],
    queryFn: () => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/attendance/risk`),
    enabled: !!sectionEnrollmentId,
  });
}

export function useJustificarInasistencia() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ attendanceId, comprobante, reason }) => {
      const formData = new FormData();
      formData.append('comprobante', comprobante);
      if (reason) formData.append('reason', reason);
      return apiFetch(`/me/attendance/${attendanceId}/justify`, { method: 'POST', body: formData });
    },
    onSuccess: (_data, { sectionEnrollmentId }) =>
      queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments', sectionEnrollmentId, 'attendance'] }),
  });
}
