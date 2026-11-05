const MONTH_NAMES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

export const HOLIDAY_TYPES = [
  { value: 'NATIONAL', label: 'Nacional', chip: 'N', pillClass: 'border-brand bg-accent-soft text-brand', cellClass: 'bg-accent-soft text-brand' },
  { value: 'PROVINCIAL', label: 'Provincial', chip: 'P', pillClass: 'border-accent-2 bg-accent-soft text-accent-2', cellClass: 'bg-accent-soft text-accent-2' },
  { value: 'MUNICIPAL', label: 'Municipal', chip: 'M', pillClass: 'border-ink-soft bg-accent-soft text-ink-soft', cellClass: 'bg-accent-soft text-ink-soft' },
  { value: 'NON_WORKING_DAY', label: 'Día no laborable', chip: 'A', pillClass: 'border-neutral-ink bg-neutral-bg text-neutral-ink', cellClass: 'bg-neutral-bg text-neutral-ink' },
  { value: 'BRIDGE', label: 'Puente', chip: 'Pu', pillClass: 'border-dashed border-ink-faint bg-transparent text-ink-soft', cellClass: 'border-dashed border-ink-faint bg-transparent text-ink-soft' },
  { value: 'OTHER', label: 'Otro', chip: 'O', pillClass: 'border-neutral-ink bg-neutral-bg text-ink-soft', cellClass: 'bg-neutral-bg text-ink-soft' },
];

const TYPE_BY_VALUE = Object.fromEntries(HOLIDAY_TYPES.map((type) => [type.value, type]));

export const HOLIDAY_ALREADY_EXISTS = 'HOLIDAY_ALREADY_EXISTS';

export function holidayTypeInfo(value) {
  return TYPE_BY_VALUE[value] ?? TYPE_BY_VALUE.OTHER;
}

export function monthName(month) {
  return MONTH_NAMES[month];
}

export function toIsoDate(year, month, day) {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function parseIsoDate(iso) {
  const [year, month, day] = iso.split('-').map(Number);
  return { year, month: month - 1, day };
}

export function isoDay(iso) {
  return parseIsoDate(iso).day;
}

export function isoMonth(iso) {
  return parseIsoDate(iso).month;
}

export function formatLongDate(iso) {
  const { year, month, day } = parseIsoDate(iso);
  const weekday = new Intl.DateTimeFormat('es-AR', { weekday: 'long' }).format(new Date(year, month, day));
  return `${weekday} ${day} de ${MONTH_NAMES[month].toLowerCase()} de ${year}`;
}

export function weekdayShort(iso) {
  const { year, month, day } = parseIsoDate(iso);
  return new Intl.DateTimeFormat('es-AR', { weekday: 'short' }).format(new Date(year, month, day)).replace('.', '');
}

export function holidaysOfYear(holidays, year) {
  return (holidays ?? [])
    .filter((holiday) => holiday.date.startsWith(`${year}-`))
    .sort((a, b) => a.date.localeCompare(b.date));
}

export function groupHolidaysByMonth(holidays) {
  const groups = [];
  holidays.forEach((holiday) => {
    const month = isoMonth(holiday.date);
    const last = groups[groups.length - 1];
    if (last && last.month === month) last.holidays.push(holiday);
    else groups.push({ month, holidays: [holiday] });
  });
  return groups;
}

export function countByType(holidays) {
  return HOLIDAY_TYPES
    .map((type) => ({ ...type, count: holidays.filter((holiday) => holiday.type === type.value).length }))
    .filter((type) => type.count > 0);
}

export function defaultNewHolidayDate(year, month, today = new Date()) {
  if (today.getFullYear() === year && today.getMonth() === month) {
    return toIsoDate(year, month, today.getDate());
  }
  return toIsoDate(year, month, 1);
}

export function holidayCountLabel(count, year) {
  if (count === 0) return `Sin feriados cargados en ${year}`;
  return count === 1 ? `1 feriado en ${year}` : `${count} feriados en ${year}`;
}
