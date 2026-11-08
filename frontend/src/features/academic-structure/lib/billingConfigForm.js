import { MESES } from './aniosLectivos';

const FORM_LABELS = {
  enrollmentFeeCount: 'Cantidad de matrículas',
  firstEnrollmentFeeMonth: 'Mes de la matrícula',
  secondEnrollmentFeeMonth: 'Mes de la segunda matrícula',
  enrollmentFeeLeadMonths: 'Meses de anticipación a la inscripción',
  advanceEnrollmentFeeOpensOn: 'Matrícula anticipada · Abre',
  lateEnrollmentFeeDueDays: 'Días de vencimiento fuera de ventana',
  paysWhilePaused: 'Alumnos en pausa pagan',
  blocksEnrollment: 'Bloquea inscripción a materias',
  blocksExamRegistration: 'Bloquea inscripción a mesas',
  blocksCertificates: 'Bloquea pedido de certificados',
  requireEnrollmentFeeForFinalExamOnlyStudents: 'Quienes solo rinden finales pagan matrícula',
};

export function formatIso(isoDate) {
  const [year, month, day] = isoDate.split('T')[0].split('-');
  return `${day}/${month}/${year}`;
}

export function toBillingForm(template) {
  return {
    enrollmentFeeCount: String(template.enrollmentFeeCount ?? 1),
    firstEnrollmentFeeMonth: String(template.firstEnrollmentFeeMonth ?? 2),
    secondEnrollmentFeeMonth: String(template.secondEnrollmentFeeMonth ?? 7),
    enrollmentFeeLeadMonths: String(template.enrollmentFeeLeadMonths ?? 1),
    advanceEnrollmentFeeOpensOn: template.advanceEnrollmentFeeOpensOn ?? '',
    lateEnrollmentFeeDueDays: String(template.lateEnrollmentFeeDueDays ?? 10),
    paysWhilePaused: template.paysWhilePaused ?? false,
    blocksEnrollment: template.blocksEnrollment ?? true,
    blocksExamRegistration: template.blocksExamRegistration ?? true,
    blocksCertificates: template.blocksCertificates ?? true,
    requireEnrollmentFeeForFinalExamOnlyStudents: template.requireEnrollmentFeeForFinalExamOnlyStudents ?? false,
  };
}

export function toBillingPayload(form) {
  const twoFees = form.enrollmentFeeCount === '2';
  return {
    enrollmentFeeCount: Number(form.enrollmentFeeCount),
    firstEnrollmentFeeMonth: Number(form.firstEnrollmentFeeMonth),
    ...(twoFees ? { secondEnrollmentFeeMonth: Number(form.secondEnrollmentFeeMonth) } : {}),
    enrollmentFeeLeadMonths: Number(form.enrollmentFeeLeadMonths),
    lateEnrollmentFeeDueDays: Number(form.lateEnrollmentFeeDueDays),
    paysWhilePaused: form.paysWhilePaused,
    blocksEnrollment: form.blocksEnrollment,
    blocksExamRegistration: form.blocksExamRegistration,
    blocksCertificates: form.blocksCertificates,
    requireEnrollmentFeeForFinalExamOnlyStudents: form.requireEnrollmentFeeForFinalExamOnlyStudents,
    ...(form.advanceEnrollmentFeeOpensOn ? { advanceEnrollmentFeeOpensOn: form.advanceEnrollmentFeeOpensOn } : {}),
  };
}

function displayValue(key, value) {
  if (typeof value === 'boolean') return value ? 'Sí' : 'No';
  if (key === 'firstEnrollmentFeeMonth' || key === 'secondEnrollmentFeeMonth') return MESES[Number(value) - 1];
  if (key === 'enrollmentFeeCount') return value === '2' ? '2 por año' : '1 por año';
  if (key === 'advanceEnrollmentFeeOpensOn') return value ? formatIso(value) : 'Con la inscripción';
  return value === '' ? '—' : value;
}

export function diffBillingForm(base, next) {
  return Object.keys(FORM_LABELS)
    .filter((key) => base[key] !== next[key])
    .filter((key) => key !== 'secondEnrollmentFeeMonth' || next.enrollmentFeeCount === '2' || base.enrollmentFeeCount === '2')
    .map((key) => ({ key, label: FORM_LABELS[key], today: displayValue(key, base[key]), next: displayValue(key, next[key]), difference: 'Cambia' }));
}

function addDays(isoDate, days) {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function emissionMonthOf(term, leadMonths) {
  const [year, month] = term.enrollmentStart.split('T')[0].split('-').map(Number);
  const index = month - 1 - Number(leadMonths);
  return { year: year + Math.floor(index / 12), month: (((index % 12) + 12) % 12) + 1 };
}

export function billingTimeline({ term, form }) {
  if (!term?.enrollmentStart || !term?.enrollmentEnd) return [];
  const windowStart = term.enrollmentStart.split('T')[0];
  const windowEnd = term.enrollmentEnd.split('T')[0];
  const emission = emissionMonthOf(term, form.enrollmentFeeLeadMonths);
  const advanceDate = form.advanceEnrollmentFeeOpensOn || windowStart;
  const events = [
    { key: 'advance', date: advanceDate, label: 'Se puede generar la matrícula anticipada', order: 0 },
    {
      key: 'emission',
      date: `${emission.year}-${String(emission.month).padStart(2, '0')}-01`,
      label: `Emisión regular de la matrícula (${MESES[emission.month - 1]} ${emission.year}, entre el 1 y el 5)`,
      order: 1,
    },
    { key: 'window', date: windowStart, label: 'Abre la inscripción', order: 2 },
    { key: 'due', date: windowEnd, label: 'Vence la matrícula y cierra la ventana', order: 3 },
    { key: 'expiry', date: addDays(windowEnd, 1), label: 'Se anula sola la matrícula anticipada impaga', order: 4 },
  ];
  return events.sort((a, b) => (a.date === b.date ? a.order - b.order : a.date < b.date ? -1 : 1));
}

export function advanceOpeningError(term, opensOn) {
  if (!opensOn || !term?.enrollmentEnd) return null;
  return opensOn > term.enrollmentEnd.split('T')[0] ? 'La apertura no puede ser posterior al cierre de la ventana de inscripción.' : null;
}
