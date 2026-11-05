import { formatCourseDuration } from '@/lib/courseDuration';
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
    formatCourseDuration(course.durationMonths),
    TEACHING_MODE_LABEL[course.teachingMode],
    course.schedule,
  ]
    .filter(Boolean)
    .join(' · ');
}

export function formatCredits(credits) {
  return `${credits} ${credits === 1 ? 'crédito' : 'créditos'}`;
}

export function formatWeeklyWorkload(hours) {
  return `${hours} h/semana`;
}

export const NO_YEAR_GROUP = 'none';

export function groupSubjectsByYear(subjects) {
  const groups = (subjects ?? []).reduce((acc, subject) => {
    const key = subject.curricularYear ?? NO_YEAR_GROUP;
    (acc[key] ??= []).push(subject);
    return acc;
  }, {});
  return Object.entries(groups)
    .sort(([a], [b]) => {
      if (a === NO_YEAR_GROUP) return 1;
      if (b === NO_YEAR_GROUP) return -1;
      return Number(a) - Number(b);
    })
    .map(([year, items]) => ({
      year,
      label: year === NO_YEAR_GROUP ? 'Sin año asignado' : `Año ${year}`,
      items,
    }));
}

export function isListedInCatalog(item) {
  return (item.status == null || item.status === 'ACTIVE') && item.visibleInCatalog !== false;
}

export function subjectTag(subject) {
  return subject.category === 'ELECTIVE' ? 'Optativa' : null;
}
