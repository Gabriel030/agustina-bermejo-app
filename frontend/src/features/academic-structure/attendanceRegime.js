const EXAMPLE_CLASSES = 40;

export const EMPTY_ATTENDANCE_REGIME = { mode: 'none', percent: '' };

export function attendanceRegimeFromPercent(maxAbsencePercent) {
  return maxAbsencePercent == null
    ? EMPTY_ATTENDANCE_REGIME
    : { mode: 'limit', percent: String(maxAbsencePercent) };
}

export function evaluateAttendanceRegime(regime) {
  if (regime.mode !== 'limit') return { status: 'none', value: null };
  const raw = regime.percent.trim();
  if (raw === '') return { status: 'empty', value: null };
  const value = Number(raw);
  if (!Number.isFinite(value) || value < 0 || value > 100) return { status: 'invalid', value: null };
  if (value === 0) return { status: 'strict', value };
  if (value === 100) return { status: 'unlimited', value };
  return { status: 'ok', value };
}

export function isAttendanceRegimeValid(regime) {
  const { status } = evaluateAttendanceRegime(regime);
  return status !== 'empty' && status !== 'invalid';
}

export function attendanceRegimeToPercent(regime) {
  return evaluateAttendanceRegime(regime).value;
}

export function helperFor({ status, value }) {
  switch (status) {
    case 'empty':
      return { tone: 'bad', text: 'Ingresá un porcentaje entre 0 y 100.' };
    case 'invalid':
      return { tone: 'bad', text: 'Ingresá un valor entre 0 y 100.' };
    case 'strict':
      return { tone: 'warn', text: 'Con 0 %, cualquier inasistencia injustificada deja libre al alumno.' };
    case 'unlimited':
      return { tone: 'warn', text: 'Con 100 % nunca se marca libre a nadie. Si es lo que querés, elegí "Sin límite".' };
    default: {
      const absence = Math.floor((EXAMPLE_CLASSES * value) / 100) + 1;
      return {
        tone: 'ok',
        text: `Queda libre con más de ${value} % de inasistencias. Ejemplo: en ${EXAMPLE_CLASSES} clases dictadas, desde la inasistencia número ${absence}.`,
      };
    }
  }
}
