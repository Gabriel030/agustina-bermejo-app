export const CATALOG_TABS = ['carreras', 'cursos', 'mis-solicitudes'];

export const TEACHING_MODE_LABEL = {
  IN_PERSON: 'Presencial',
  ONLINE: 'Virtual',
  HYBRID: 'Híbrido',
};

export function normalizeForSearch(text) {
  return (text ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

export function matchesSearch(text, query) {
  return normalizeForSearch(text).includes(normalizeForSearch(query));
}

export function pageWithinRange(page, totalPages) {
  if (!Number.isInteger(page) || page < 1) return 1;
  return Math.min(page, totalPages);
}

export function programSummary(program) {
  return [`${program.totalYears} años`, TEACHING_MODE_LABEL[program.teachingMode]].filter(Boolean).join(' · ');
}

export function courseSummary(course) {
  return [
    course.durationMonths ? `${course.durationMonths} meses` : null,
    TEACHING_MODE_LABEL[course.teachingMode],
    course.schedule,
  ]
    .filter(Boolean)
    .join(' · ');
}
