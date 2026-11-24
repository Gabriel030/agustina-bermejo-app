import { PLAN_STATUS_PILL } from '@/features/workspace/components/planStatusPills';

export const CURRICULUM_STATE_TO_PLAN_STATUS = {
  APPROVED: 'aprobada',
  IN_PROGRESS: 'en-curso',
  REGULAR_PENDING_FINAL: 'regular',
  FAILED: 'desaprobada',
  AVAILABLE: 'disponible',
  BLOCKED: 'bloqueada',
};

export const STATUS_FILTER_TO_PLAN_STATUS = {
  aprobadas: 'aprobada',
  disponibles: 'disponible',
  'en-curso': 'en-curso',
  regulares: 'regular',
  desaprobadas: 'desaprobada',
  bloqueadas: 'bloqueada',
};

export const PLAN_STATUS_FILTER_OPTIONS = [
  { id: 'todas', label: 'Todas' },
  { id: 'en-curso', label: 'En curso' },
  { id: 'regulares', label: 'Cursada regular' },
  { id: 'aprobadas', label: 'Aprobadas' },
  { id: 'desaprobadas', label: 'Desaprobadas' },
  { id: 'disponibles', label: 'Disponibles' },
  { id: 'bloqueadas', label: 'Bloqueadas' },
];

export function normalizeStatusFilter(value) {
  return PLAN_STATUS_FILTER_OPTIONS.some((option) => option.id === value) ? value : 'todas';
}

const TERM_RANK = { FIRST: 0, SECOND: 1, ANNUAL: 2 };

const STANDING_LABEL = {
  APPROVED: 'aprobada',
  REGULAR: 'cursada regular',
  NOT_TAKEN: 'sin cursar',
  WAIVED: 'eximida',
};

export function toPlanRow(subject, creditsDefined = true) {
  const estado = CURRICULUM_STATE_TO_PLAN_STATUS[subject.state];
  return {
    key: `plan-${subject.programSubjectId}`,
    subjectName: subject.subjectName,
    subjectCode: subject.subjectCode,
    curricularYear: subject.curricularYear,
    curricularTerm: subject.curricularTerm,
    credits: subject.credits,
    creditsDefined,
    earnedCredits: subject.state === 'APPROVED' ? subject.credits : 0,
    finalScore: subject.finalScore,
    category: subject.category,
    estado,
    courseworkDone: subject.state === 'APPROVED' || subject.state === 'REGULAR_PENDING_FINAL',
    blockedBy: subject.blockedBy,
    corequisites: subject.corequisites,
  };
}

export function comparePlanRows(a, b) {
  const byYear = (a.curricularYear ?? Infinity) - (b.curricularYear ?? Infinity);
  if (byYear !== 0) return byYear;
  const byTerm = (TERM_RANK[a.curricularTerm] ?? 99) - (TERM_RANK[b.curricularTerm] ?? 99);
  if (byTerm !== 0) return byTerm;
  return (a.subjectCode ?? '').localeCompare(b.subjectCode ?? '');
}

export function buildPlanRows(subjects, creditsDefined = true) {
  return (subjects ?? []).map((subject) => toPlanRow(subject, creditsDefined)).sort(comparePlanRows);
}

export function filterPlanRows(rows, statusFilter) {
  const status = STATUS_FILTER_TO_PLAN_STATUS[statusFilter];
  return status ? rows.filter((row) => row.estado === status) : rows;
}

export function describeBlockingPrerequisite(prerequisite) {
  const action = prerequisite.requirementType === 'PASSED_COURSEWORK' ? 'Regularizar' : 'Aprobar';
  const standing = STANDING_LABEL[prerequisite.standing];
  const base = `${action} ${prerequisite.subjectName} (${prerequisite.subjectCode})`;
  return standing ? `${base} · ${standing}` : base;
}

export function describeCorequisite(corequisite) {
  const status = PLAN_STATUS_PILL[CURRICULUM_STATE_TO_PLAN_STATUS[corequisite.state]];
  return `Se cursa junto con ${corequisite.subjectName} (${corequisite.subjectCode}) · ${status.label.toLowerCase()}`;
}

export function formatFinalScore(score) {
  if (score == null) return '—';
  return Number(score).toLocaleString('es-AR', { maximumFractionDigits: 2 });
}

export function formatEarnedCredits(row) {
  if (!row.creditsDefined) return '—';
  return `${formatCredits(row.earnedCredits)} / ${formatCredits(row.credits)}`;
}

export function formatCredits(credits) {
  return Number(credits ?? 0).toLocaleString('es-AR');
}

export const PROJECTION_REASON_MESSAGE = {
  NO_PLAN_SUBJECTS: 'El plan todavía no tiene materias cargadas.',
  PLAN_WITHOUT_CREDITS: 'El plan no define créditos por materia.',
  ENROLLMENT_WITHDRAWN: 'Tu matrícula está dada de baja.',
  ENROLLMENT_PAUSED: 'Tu matrícula está pausada.',
  PLAN_WITHOUT_DURATION: 'Todavía no hay historial ni duración del plan para estimar.',
  CREDITS_UNREACHABLE: 'Con las materias disponibles no se llega a los créditos que pide el plan.',
  PREREQUISITE_CYCLE: 'Hay un error en las correlativas del plan. Consultá con Administración.',
  REQUIRED_SUBJECT_INACTIVE: 'Una materia obligatoria ya no está activa en el plan.',
};

export const PROJECTION_CONFIDENCE = {
  HIGH: { label: 'Confianza alta', tone: 'success' },
  MEDIUM: { label: 'Confianza media', tone: 'accent' },
  LOW: { label: 'Confianza baja', tone: 'warning' },
};

const STAFF_PROJECTION_REASON_MESSAGE = {
  ENROLLMENT_WITHDRAWN: 'La matrícula está dada de baja.',
  ENROLLMENT_PAUSED: 'La matrícula está pausada.',
};

export function describeProjectionReason(code, audience = 'student') {
  if (audience === 'staff') {
    return STAFF_PROJECTION_REASON_MESSAGE[code] ?? PROJECTION_REASON_MESSAGE[code] ?? 'No se pudo estimar el egreso con los datos actuales.';
  }
  return PROJECTION_REASON_MESSAGE[code] ?? 'No pudimos estimar tu egreso con los datos actuales.';
}

export function describePeriodUnit(periodUnit, count) {
  if (periodUnit !== 'SEMESTER') return null;
  return count === 1 ? 'cuatrimestre estimado' : 'cuatrimestres estimados';
}

export function describePeriodRange(range, periodUnit) {
  if (!range) return null;
  if (periodUnit !== 'SEMESTER') return null;
  if (range.minPeriods === range.maxPeriods) return `Alrededor de ${range.maxPeriods} cuatrimestres`;
  return `Entre ${range.minPeriods} y ${range.maxPeriods} cuatrimestres`;
}
