export const WEEKDAYS = [
  { id: 'MONDAY', label: 'Lunes', short: 'Lun' },
  { id: 'TUESDAY', label: 'Martes', short: 'Mar' },
  { id: 'WEDNESDAY', label: 'Miércoles', short: 'Mié' },
  { id: 'THURSDAY', label: 'Jueves', short: 'Jue' },
  { id: 'FRIDAY', label: 'Viernes', short: 'Vie' },
  { id: 'SATURDAY', label: 'Sábado', short: 'Sáb' },
];

export const CLASSROOM_STATES = {
  SHORT_CAPACITY: { label: 'Capacidad menor', tone: 'danger' },
  OCCUPIED: { label: 'Ocupada', tone: 'warning' },
  FREE_IN_PERIOD: { label: 'Libre en tu período', tone: 'accent' },
  FREE: { label: 'Libre', tone: 'success' },
};

export function classifyClassroom(classroom) {
  if (classroom.capacityShortfall > 0) return 'SHORT_CAPACITY';
  if (!classroom.available) return 'OCCUPIED';
  if (classroom.occupant && classroom.overlapsInDates === false) return 'FREE_IN_PERIOD';
  return 'FREE';
}

const STATE_ORDER = ['FREE', 'FREE_IN_PERIOD', 'OCCUPIED', 'SHORT_CAPACITY'];

export function sortClassrooms(classrooms) {
  return [...classrooms].sort(
    (a, b) =>
      STATE_ORDER.indexOf(classifyClassroom(a)) - STATE_ORDER.indexOf(classifyClassroom(b)) ||
      a.name.localeCompare(b.name, 'es'),
  );
}

export function formatDate(isoDate) {
  const [year, month, day] = isoDate.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

export function describeOccupant(occupant) {
  const code = occupant.section.code ?? occupant.section.sectionName;
  return `${code} · ${occupant.section.name}`;
}

export function describeTerm(occupant) {
  return `${occupant.term.name} (${formatDate(occupant.term.startDate)} al ${formatDate(occupant.term.endDate)})`;
}
