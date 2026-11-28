export function attendanceChangeRequest(overrides = {}) {
  return {
    id: 41,
    attendanceId: 901,
    classDate: '2026-11-04',
    createdAt: '2026-11-05T09:30:00',
    previousStatus: 'ABSENT',
    requestedStatus: 'PRESENT',
    reason: 'Rindió el parcial recuperatorio ese día.',
    rejectionReason: null,
    requestedByName: 'Marina Fernández',
    sectionName: 'Comisión A',
    status: 'PENDING',
    studentName: 'Valentina Suárez',
    subjectName: 'Programación II',
    decidedAt: null,
    decidedByName: null,
    revertibleUntil: null,
    revertible: false,
    revertedByName: null,
    revertedAt: null,
    revertReason: null,
    ...overrides,
  };
}

export const REQUESTS_FIXTURE = [
  attendanceChangeRequest(),
  attendanceChangeRequest({
    id: 40,
    attendanceId: 902,
    classDate: '2026-10-30',
    createdAt: '2026-11-03T10:00:00',
    previousStatus: 'ABSENT',
    requestedStatus: 'LATE',
    reason: 'Llegó 20 minutos tarde.',
    rejectionReason: 'La asistencia ya fue cerrada en el acta del 31/10.',
    sectionName: 'Comisión B',
    status: 'REJECTED',
    studentName: 'Joaquín Benítez',
    subjectName: 'Bases de Datos',
  }),
  attendanceChangeRequest({
    id: 37,
    attendanceId: 903,
    classDate: '2026-10-23',
    createdAt: '2026-10-27T10:00:00',
    reason: 'Estuvo presente en la clase de consulta.',
    rejectionReason: null,
    sectionName: 'Comisión B',
    status: 'REJECTED',
    studentName: 'Lucía Herrera',
    subjectName: 'Bases de Datos',
  }),
  attendanceChangeRequest({
    id: 39,
    attendanceId: 904,
    classDate: '2026-10-29',
    createdAt: '2026-11-03T08:00:00',
    previousStatus: 'LATE',
    requestedStatus: 'PRESENT',
    reason: 'Llegó en horario.',
    status: 'APPROVED',
    studentName: 'Camila Ferreyra',
  }),
];

export function resolvedAttendanceChangeRequest(overrides = {}) {
  return attendanceChangeRequest({
    id: 39,
    status: 'APPROVED',
    decidedAt: '2026-11-24T10:15:00',
    decidedByName: 'Laura Gómez',
    revertibleUntil: '2026-12-01',
    revertible: true,
    ...overrides,
  });
}

export function resolvedAttendanceChangeRequestsPage(items = [resolvedAttendanceChangeRequest()], overrides = {}) {
  return { items, page: 0, size: 20, totalElements: items.length, ...overrides };
}
