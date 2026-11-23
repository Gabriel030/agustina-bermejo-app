import { FileText, CalendarCheck, CircleCheck, Award } from 'lucide-react';

export const RECIPIENT_MAX_LENGTH = 80;

export const PROCEDURES = [
  {
    type: 'REGULAR_STUDENT_CERTIFICATE',
    title: 'Constancia de alumno regular',
    description: 'Acredita que sos alumno regular de tu carrera.',
    icon: FileText,
    includes: ['Tu nombre y DNI', 'La carrera y el plan de estudios', 'Un código y un QR para verificarla'],
    validity: 'Vale 30 días desde que la generás.',
    filename: 'constancia-alumno-regular.pdf',
    hasRecipient: true,
    hasExamPicker: false,
  },
  {
    type: 'EXAM_REGISTRATION_CERTIFICATE',
    title: 'Constancia de inscripción a examen',
    description: 'Acredita que estás inscripto a una mesa de examen.',
    icon: CalendarCheck,
    includes: ['La materia, la fecha, la hora y el lugar de la mesa', 'Un código y un QR para verificarla'],
    validity: 'Vale hasta el día del examen.',
    filename: 'constancia-inscripcion-examen.pdf',
    hasRecipient: true,
    hasExamPicker: false,
  },
  {
    type: 'EXAM_CERTIFICATE',
    title: 'Constancia de examen',
    description: 'Acredita que te presentaste a rendir un examen.',
    icon: CircleCheck,
    includes: ['La materia, la fecha y la mesa', 'Que estuviste presente, sin la nota', 'Un código y un QR para verificarla'],
    validity: 'Vale 30 días desde que la generás.',
    filename: 'constancia-examen.pdf',
    hasRecipient: true,
    hasExamPicker: true,
  },
  {
    type: 'APPROVED_SUBJECTS_CERTIFICATE',
    title: 'Constancia de materias aprobadas',
    description: 'Tus materias aprobadas con nota, promedio y avance.',
    icon: Award,
    includes: [
      'Tus materias aprobadas con tipo, cursada y nota',
      'Tu promedio general y el avance del programa',
      'Un código y un QR para verificarla',
    ],
    validity: 'Vale 30 días desde que la generás.',
    filename: 'constancia-materias-aprobadas.pdf',
    hasRecipient: false,
    hasExamPicker: false,
  },
];

export const RECIPIENT_OPTIONS = [
  { type: 'HEALTH_INSURANCE', label: 'Obra social', fieldLabel: 'Nombre de la obra social' },
  { type: 'EMPLOYER', label: 'Trabajo', fieldLabel: 'Nombre de la empresa' },
  { type: 'STUDENT_TRANSPORT', label: 'Boleto estudiantil', fieldLabel: 'Empresa de transporte o entidad' },
  { type: 'OTHER', label: 'Otro', fieldLabel: '¿Para quién es?' },
];

export const procedureOf = (type) => PROCEDURES.find((procedure) => procedure.type === type);

const INFORMATIVE_REASONS = new Set([
  'NO_OPEN_ENROLLMENT_WINDOW',
  'NO_SUBJECTS_IN_PROGRESS',
  'NOT_REGISTERED_FOR_EXAM',
  'EXAM_NOT_HELD_YET',
  'EXAM_ATTENDANCE_NOT_RECORDED',
  'ISSUANCE_LIMIT_REACHED',
]);

export const isInformativeReason = (reason) => INFORMATIVE_REASONS.has(reason.code);

export function reasonActionOf(reason, { carreraId, billingEnabled }) {
  switch (reason.nextStep?.kind) {
    case 'GO_TO_PAYMENTS':
      return billingEnabled ? { label: 'Ir a Mis pagos', to: '/alumno/pagos' } : null;
    case 'REGISTER_FOR_EXAM':
      return { label: 'Ver mesas de examen', to: `/alumno/mis-carreras?carrera=${carreraId}&tab=mesas-de-examen` };
    case 'COMPLETE_PENDING_SUBJECTS':
      return { label: 'Ver plan de estudio', to: `/alumno/mis-carreras?carrera=${carreraId}&tab=plan-de-estudio` };
    case 'TRY_AGAIN_LATER':
      return reason.code === 'ISSUANCE_LIMIT_REACHED' ? { label: 'Ver Trámites aprobados', section: 'approved' } : null;
    default:
      return null;
  }
}

export function sanitizeRecipientText(value) {
  return value.replace(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/gu, '').slice(0, RECIPIENT_MAX_LENGTH);
}

export function buildDocumentBody({ carreraId, recipientType, recipientText, registrationId }) {
  const text = recipientType ? sanitizeRecipientText(recipientText ?? '').trim() : '';
  return {
    enrollmentId: carreraId,
    recipientType: recipientType ?? null,
    recipientText: text === '' ? null : text,
    registrationId: registrationId ?? null,
  };
}

export function generateErrorMessage(error) {
  if (error?.name === 'RateLimitedError' || error?.status === 429 || error?.code === 'ISSUANCE_LIMIT_REACHED') {
    return 'Ya generaste hoy esta constancia. Si sigue vigente, podés volver a descargarla desde Trámites aprobados.';
  }
  if (error?.code === 'DOCUMENT_REQUIRES_PAYMENTS_UP_TO_DATE') {
    return 'Por ahora no podés descargar constancias. Comunicate con Administración.';
  }
  return null;
}

export function examOptionsOf(registrations, carreraId, now) {
  return (registrations ?? [])
    .filter(
      (registration) =>
        registration.enrollmentId === carreraId &&
        registration.status !== 'WITHDRAWN' &&
        registration.examBoard?.status !== 'CANCELED' &&
        new Date(registration.examBoard.examDate) <= now,
    )
    .sort((a, b) => new Date(b.examBoard.examDate) - new Date(a.examBoard.examDate));
}

export const APPROVED_STATUS_PILL = {
  VALID: { tone: 'success', label: 'Vigente' },
  EXPIRED: { tone: 'neutral', label: 'Vencida' },
  REVOKED: { tone: 'danger', label: 'Revocada' },
  REPLACED: { tone: 'neutral', label: 'Reemplazada' },
};

export const isRegeneratable = (item) =>
  item.status === 'EXPIRED' && item.origin === 'SELF_SERVICE' && item.documentType !== 'GRADUATION_CERTIFICATE';
