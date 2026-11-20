function absencesLabel(count) {
  return `${count} ${count === 1 ? 'inasistencia' : 'inasistencias'}`;
}

export function attendanceRisk(entry) {
  const standing = entry.absenceStanding;
  const permitted = standing.allowedAbsences;
  if (permitted == null) {
    return { permitted: null, level: 'ok', text: absencesLabel(standing.countableAbsences), pill: null, hint: null };
  }
  const text = `${standing.countableAbsences} de ${permitted} ${permitted === 1 ? 'inasistencia permitida' : 'inasistencias permitidas'}`;
  if (standing.exceedsAbsenceLimit) {
    return {
      permitted,
      level: 'danger',
      text,
      pill: 'Superó el máximo',
      hint: 'Superó el máximo de inasistencias de la comisión.',
    };
  }
  if (standing.remainingAbsences === 0) {
    return {
      permitted,
      level: 'warning',
      text,
      pill: 'Al límite',
      hint: 'Está en el límite: una inasistencia más supera el máximo.',
    };
  }
  if (standing.remainingAbsences === 1) {
    return {
      permitted,
      level: 'warning',
      text,
      pill: 'Queda 1 inasistencia',
      hint: 'Le queda 1 inasistencia antes de superar el máximo.',
    };
  }
  return { permitted, level: 'ok', text, pill: null, hint: null };
}
