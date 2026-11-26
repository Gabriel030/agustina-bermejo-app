import { TEACHING_MODE_LABELS } from '@/lib/teachingMode';
import { formatCourseDuration } from '@/lib/courseDuration';
import { matchesSearch } from '@/lib/catalog';

export const COURSE_LEVEL = 'COURSE';
export const CATALOG_PATH = '/catalogo';
export const CLOSING_SOON_DAYS = 30;
export const NO_AREA_KEY = 'NONE';
export const NO_AREA_LABEL = 'Otras ofertas';

export const AREA_LABELS = {
  BUSINESS_ADMINISTRATION: 'Administración y Negocios',
  TECHNOLOGY: 'Tecnología',
  DESIGN: 'Diseño',
  HEALTH: 'Salud',
  SOCIAL_SCIENCES: 'Ciencias Sociales',
  LANGUAGES_AND_COMMUNICATION: 'Idiomas y Comunicación',
};

export const LEVEL_LABELS = {
  UNDERGRADUATE: 'Pregrado',
  BACHELOR: 'De grado',
  GRADUATE: 'Posgrado',
  EXTENSION: 'Extensión',
  DIPLOMA: 'Diplomatura',
  [COURSE_LEVEL]: 'Curso',
};

const PROGRAM_FALLBACK_LABEL = 'Carrera';

export function parseOfferingFilters(searchParams) {
  const area = searchParams.get('area');
  const level = searchParams.get('level');
  return {
    area: Object.hasOwn(AREA_LABELS, area) ? area : null,
    level: Object.hasOwn(LEVEL_LABELS, level) ? level : null,
    q: (searchParams.get('q') ?? '').trim(),
  };
}

export function hasOfferingFilters({ area, level, q }) {
  return Boolean(area || level || q);
}

export function offeringPath({ area, level, q } = {}) {
  const params = new URLSearchParams();
  if (area) params.set('area', area);
  if (level) params.set('level', level);
  if (q) params.set('q', q);
  const query = params.toString();
  return query ? `${CATALOG_PATH}?${query}` : CATALOG_PATH;
}

function programItem(program) {
  return {
    key: `program-${program.id}`,
    kind: 'program',
    id: program.id,
    name: program.name,
    area: program.area ?? null,
    typeLabel: LEVEL_LABELS[program.level] ?? PROGRAM_FALLBACK_LABEL,
    duration: program.totalYears ? `${program.totalYears} ${program.totalYears === 1 ? 'año' : 'años'}` : null,
    modality: TEACHING_MODE_LABELS[program.teachingMode] ?? null,
    to: `${CATALOG_PATH}/carreras/${program.id}`,
  };
}

function courseItem(course) {
  return {
    key: `course-${course.id}`,
    kind: 'course',
    id: course.id,
    name: course.name,
    area: course.area ?? null,
    typeLabel: LEVEL_LABELS[COURSE_LEVEL],
    duration: formatCourseDuration(course.durationMonths),
    modality: TEACHING_MODE_LABELS[course.teachingMode] ?? null,
    to: `${CATALOG_PATH}/cursos/${course.id}`,
  };
}

export function buildOfferingItems(programs, courses) {
  return [...(programs ?? []).map(programItem), ...(courses ?? []).map(courseItem)];
}

export function filterOfferingByQuery(items, q) {
  return q ? items.filter((item) => matchesSearch(item.name, q)) : items;
}

export function groupOfferingByArea(items) {
  const groups = [];
  const byKey = new Map();
  items.forEach((item) => {
    const key = item.area ?? NO_AREA_KEY;
    if (!byKey.has(key)) {
      const group = { key, label: AREA_LABELS[key] ?? NO_AREA_LABEL, items: [] };
      byKey.set(key, group);
      groups.push(group);
    }
    byKey.get(key).items.push(item);
  });
  const areaOrder = Object.keys(AREA_LABELS);
  const rank = (group) => (group.key === NO_AREA_KEY ? areaOrder.length : areaOrder.indexOf(group.key));
  return groups.sort((a, b) => rank(a) - rank(b));
}

export function describeOfferingView({ area, level, q }) {
  const areaLabel = AREA_LABELS[area];
  const levelLabel = LEVEL_LABELS[level];
  const trail = [];
  if (areaLabel) trail.push({ label: areaLabel, to: offeringPath({ area }) });
  if (levelLabel) trail.push({ label: levelLabel, to: offeringPath({ area, level }) });
  if (q) trail.push({ label: `Resultados para «${q}»` });
  const crumbs = [
    { label: 'Oferta académica', to: CATALOG_PATH },
    ...trail.map((crumb, index) => (index === trail.length - 1 ? { label: crumb.label } : crumb)),
  ];

  let title = 'Oferta académica';
  if (q) title = `Resultados para «${q}»`;
  else if (areaLabel && levelLabel) title = `${levelLabel} · ${areaLabel}`;
  else if (areaLabel) title = `Oferta de ${areaLabel}`;
  else if (levelLabel) title = levelLabel;
  return { title, crumbs };
}

function dayNumber(isoDate) {
  const [year, month, day] = isoDate.split('-').map(Number);
  return Date.UTC(year, month - 1, day) / 86400000;
}

export function todayIsoDate(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function formatDayMonth(isoDate) {
  const [, month, day] = isoDate.split('-');
  return `${day}/${month}`;
}

export const ADMISSION_BADGES = {
  open: { tone: 'success', label: 'Inscripciones abiertas' },
  unknown: { tone: 'neutral', label: 'Consultá fechas' },
};

export function admissionBadge(cohorts, todayIso) {
  const open = (cohorts ?? []).filter((cohort) => cohort.admissionOpen);
  if (open.length === 0) return ADMISSION_BADGES.unknown;
  const endings = open.map((cohort) => cohort.admissionEnd).filter(Boolean).sort();
  const nextEnd = endings.find((end) => dayNumber(end) >= dayNumber(todayIso));
  if (nextEnd && dayNumber(nextEnd) - dayNumber(todayIso) <= CLOSING_SOON_DAYS) {
    return { tone: 'warning', label: `Cierra ${formatDayMonth(nextEnd)}` };
  }
  return ADMISSION_BADGES.open;
}

const SESSION_MODALITY_LABELS = { ONLINE: 'Online', IN_PERSON: 'Presencial' };
const SESSION_TIME_ZONE = 'America/Argentina/Buenos_Aires';

export function nextInformativeSession(sessions, now) {
  return (
    (sessions ?? [])
      .filter((session) => session.status === 'SCHEDULED' && new Date(session.scheduledAt) >= now)
      .sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt))[0] ?? null
  );
}

export function describeInformativeSession(session) {
  const date = new Date(session.scheduledAt);
  const day = new Intl.DateTimeFormat('es-AR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: SESSION_TIME_ZONE }).format(date);
  const time = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: SESSION_TIME_ZONE }).format(date);
  const plainDay = day.replace(',', '');
  const when = `${plainDay.charAt(0).toUpperCase()}${plainDay.slice(1)} · ${time} h`;
  return [when, SESSION_MODALITY_LABELS[session.modality]].filter(Boolean).join(' · ');
}
