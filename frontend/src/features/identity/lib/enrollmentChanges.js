export const REASON_MIN_LENGTH = 10;
export const REASON_MAX_LENGTH = 500;

const ACTIVE_SECTION_STATUSES = ['ENROLLED', 'IN_PROGRESS', 'WAITLISTED'];
const ACTIVE_ENROLLMENT_STATUSES = ['ENROLLED', 'IN_PROGRESS', 'PAUSED'];

export function isSectionActionable(status) {
  return ACTIVE_SECTION_STATUSES.includes(status);
}

export function isEnrollmentActionable(status) {
  return ACTIVE_ENROLLMENT_STATUSES.includes(status);
}

export function permissionsFor(roleBase) {
  const isStaff = ['preceptor', 'administrativo', 'director'].includes(roleBase);
  const canWithdrawWhole = roleBase === 'administrativo' || roleBase === 'director';
  return {
    canWithdrawSubject: isStaff,
    canChangeSection: isStaff,
    canWithdrawModule: isStaff,
    canWithdrawProgram: canWithdrawWhole,
    canWithdrawCourse: canWithdrawWhole,
  };
}

export function sectionActionsOf(row, permissions) {
  if (!isSectionActionable(row.status)) return [];
  const waitlisted = row.status === 'WAITLISTED';
  const actions = [];
  if (permissions.canChangeSection) {
    actions.push({
      kind: 'change-section',
      label: 'Cambiar de comisión',
      disabledReason: waitlisted ? 'Está en lista de espera: no se puede cambiar de comisión.' : null,
    });
  }
  if (permissions.canWithdrawSubject) {
    actions.push({ kind: 'withdraw-subject', label: 'Dar de baja de la materia', destructive: true, disabledReason: null });
  }
  return actions;
}

export function hasAnyAction(rows, permissions) {
  return rows.some((row) => sectionActionsOf(row, permissions).length > 0);
}

export function normalizeConfirmation(text) {
  return String(text ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .trim()
    .toLowerCase();
}

export function matchesConfirmation(typed, expected) {
  return normalizeConfirmation(typed) === normalizeConfirmation(expected);
}

export function reasonError(reason) {
  const length = String(reason ?? '').trim().length;
  if (length === 0) return 'Indicá el motivo: es obligatorio y queda registrado.';
  if (length < REASON_MIN_LENGTH) return `El motivo debe tener al menos ${REASON_MIN_LENGTH} caracteres.`;
  return null;
}

function toIsoDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function withdrawalDateRange(enrollmentDate, today = new Date()) {
  const firstOfPreviousMonth = toIsoDate(new Date(today.getFullYear(), today.getMonth() - 1, 1));
  const enrolled = enrollmentDate ? String(enrollmentDate).slice(0, 10) : null;
  const min = enrolled && enrolled > firstOfPreviousMonth ? enrolled : firstOfPreviousMonth;
  return { min, max: toIsoDate(today) };
}

export function withdrawalDateError(value, range) {
  if (!value) return null;
  if (value < range.min || value > range.max) return 'La fecha de baja está fuera del rango permitido.';
  return null;
}

export const BLOCK_REASON_LABELS = {
  CURRENT_SECTION: 'Es su comisión actual',
  SECTION_NOT_OPEN: 'La inscripción a esta comisión no está abierta',
  OTHER_TERM: 'Es de otro período',
  SECTION_FULL: 'Sin cupo',
  SCHEDULE_OVERLAP: 'Se superpone con otra cursada',
};

export function describeBlockReason(candidate) {
  return candidate.blockDetail ?? BLOCK_REASON_LABELS[candidate.blockReason] ?? 'No disponible';
}

const DAY_LABELS = {
  MONDAY: 'lunes',
  TUESDAY: 'martes',
  WEDNESDAY: 'miércoles',
  THURSDAY: 'jueves',
  FRIDAY: 'viernes',
  SATURDAY: 'sábado',
  SUNDAY: 'domingo',
};

function shortTime(time) {
  return String(time ?? '').slice(0, 5);
}

export function describeSlot(slot) {
  const day = DAY_LABELS[slot.dayOfWeek] ?? slot.dayOfWeek;
  const place = slot.classroomName ? ` · ${slot.classroomName}` : '';
  return `${day} ${shortTime(slot.startTime)} a ${shortTime(slot.endTime)}${place}`;
}

export function describeSeats(candidate) {
  if (candidate.availableSeats == null) return 'Sin tope de cupo';
  if (candidate.availableSeats === 0) return 'Sin cupo';
  return candidate.availableSeats === 1 ? '1 lugar libre' : `${candidate.availableSeats} lugares libres`;
}

export function splitCandidates(candidates = []) {
  return {
    available: candidates.filter((candidate) => candidate.selectable),
    unavailable: candidates.filter((candidate) => !candidate.selectable),
  };
}

export function buildChangeRows(current, target) {
  const seats = describeSeats(target);
  const rows = [
    {
      key: 'section',
      label: 'Comisión',
      today: `Com. ${current.sectionName}`,
      next: `Com. ${target.name}`,
      difference: current.sectionName === target.name ? 'Sin cambios' : 'Cambia',
    },
    {
      key: 'schedule',
      label: 'Horario',
      today: current.schedule || '—',
      next: target.slots?.length ? target.slots.map(describeSlot).join(' · ') : 'Sin horario cargado',
      difference: 'Cambia',
    },
    {
      key: 'seats',
      label: 'Cupo de la nueva comisión',
      today: '—',
      next: seats,
      difference: '—',
    },
    {
      key: 'attendance',
      label: 'Asistencia y notas',
      today: 'Se conservan',
      next: 'Se conservan',
      difference: 'Sin cambios',
      unchanged: true,
    },
  ];
  return rows;
}

export const INLINE_ERROR_CODES = [
  'ENROLLMENT_CHANGE_REASON_REQUIRED',
  'ENROLLMENT_CHANGE_REASON_INVALID',
  'SECTION_SCHEDULE_OVERLAP',
  'SECTION_FULL',
  'VALIDATION_FAILED',
  'WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE',
];

export function isInlineError(error) {
  return INLINE_ERROR_CODES.includes(error?.code);
}

export function describeOverlap(error) {
  const details = error?.details;
  if (error?.code !== 'SECTION_SCHEDULE_OVERLAP' || !details?.conflictingSubject) return null;
  return `Se superpone con ${details.conflictingSubject} (${details.day} de ${details.from} a ${details.to}).`;
}
