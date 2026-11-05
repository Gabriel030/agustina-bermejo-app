const RATE_EPSILON = 1e-9;

export function permittedAbsences(entry) {
  if (entry.remainingAbsences == null) return null;
  if (entry.maxAbsenceRate != null && entry.classesHeld > 0) {
    return Math.floor((entry.maxAbsenceRate * entry.classesHeld) / 100 + RATE_EPSILON);
  }
  return entry.absenceCount + entry.remainingAbsences;
}

function absencesLabel(count) {
  return `${count} ${count === 1 ? 'falta' : 'faltas'}`;
}

export function attendanceRisk(entry, absences = entry.absenceCount) {
  const permitted = permittedAbsences(entry);
  if (permitted == null) {
    return { permitted: null, level: 'ok', text: absencesLabel(absences), pill: null, hint: null };
  }
  const text = `${absences} de ${permitted} faltas permitidas`;
  const remaining = permitted - absences;
  if (remaining < 0) {
    return {
      permitted,
      level: 'danger',
      text,
      pill: 'Superó el máximo',
      hint: 'Superó el máximo de inasistencias de la comisión.',
    };
  }
  if (remaining === 0) {
    return {
      permitted,
      level: 'warning',
      text,
      pill: 'Al límite',
      hint: 'Está en el límite: una falta más supera el máximo.',
    };
  }
  if (remaining === 1) {
    return {
      permitted,
      level: 'warning',
      text,
      pill: 'Queda 1 falta',
      hint: 'Le queda 1 falta antes de superar el máximo.',
    };
  }
  return { permitted, level: 'ok', text, pill: null, hint: null };
}
