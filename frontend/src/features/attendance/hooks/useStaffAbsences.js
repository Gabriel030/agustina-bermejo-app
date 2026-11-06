import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const attendanceKey = (sectionEnrollmentId) => ['section-enrollments', sectionEnrollmentId, 'attendance'];

export function useEnrollmentAttendance(sectionEnrollmentId) {
  return useQuery({
    queryKey: attendanceKey(sectionEnrollmentId),
    queryFn: () => apiFetch(`/section-enrollments/${sectionEnrollmentId}/attendance`),
    enabled: !!sectionEnrollmentId,
  });
}

export function useJustifyAbsence(sectionEnrollmentId) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: attendanceKey(sectionEnrollmentId) });
  const mutation = useMutation({
    mutationFn: ({ attendanceId, reason, exceptionReason }) =>
      apiFetch(`/attendance/${attendanceId}/justify`, {
        method: 'POST',
        body: JSON.stringify({ reason, ...(exceptionReason ? { exceptionReason } : {}) }),
      }),
    onSuccess: refresh,
  });
  return { ...mutation, refresh };
}
