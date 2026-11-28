import { AREA_LABELS, COURSE_LEVEL, filterOfferingByQuery, offeringPath } from '@/features/enrollment/lib/catalogOffering';

export const PANEL_LEVELS = [
  { id: 'UNDERGRADUATE', label: 'Pregrado', title: 'Carreras de pregrado' },
  { id: 'BACHELOR', label: 'De grado', title: 'Carreras de grado' },
  { id: 'GRADUATE', label: 'Posgrado', title: 'Carreras de posgrado' },
  { id: 'EXTENSION', label: 'Extensión', title: 'Extensión' },
  { id: 'DIPLOMA', label: 'Diplomaturas', title: 'Diplomaturas' },
  { id: COURSE_LEVEL, label: 'Cursos', title: 'Cursos' },
];

export function axisId(kind, value) {
  return `${kind}:${value}`;
}

export function describeAxis(id) {
  if (!id) return null;
  const [kind, value] = id.split(':');
  if (kind === 'area') {
    return { kind, value, title: `Oferta de ${AREA_LABELS[value]}`, seeAll: `Ver todo en ${AREA_LABELS[value]}`, path: offeringPath({ area: value }) };
  }
  const level = PANEL_LEVELS.find((entry) => entry.id === value);
  return { kind, value, title: level.title, seeAll: `Ver todo en ${level.title.toLowerCase()}`, path: offeringPath({ level: value }) };
}

export function countOfferingBy(items, field) {
  const counts = new Map();
  items.forEach((item) => counts.set(item[field], (counts.get(item[field]) ?? 0) + 1));
  return counts;
}

export function panelListItems(items, axis, query) {
  const field = axis?.kind === 'area' ? 'area' : 'level';
  const scoped = axis ? items.filter((item) => item[field] === axis.value) : items;
  return filterOfferingByQuery(scoped, query.trim());
}
