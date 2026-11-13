export const CLOSURE_LIST_STATUS = {
  TEACHER_CONFIRMED: { label: 'Confirmada por el docente', tone: 'accent', dot: false },
  REOPENED: { label: 'Reabierta', tone: 'warning', dot: false },
  DRAFT_AVAILABLE: { label: 'Pendiente del docente', tone: 'neutral', dot: true },
  CLOSED: { label: 'Cerrada', tone: 'success', dot: false },
};

export const CLOSURE_STATUS_FACT = {
  CONFIRMED: 'confirmada',
  REOPENED: 'reabierta',
  CLOSED: 'cerrada',
  NOT_DELIVERED: 'sin entregar',
};

export const CLOSURE_KPIS = [
  { status: null, countKey: 'all', label: 'Todas' },
  { status: 'TEACHER_CONFIRMED', countKey: 'teacherConfirmed', label: 'Para aprobar' },
  { status: 'DRAFT_AVAILABLE', countKey: 'draftAvailable', label: 'Pendientes' },
  { status: 'CLOSED', countKey: 'closed', label: 'Cerradas' },
  { status: 'REOPENED', countKey: 'reopened', label: 'Reabiertas' },
];

export const CLOSURE_LIST_PAGE_SIZE = 10;

export function closureSheetPath(role, item) {
  return `/${role}/cierre-de-comision/${item.sectionId}?type=${item.sectionKind}`;
}

export function firstInstructorLabel(names) {
  if (names.length === 0) return '—';
  return names.length === 1 ? names[0] : `${names[0]} +${names.length - 1}`;
}

export function reportLabel(item) {
  return item.reportNumber ? { number: item.reportNumber, location: `Libro ${item.book} · Folio ${item.folio}` } : null;
}

export function closureListQuery({ status, termId, q, page }) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (termId) params.set('termId', termId);
  if (q) params.set('q', q);
  params.set('page', String(Math.max(0, page - 1)));
  params.set('size', String(CLOSURE_LIST_PAGE_SIZE));
  return params.toString();
}
