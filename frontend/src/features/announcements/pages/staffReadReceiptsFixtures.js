export const PROGRAM_CYCLE = {
  id: 15,
  status: 'APPROVED',
  termId: 7,
  termName: '2.º cuatr. 2026',
  programId: 6,
  programName: 'Tecnicatura en Gestión Cultural',
  courseId: null,
  courseName: null,
  createdByName: 'Paula Roldán',
  createdAt: '2026-08-01T10:00:00',
  decidedAt: null,
  decidedByName: null,
  rejectionReason: null,
};

export const SECTIONS = [
  {
    id: 31,
    name: 'Com. B',
    subjectName: 'Historia del Arte',
    capacity: 40,
    status: 'ACTIVE',
    pendingApproval: false,
    lastRejectionReason: null,
    instructors: [{ name: 'Peña', role: 'LEAD' }],
    slots: [{ dayOfWeek: 'TUESDAY', startTime: '18:30:00', endTime: '20:30:00', classroomId: 4, classroomName: 'Aula 2' }],
  },
];

function announcement(overrides) {
  return {
    audienceLabel: 'Toda la comisión',
    body: 'Detalle',
    category: 'REMINDER',
    createdAt: '2026-11-03T14:00:00',
    createdBy: 'Peña',
    expiresAt: null,
    id: 1,
    readCount: 0,
    readTracked: true,
    recipientCount: 22,
    scheduledFor: null,
    sectionId: 31,
    sectionType: 'PROGRAM',
    sentAt: '2026-11-03T14:20:00',
    status: 'SENT',
    title: 'Aviso',
    updatedAt: null,
    urgent: false,
    ...overrides,
  };
}

export const ANNOUNCEMENTS = [
  announcement({ id: 1, title: 'Cambio de aula para el parcial', urgent: true, readCount: 18 }),
  announcement({ id: 2, title: 'Material de la unidad 3', readCount: 22 }),
  announcement({ id: 3, title: 'Visita al museo', status: 'SCHEDULED', sentAt: null, scheduledFor: '2026-11-12T09:00:00', readCount: 0, recipientCount: 0 }),
  announcement({ id: 4, title: 'Programa de la materia', readTracked: false, readCount: 0, recipientCount: 0 }),
];

function recipient(studentId, name, readAt) {
  return { studentId, name, file: `A-${2300 + studentId}`, readAt };
}

export const RECIPIENTS = [
  recipient(1, 'Acosta, Joaquín', null),
  recipient(2, 'Benítez, Lucía', '2026-11-03T15:02:00'),
  recipient(3, 'Cabrera, Julieta', null),
];

export function manyRecipients(total) {
  return Array.from({ length: total }, (_, index) =>
    recipient(index + 1, `Alumno ${String(index + 1).padStart(2, '0')}, Test`, null),
  );
}
