import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

export function useLegajoAlumno(studentId) {
  return useQuery({
    queryKey: ['alumno', studentId],
    queryFn: () => apiFetch(`/students/${studentId}`),
    enabled: !!studentId,
  });
}

export function useDocumentosAlumno(studentId) {
  return useQuery({
    queryKey: ['alumno-documentos', studentId],
    queryFn: () => apiFetch(`/students/${studentId}/documents`),
    enabled: !!studentId,
  });
}
