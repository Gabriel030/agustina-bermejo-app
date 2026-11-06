import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { offlineQuery } from '@/api/offlineQuery';

export function useMiAsistencia(sectionEnrollmentId, { enabled = true } = {}) {
  return useQuery({
    queryKey: ['me', 'section-enrollments', sectionEnrollmentId, 'attendance'],
    queryFn: () => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/attendance`),
    enabled: !!sectionEnrollmentId && enabled,
  });
}

export function toAttendanceSummary(entries, risk) {
  return {
    risk: {
      calculable: risk.calculable,
      libre: risk.libre,
      absencePercent: risk.absencePercent,
      maxAbsencePercent: risk.maxAbsencePercent,
    },
    days: (entries ?? []).map((entry) => ({ id: entry.id, classDate: entry.classDate, status: entry.status })),
  };
}

export function useMyAttendanceSummary(sectionEnrollmentId) {
  return useQuery(
    offlineQuery('N2', {
      queryKey: ['me', 'attendance-summary', sectionEnrollmentId],
      queryFn: async () => {
        const [entries, risk] = await Promise.all([
          apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/attendance`),
          apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/attendance/risk`),
        ]);
        return toAttendanceSummary(entries, risk);
      },
      enabled: !!sectionEnrollmentId,
      staleTime: 60_000,
    }),
  );
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

export function useAsistenciaPorMateria(programEnrollmentId) {
  return useQuery({
    queryKey: ['program-enrollments', programEnrollmentId, 'attendance-summary'],
    queryFn: () => apiFetch(`/program-enrollments/${programEnrollmentId}/attendance-summary`),
    enabled: !!programEnrollmentId,
  });
}
