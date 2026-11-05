import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function enrollmentApiPath(enrollment) {
  return `/${enrollment.kind}-enrollments/${enrollment.id}`;
}

export function enrollmentKey(enrollment) {
  return `${enrollment.kind}-${enrollment.id}`;
}

export function parseEnrollmentKey(key) {
  const match = /^(program|course)-(\d+)$/.exec(key ?? '');
  return match ? { kind: match[1], id: Number(match[2]) } : null;
}

export function useEnrollmentStudentDiscounts(enrollment) {
  return useQuery({
    queryKey: ['student-discounts', 'enrollment', enrollment && enrollmentKey(enrollment)],
    queryFn: () => apiFetch(`${enrollmentApiPath(enrollment)}/student-discounts`),
    enabled: !!enrollment,
  });
}

export function useStudentDiscountSearch({ status, type, page, size }) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (type) params.set('type', type);
  params.set('page', String(page));
  params.set('size', String(size));
  return useQuery({
    queryKey: ['student-discounts', 'search', { status, type, page, size }],
    queryFn: () => apiFetch(`/student-discounts?${params.toString()}`),
    placeholderData: keepPreviousData,
  });
}

export function useMyStudentDiscounts() {
  return useQuery({
    queryKey: ['me', 'student-discounts'],
    queryFn: () => apiFetch('/me/student-discounts'),
  });
}

export function useGrantPreview(enrollment, payload) {
  return useQuery({
    queryKey: ['student-discounts', 'preview', enrollment && enrollmentKey(enrollment), payload],
    queryFn: () =>
      apiFetch(`${enrollmentApiPath(enrollment)}/student-discounts/preview`, { method: 'POST', body: JSON.stringify(payload) }),
    enabled: !!enrollment && !!payload,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

export function useGrantStudentDiscount(enrollment) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) =>
      apiFetch(`${enrollmentApiPath(enrollment)}/student-discounts`, { method: 'POST', body: JSON.stringify(payload) }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['student-discounts'] });
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
    },
  });
}

export function useRevokeStudentDiscount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }) =>
      apiFetch(`/student-discounts/${id}/revocation`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['student-discounts'] });
      queryClient.invalidateQueries({ queryKey: ['authorization-requests'] });
    },
  });
}

export function useEnrollmentDetail(enrollment) {
  return useQuery({
    queryKey: ['enrollment-detail', enrollment && enrollmentKey(enrollment)],
    queryFn: () => apiFetch(enrollmentApiPath(enrollment)),
    enabled: !!enrollment,
    retry: false,
  });
}
