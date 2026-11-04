export const MODULE_MINUTES = 40;
export const RECESS_MINUTES = 15;
export const MODULES_PER_RECESS = 3;
export const MINIMUM_MODULES = 3;

export const SHIFTS = [
  { id: 'MORNING', label: 'Mañana', start: '08:00', end: '12:30' },
  { id: 'AFTERNOON', label: 'Tarde', start: '13:30', end: '18:00' },
  { id: 'NIGHT', label: 'Noche', start: '18:30', end: '22:30' },
];

export function toMinutes(time) {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + minutes;
}

export function formatMinutes(total) {
  const hours = String(Math.floor(total / 60)).padStart(2, '0');
  const minutes = String(total % 60).padStart(2, '0');
  return `${hours}:${minutes}`;
}

export function buildModules(shift) {
  const modules = [];
  const shiftEnd = toMinutes(shift.end);
  let cursor = toMinutes(shift.start);
  while (cursor + MODULE_MINUTES <= shiftEnd) {
    modules.push({
      index: modules.length,
      startTime: formatMinutes(cursor),
      endTime: formatMinutes(cursor + MODULE_MINUTES),
      recessAfter: (modules.length + 1) % MODULES_PER_RECESS === 0,
    });
    cursor += MODULE_MINUTES;
    if (modules.length % MODULES_PER_RECESS === 0) cursor += RECESS_MINUTES;
  }
  return modules;
}

export function toggleModule(selection, index) {
  if (selection.length === 0) return { selection: [index], hint: null };
  const first = selection[0];
  const last = selection[selection.length - 1];
  if (selection.includes(index)) {
    if (selection.length === 1) return { selection: [], hint: null };
    if (index === first) return { selection: selection.slice(1), hint: null };
    if (index === last) return { selection: selection.slice(0, -1), hint: null };
    return { selection, hint: 'Solo se puede quitar un módulo de los extremos.' };
  }
  if (index === first - 1) return { selection: [index, ...selection], hint: null };
  if (index === last + 1) return { selection: [...selection, index], hint: null };
  return { selection, hint: 'Elegí un módulo pegado a los que ya marcaste.' };
}

export function selectionRange(modules, selection) {
  if (selection.length === 0) return null;
  return {
    startTime: modules[selection[0]].startTime,
    endTime: modules[selection[selection.length - 1]].endTime,
  };
}
