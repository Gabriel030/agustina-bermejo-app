export function formatDayKey(dayKey) {
  if (!dayKey) return '';
  const [year, month, day] = dayKey.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

const REGISTERED = 'Queda registrado quién la revirtió y por qué.';

export function revertDialogCopy({ kind, result, studentName, previousStatusLabel }) {
  const justification = kind === 'justification';
  if (result === 'APPROVED') {
    return {
      title: justification ? 'Revertir justificación aprobada' : 'Revertir aprobación',
      description: justification
        ? `La inasistencia de ${studentName} vuelve a «Ausente» y la justificación vuelve a Pendientes. ${REGISTERED}`
        : `La asistencia de ${studentName} vuelve a «${previousStatusLabel}» y la solicitud vuelve a Pendientes. ${REGISTERED}`,
      placeholder: 'Explicá por qué se revierte la aprobación',
    };
  }
  return {
    title: justification ? 'Revertir rechazo de justificación' : 'Revertir rechazo',
    description: justification
      ? `La justificación vuelve a Pendientes para que la decidas de nuevo. La inasistencia sigue como Ausente. ${REGISTERED}`
      : `La solicitud vuelve a Pendientes para que la decidas de nuevo. La asistencia no cambia. ${REGISTERED}`,
    placeholder: 'Explicá por qué se revierte el rechazo',
  };
}
