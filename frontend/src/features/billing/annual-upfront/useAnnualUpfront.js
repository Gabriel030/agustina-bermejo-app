import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const scopePrefix = (scope) => (scope === 'student' ? '/me' : '');

export function useAnnualUpfrontQuote(enrollmentId, scope) {
  return useQuery({
    queryKey: ['annual-upfront', scope, enrollmentId, 'quote'],
    queryFn: () => apiFetch(`${scopePrefix(scope)}/program-enrollments/${enrollmentId}/annual-upfront/quote`),
    enabled: enrollmentId != null,
  });
}

export function useAnnualUpfrontRequests(enrollmentId, scope) {
  return useQuery({
    queryKey: ['annual-upfront', scope, enrollmentId, 'requests'],
    queryFn: () => apiFetch(`${scopePrefix(scope)}/program-enrollments/${enrollmentId}/annual-upfront-requests`),
    enabled: enrollmentId != null,
  });
}

function useInvalidateAnnualUpfront() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['annual-upfront'] }),
      queryClient.invalidateQueries({ queryKey: ['me', 'program-enrollments'] }),
      queryClient.invalidateQueries({ queryKey: ['students'] }),
    ]);
}

export function useRequestAnnualUpfront(enrollmentId) {
  const invalidate = useInvalidateAnnualUpfront();
  return useMutation({
    mutationFn: ({ key, body }) =>
      apiFetch(`/me/program-enrollments/${enrollmentId}/annual-upfront-requests`, {
        method: 'POST',
        headers: { 'Idempotency-Key': key },
        body: JSON.stringify(body),
      }),
    onSettled: invalidate,
  });
}

export function useDiscardAnnualUpfront() {
  const invalidate = useInvalidateAnnualUpfront();
  return useMutation({
    mutationFn: (requestId) => apiFetch(`/me/annual-upfront-requests/${requestId}/discard`, { method: 'POST' }),
    onSettled: invalidate,
  });
}

export function useRegisterAnnualUpfrontCashPayment(studentId, requestId) {
  const invalidate = useInvalidateAnnualUpfront();
  return useMutation({
    mutationFn: ({ key, body }) =>
      apiFetch(`/students/${studentId}/annual-upfront-requests/${requestId}/cash-payment`, {
        method: 'POST',
        headers: { 'Idempotency-Key': key },
        body: JSON.stringify(body),
      }),
    onSettled: invalidate,
  });
}
