import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

// Épica 2 Fase 1 (Alumno · Inicio) — autoservicio del Alumno, independiente de
// useCatalogo.js (que es Autoservicio de alta/inscripción, otra épica).

export function usePaymentStanding() {
  return useQuery({ queryKey: ['me', 'payment-standing'], queryFn: () => apiFetch('/me/payment-standing') });
}

export function useDebtSummary() {
  return useQuery({ queryKey: ['me', 'invoices', 'debt-summary'], queryFn: () => apiFetch('/me/invoices/debt-summary') });
}

export function useMisCursos() {
  return useQuery({ queryKey: ['me', 'course-enrollments'], queryFn: () => apiFetch('/me/course-enrollments') });
}
