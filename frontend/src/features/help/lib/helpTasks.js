import { parseFrontMatter } from '@/features/help/lib/frontMatter';
import { parseMarkdown, plainText } from '@/features/help/lib/markdownBlocks';

export const HELP_SECTIONS = [
  { id: 'first-day', label: 'Primer día' },
  { id: 'roles-and-permissions', label: 'Permisos y roles' },
  { id: 'set-up-school-year', label: 'Armar el año' },
  { id: 'monthly-and-yearly', label: 'Mes y año' },
  { id: 'students', label: 'Alumnos' },
  { id: 'exams-and-graduation', label: 'Mesas y egreso' },
  { id: 'data-import', label: 'Importación' },
  { id: 'notices-and-invitations', label: 'Avisos e invitaciones' },
  { id: 'institution', label: 'Institución' },
  { id: 'billing', label: 'Cobranza', underRedesign: true },
];

export const HELP_ROLE_LABELS = {
  docente: 'Docente',
  preceptor: 'Preceptor',
  administrativo: 'Administrativo',
  subdirector: 'Subdirector',
  director: 'Director',
  platform_admin: 'Plataforma',
  alumno: 'Alumno',
};

const sectionLabelOf = (sectionId) => HELP_SECTIONS.find((section) => section.id === sectionId)?.label ?? sectionId;

export function parseHelpTask(path, source) {
  const { data, body } = parseFrontMatter(source);
  const section = data.section ?? path.split('/').slice(-2, -1)[0];
  const slug = path.split('/').pop().replace(/\.md$/, '');
  const blocks = parseMarkdown(body);
  return {
    id: `${section}/${slug}`,
    title: data.title ?? slug,
    section,
    sectionLabel: sectionLabelOf(section),
    roles: Array.isArray(data.roles) ? data.roles : [],
    status: data.status ?? 'borrador',
    lastReview: data.last_review ?? null,
    blocks,
    text: plainText(blocks),
  };
}

export function isVisibleStatus(task, includeDrafts) {
  return includeDrafts || task.status === 'publicado';
}

export function tasksForRole(tasks, roleBase, showAllRoles) {
  if (showAllRoles) return tasks;
  return tasks.filter((task) => task.roles.includes(roleBase));
}

export function browsableTasks(tasks) {
  const redesigned = new Set(HELP_SECTIONS.filter((section) => section.underRedesign).map((section) => section.id));
  return tasks.filter((task) => !redesigned.has(task.section));
}

export function groupBySection(tasks) {
  return HELP_SECTIONS.map((section) => ({
    ...section,
    tasks: tasks.filter((task) => task.section === section.id),
  }));
}

export function normalizeSearchText(value) {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

function snippetAround(text, term) {
  const normalized = normalizeSearchText(text);
  const position = normalized.indexOf(term);
  if (position < 0) return '';
  const start = Math.max(0, position - 40);
  const end = Math.min(text.length, position + term.length + 80);
  const prefix = start > 0 ? '…' : '';
  const suffix = end < text.length ? '…' : '';
  return `${prefix}${text.slice(start, end).replace(/\s+/g, ' ').trim()}${suffix}`;
}

export function searchTasks(tasks, query) {
  const terms = normalizeSearchText(query).split(/\s+/).filter(Boolean);
  if (terms.length === 0) return [];
  return tasks
    .filter((task) => {
      const haystack = normalizeSearchText(`${task.title}\n${task.text}`);
      return terms.every((term) => haystack.includes(term));
    })
    .map((task) => {
      const inTitle = terms.every((term) => normalizeSearchText(task.title).includes(term));
      return { task, inTitle, snippet: inTitle ? '' : snippetAround(task.text, terms[0]) };
    })
    .sort((a, b) => Number(b.inTitle) - Number(a.inTitle));
}
