export const OFFER_APPROVALS_PATH = '/director/aprobaciones/oferta-academica';

const DAY_LABELS = {
  MONDAY: 'Lun',
  TUESDAY: 'Mar',
  WEDNESDAY: 'Mié',
  THURSDAY: 'Jue',
  FRIDAY: 'Vie',
  SATURDAY: 'Sáb',
  SUNDAY: 'Dom',
};

const DAY_ORDER = Object.keys(DAY_LABELS);

export function cycleTargetName(cycle) {
  return cycle.programName ?? cycle.courseName ?? '—';
}

export function cycleTargetKind(cycle) {
  return cycle.programId ? 'Carrera' : 'Curso';
}

const SHIFT_LABELS = { MORNING: 'Mañana', AFTERNOON: 'Tarde', EVENING: 'Noche' };

export function shiftLabel(shift) {
  return SHIFT_LABELS[shift] ?? 'Sin turno';
}

function timeOf(value) {
  return value?.slice(0, 5) ?? '';
}

export function slotLabel(slot) {
  return `${DAY_LABELS[slot.dayOfWeek] ?? ''} ${timeOf(slot.startTime)}–${timeOf(slot.endTime)} · ${slot.classroomName}`;
}

export function sortedSlots(slots) {
  return [...(slots ?? [])].sort(
    (a, b) => DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) || timeOf(a.startTime).localeCompare(timeOf(b.startTime)),
  );
}

export function leadInstructorName(section) {
  const lead = section.instructors?.find((instructor) => instructor.role === 'LEAD') ?? section.instructors?.[0];
  return lead?.name ?? 'Sin docente';
}

function sideLabel(side) {
  return `${side.subjectName} · ${side.sectionName}`;
}

export function conflictTitle(conflict) {
  const when = `${DAY_LABELS[conflict.dayOfWeek] ?? ''} de ${timeOf(conflict.startTime)} a ${timeOf(conflict.endTime)}`;
  if (conflict.type === 'INSTRUCTOR') {
    return `Docente ${conflict.instructorName} · ${when}`;
  }
  return `${conflict.classroomName} · ${when}`;
}

export function conflictDetail(conflict) {
  const own = sideLabel(conflict.ownSection);
  const other = sideLabel(conflict.otherSection);
  return conflict.type === 'INSTRUCTOR'
    ? `${own} y ${other} tienen al mismo docente a la vez.`
    : `${own} y ${other} usan la misma aula a la vez.`;
}

export function conflictedSectionIds(conflicts) {
  return new Set((conflicts ?? []).map((conflict) => conflict.ownSection.sectionId));
}

export function approvalSummary(count) {
  return count === 1 ? 'Se activa 1 comisión' : `Se activan ${count} comisiones`;
}
