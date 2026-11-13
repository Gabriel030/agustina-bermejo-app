import { useMemo } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

const REQUESTS_KEY = ['regular-certificate-requests'];
const CERTIFICATES_KEY = ['regular-certificates'];
const LOOKUP_STALE_MS = 5 * 60_000;

export function usePendingRegularRequests() {
  return useQuery({
    queryKey: [...REQUESTS_KEY, 'PENDING_STAFF'],
    queryFn: () => apiFetch('/regular-certificate-requests?outcome=PENDING_STAFF'),
  });
}

export function useIssuedRegularCertificates() {
  return useQuery({
    queryKey: CERTIFICATES_KEY,
    queryFn: () => apiFetch('/regular-certificates'),
  });
}

function enrollmentPath(kind, enrollmentId) {
  return kind === 'COURSE' ? `/course-enrollments/${enrollmentId}` : `/program-enrollments/${enrollmentId}`;
}

export function useRegularCertificateHolders(items) {
  const enrollmentItems = useMemo(() => {
    const unique = new Map();
    (items ?? []).forEach((item) => unique.set(`${item.kind}:${item.enrollmentId}`, item));
    return [...unique.values()];
  }, [items]);

  const enrollments = useQueries({
    queries: enrollmentItems.map((item) => ({
      queryKey: ['regular-certificate-holders', 'enrollment', item.kind, item.enrollmentId],
      queryFn: () => apiFetch(enrollmentPath(item.kind, item.enrollmentId)),
      staleTime: LOOKUP_STALE_MS,
    })),
  });

  const enrollmentByKey = new Map();
  enrollmentItems.forEach((item, index) => {
    if (enrollments[index].data) enrollmentByKey.set(`${item.kind}:${item.enrollmentId}`, enrollments[index].data);
  });

  const studentIds = [...new Set([...enrollmentByKey.values()].map((enrollment) => enrollment.studentId))];
  const students = useQueries({
    queries: studentIds.map((studentId) => ({
      queryKey: ['regular-certificate-holders', 'student', studentId],
      queryFn: () => apiFetch(`/students/${studentId}`),
      staleTime: LOOKUP_STALE_MS,
    })),
  });
  const studentById = new Map();
  studentIds.forEach((studentId, index) => {
    if (students[index].data) studentById.set(studentId, students[index].data);
  });

  return function holderOf(item) {
    const enrollment = enrollmentByKey.get(`${item.kind}:${item.enrollmentId}`);
    const student = enrollment ? studentById.get(enrollment.studentId) : null;
    return {
      studentId: enrollment?.studentId ?? item.studentId ?? null,
      studentName: student ? `${student.apellido}, ${student.nombre}` : null,
      programName: enrollment?.programName ?? enrollment?.courseName ?? null,
    };
  };
}

function useInvalidatingMutation(mutationFn) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: REQUESTS_KEY });
      queryClient.invalidateQueries({ queryKey: CERTIFICATES_KEY });
    },
  });
}

export function useIssueRegularCertificate() {
  return useInvalidatingMutation(({ kind, enrollmentId, standingEvidence }) =>
    apiFetch('/regular-certificates', {
      method: 'POST',
      body: JSON.stringify({ kind, enrollmentId, standingConfirmed: true, standingEvidence: standingEvidence ?? '' }),
    }),
  );
}

export function useRejectRegularRequest() {
  return useInvalidatingMutation(({ requestId, reason }) =>
    apiFetch(`/regular-certificate-requests/${requestId}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
  );
}

export function useRevokeRegularCertificate() {
  return useInvalidatingMutation(({ certificateId, reason }) =>
    apiFetch(`/regular-certificates/${certificateId}/revoke`, { method: 'POST', body: JSON.stringify({ reason }) }),
  );
}
