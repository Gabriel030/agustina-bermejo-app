const AVAILABLE = 'AVAILABLE';
const OVERDUE_DEBT = 'ADVANCE_FEE_OVERDUE_DEBT';
const WINDOW_NOT_OPEN = 'ADVANCE_FEE_WINDOW_NOT_OPEN';
const ALREADY_ISSUED = 'ADVANCE_FEE_ALREADY_ISSUED';

export const ADVANCE_ERRORS = {
  ADVANCE_FEE_OVERDUE_DEBT: 'Hay cuotas vencidas: regularizalas antes de generar la matrícula.',
  ADVANCE_FEE_WINDOW_NOT_OPEN: 'Todavía no se puede pagar la matrícula del año siguiente.',
  ADVANCE_FEE_WINDOW_CLOSED: 'Ya pasó el plazo para pagar la matrícula del año siguiente.',
  ADVANCE_FEE_NEXT_YEAR_NOT_CONFIGURED: 'La matrícula del año siguiente todavía no está disponible.',
  ADVANCE_FEE_ENROLLMENT_NOT_ACTIVE: 'La inscripción no está activa.',
  ADVANCE_FEE_ALREADY_ISSUED: 'La matrícula del año siguiente ya fue generada.',
};

export function advanceOf(obligations) {
  return (obligations ?? []).find((obligation) => obligation.advance)?.advance ?? null;
}

export function advanceYearOf(advance) {
  return advance?.dueDate ? Number(String(advance.dueDate).slice(0, 4)) : null;
}

export function advanceKindOf(advance) {
  if (!advance) return 'hidden';
  if (advance.available && advance.reasonCode === AVAILABLE) return 'available';
  if (advance.reasonCode === OVERDUE_DEBT) return 'debt';
  if (advance.reasonCode === WINDOW_NOT_OPEN) return 'early';
  if (advance.reasonCode === ALREADY_ISSUED) return 'issued';
  return 'hidden';
}

export function formatLongDate(isoDate) {
  if (!isoDate) return '';
  const [year, month, day] = String(isoDate).slice(0, 10).split('-').map(Number);
  return new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(year, month - 1, day));
}

export function advanceErrorMessage(error) {
  const codes = [error?.code, ...(error?.violations ?? []).map((violation) => violation.code)].filter(Boolean);
  const known = codes.find((code) => ADVANCE_ERRORS[code]);
  return known ? ADVANCE_ERRORS[known] : (error?.message ?? 'No pudimos generar la matrícula. Probá de nuevo.');
}
