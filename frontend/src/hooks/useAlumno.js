import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from '@/api/client';

// Épica 2 Fase 1 (Alumno · Inicio) — autoservicio del Alumno, independiente de
// useCatalogo.js (que es Autoservicio de alta/inscripción, otra épica).

export function usePaymentStanding() {
  return useQuery({
    queryKey: ['me', 'billing-standing'],
    queryFn: () => apiFetch('/me/billing-standing'),
    select: (entries) => ({
      standing: entries.some((e) => e.billingStanding !== 'UP_TO_DATE') ? 'MOROSO' : 'AL_DIA',
    }),
  });
}

export function useMisCursos(enabled = true) {
  return useQuery({ queryKey: ['me', 'course-enrollments'], queryFn: () => apiFetch('/me/course-enrollments'), enabled });
}

// Épica 2 Fase 1 (Alumno · Mis carreras).
export function useMisMatriculaciones(enabled = true) {
  return useQuery({ queryKey: ['me', 'program-enrollments'], queryFn: () => apiFetch('/me/program-enrollments'), enabled });
}

export function useMiProgreso(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'progress'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/progress`),
    enabled: !!programEnrollmentId,
  });
}

// Todas las inscripciones a comisión del alumno, across todas sus carreras — la
// pantalla filtra client-side por la carrera seleccionada (programEnrollmentId).
// `enabled` (default true) existe para useBreadcrumb.jsx: ese hook corre dentro
// de RoleShell para CUALQUIER rol, así que sin poder desactivarlo terminaba
// pegándole a este endpoint solo-Alumno incluso logueado como Preceptor/Docente/etc.
export function useMisInscripciones(enabled = true) {
  return useQuery({ queryKey: ['me', 'section-enrollments'], queryFn: () => apiFetch('/me/section-enrollments'), enabled });
}

// Detalle de UNA inscripción a comisión puntual — se resuelve del listado de
// arriba (no hay GET /me/section-enrollments/{id} de detalle propio, mismo
// criterio que DetalleSolicitud.jsx con las solicitudes de carrera).
export function useMiInscripcion(sectionEnrollmentId, enabled = true) {
  const { data: inscripciones, ...resto } = useMisInscripciones(enabled);
  const inscripcion = inscripciones?.find((i) => String(i.id) === String(sectionEnrollmentId));
  return { ...resto, data: inscripcion };
}

export function useNotasDeMateria(sectionEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'section-enrollments', sectionEnrollmentId, 'grade-items'],
    queryFn: () => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/grade-items`),
    enabled: !!sectionEnrollmentId,
  });
}

export function useDarmeDeBajaDeMateria() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sectionEnrollmentId) => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/withdraw`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments'] }),
  });
}

export function useInscribirmeAMateria() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ programEnrollmentId, sectionId }) =>
      apiFetch('/me/section-enrollments', { method: 'POST', body: JSON.stringify({ programEnrollmentId, sectionId }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'section-enrollments'] }),
  });
}

// Comisiones disponibles de una materia (no es /me — endpoint público para
// cualquier autenticado, ver SubjectSectionController) y el período (Term) al
// que pertenecen, para saber si la inscripción está abierta.
export function useSeccionesDeMateria(subjectId) {
  return useQuery({
    queryKey: ['subjects', subjectId, 'sections'],
    queryFn: () => apiFetch(`/subjects/${subjectId}/sections?status=ACTIVE`),
    enabled: !!subjectId,
  });
}

export function useTerm(termId) {
  return useQuery({ queryKey: ['terms', termId], queryFn: () => apiFetch(`/terms/${termId}`), enabled: !!termId });
}

export function useMiAsistencia(sectionEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'section-enrollments', sectionEnrollmentId, 'attendance'],
    queryFn: () => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/attendance`),
    enabled: !!sectionEnrollmentId,
  });
}

// % de ausentismo real y si ya supera el máximo permitido de la comisión (política
// institucional real, SubjectSection.maxAbsenceRate — nunca un 70% fijo).
export function useMiRiesgoDeAsistencia(sectionEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'section-enrollments', sectionEnrollmentId, 'attendance', 'risk'],
    queryFn: () => apiFetch(`/me/section-enrollments/${sectionEnrollmentId}/attendance/risk`),
    enabled: !!sectionEnrollmentId,
  });
}

// Mesas SCHEDULED de una materia puntual — solo tiene sentido pedirlo para una
// materia donde el alumno está Regular (el chequeo real de elegibilidad lo
// hace el backend al inscribirse, esto es solo el listado para elegir llamado).
export function useMesasDisponibles(subjectId) {
  return useQuery({
    queryKey: ['me', 'exam-boards', subjectId],
    queryFn: () => apiFetch(`/me/exam-boards?subjectId=${subjectId}`),
    enabled: !!subjectId,
  });
}

// Mis inscripciones a mesas (todas las materias, pasadas y presentes) — cada una
// ya trae la mesa completa (fecha/aula) y la nota si el alumno ya rindió.
export function useMisInscripcionesAMesas() {
  return useQuery({ queryKey: ['me', 'exam-board-registrations'], queryFn: () => apiFetch('/me/exam-board-registrations') });
}

export function useInscribirmeAMesa() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ examBoardId, programSectionEnrollmentId }) =>
      apiFetch('/me/exam-board-registrations', { method: 'POST', body: JSON.stringify({ examBoardId, programSectionEnrollmentId }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'exam-board-registrations'] }),
  });
}

export function useDarmeDeBajaDeMesa() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (registrationId) => apiFetch(`/me/exam-board-registrations/${registrationId}/withdraw`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me', 'exam-board-registrations'] }),
  });
}

// Justificar una inasistencia propia con un comprobante (solo PDF, validado en el
// backend) — antes esto era exclusivo de Preceptor/Administrativo.
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

export function useEstadoMatricula(programEnrollmentId) {
  return useQuery({
    queryKey: ['me', 'program-enrollments', programEnrollmentId, 'enrollment-fee-status'],
    queryFn: () => apiFetch(`/me/program-enrollments/${programEnrollmentId}/enrollment-fee-status`),
    enabled: !!programEnrollmentId,
  });
}
