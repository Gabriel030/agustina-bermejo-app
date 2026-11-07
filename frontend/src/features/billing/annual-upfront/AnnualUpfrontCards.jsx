import { useQueries } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';
import { isAdmissionInvoiceActionable } from '@/lib/admissionInvoice';
import { useAdmissionInvoicesPending } from '../hooks/useAdmissionInvoicesPending';
import { AnnualUpfrontCard } from './AnnualUpfrontCard';
import { isAnnualUpfrontActionable } from './annualUpfront';

export function AnnualUpfrontCards({ enrollments }) {
  const { pendientes } = useAdmissionInvoicesPending();
  const quotes = useQueries({
    queries: enrollments.map((enrollment) => ({
      queryKey: ['annual-upfront', 'student', enrollment.id, 'quote'],
      queryFn: () => apiFetch(`/me/program-enrollments/${enrollment.id}/annual-upfront/quote`),
    })),
  });
  const requests = useQueries({
    queries: enrollments.map((enrollment) => ({
      queryKey: ['annual-upfront', 'student', enrollment.id, 'requests'],
      queryFn: () => apiFetch(`/me/program-enrollments/${enrollment.id}/annual-upfront-requests`),
    })),
  });

  const admissionActionable = (pendientes ?? []).some(isAdmissionInvoiceActionable);
  const firstActionable = admissionActionable
    ? -1
    : enrollments.findIndex((_, index) => isAnnualUpfrontActionable(quotes[index]?.data, requests[index]?.data));

  return enrollments.map((enrollment, index) => (
    <AnnualUpfrontCard
      key={enrollment.id}
      enrollmentId={enrollment.id}
      programName={enrollment.programName ?? `Carrera #${enrollment.programId}`}
      primary={index === firstActionable}
    />
  ));
}
