export const SCHEDULE_CONFLICT_LABEL = {
  SLOT_OUTSIDE_SHIFT: 'Fuera del turno de la comisión',
  CLASSROOM_CAPACITY_EXCEEDED: 'El aula no tiene capacidad',
  CLASSROOM_OCCUPIED: 'El aula ya está ocupada',
  INSTRUCTOR_BUSY: 'El docente tiene otra clase en ese horario',
  SLOTS_OVERLAP: 'Las franjas se superponen',
};

export function scheduleConflictText(conflict) {
  const label = SCHEDULE_CONFLICT_LABEL[conflict.type] ?? 'Conflicto de horario';
  return `${label} · ${conflict.conflictingSectionLabel} · franja ${conflict.slotIndex + 1}`;
}

export function toScheduleSlotRequests(slots) {
  return slots.map((slot) => ({
    classroomId: slot.classroomId,
    dayOfWeek: slot.dayOfWeek,
    startTime: slot.startTime,
    endTime: slot.endTime,
  }));
}

export function sectionScheduleTitle(request) {
  return `${request.subjectName} · ${request.sectionName}`;
}
