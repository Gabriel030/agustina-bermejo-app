const INTAKE_LABELS = {
  FIRST: '1er semestre',
  SECOND: '2do semestre',
};

export function cohortTitle(cohort) {
  if (cohort.label) return cohort.label;
  const intakeLabel = INTAKE_LABELS[cohort.intake] ?? cohort.intake;
  const year = cohort.admissionStart ? new Date(cohort.admissionStart).getFullYear() : '';
  return `Ingreso ${intakeLabel} ${year}`;
}

const READINESS_LABELS = {
  COHORT_RESPONSIBLE: 'Responsable',
  CURRENT_PLAN: 'Plan de estudios',
  BILLING_TEMPLATE: 'Año lectivo',
  FIRST_YEAR_SECTIONS: 'Comisiones de primer año',
  SECTION_PRECEPTORS: 'Preceptores en comisiones',
  HOLIDAYS_LOADED: 'Feriados cargados',
};

const READINESS_ORDER = [
  'COHORT_RESPONSIBLE',
  'CURRENT_PLAN',
  'BILLING_TEMPLATE',
  'FIRST_YEAR_SECTIONS',
  'SECTION_PRECEPTORS',
  'HOLIDAYS_LOADED',
];

export function readinessChecklist(readiness) {
  return [...readiness.items]
    .sort((a, b) => READINESS_ORDER.indexOf(a.code) - READINESS_ORDER.indexOf(b.code))
    .map((item) => ({
      code: item.code,
      label: READINESS_LABELS[item.code] ?? item.code,
      fulfilled: item.fulfilled,
      pendingCount: item.fulfilled ? 0 : item.reasons.length,
    }));
}

export function cohortStatus(readiness) {
  if (readiness.open) return { key: 'open', label: 'Abierta', tone: 'success' };
  if (readiness.items.some((item) => !item.fulfilled)) return { key: 'incomplete', label: 'Incompleta', tone: 'warning' };
  return { key: 'ready', label: 'Lista para abrir', tone: 'accent' };
}
