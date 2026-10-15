import { numericValue } from '@/components/charts';

const UNIT_BY_METRIC = { COUNT: 'count', MONEY: 'money', PERCENT: 'percent', DAYS: 'days' };

const monthFormat = new Intl.DateTimeFormat('es-AR', { month: 'short', year: '2-digit', timeZone: 'UTC' });
const longMonthFormat = new Intl.DateTimeFormat('es-AR', { month: 'long', year: 'numeric', timeZone: 'UTC' });

function monthDate(month) {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number - 1, 1));
}

export function formatShortMonth(month) {
  return monthFormat.format(monthDate(month)).replace('.', '');
}

export function formatLongMonth(month) {
  return longMonthFormat.format(monthDate(month));
}

export function unitOf(metricUnit) {
  return UNIT_BY_METRIC[metricUnit] ?? 'count';
}

export function seriesOf(metric, key) {
  return metric?.series?.find((candidate) => candidate.key === key) ?? null;
}

export function valueAt(metric, key, month) {
  const index = metric?.months?.indexOf(month) ?? -1;
  if (index < 0) return null;
  return seriesOf(metric, key)?.values?.[index] ?? null;
}

export function previousMonthOf(metric, month) {
  const index = metric?.months?.indexOf(month) ?? -1;
  return index > 0 ? metric.months[index - 1] : null;
}

function toCents(amount) {
  const [whole, fraction = ''] = String(amount).split('.');
  const negative = whole.startsWith('-');
  const digits = BigInt(whole.replace('-', '') || '0') * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2));
  return negative ? -digits : digits;
}

function fromCents(cents) {
  const negative = cents < 0n;
  const absolute = negative ? -cents : cents;
  const text = absolute.toString().padStart(3, '0');
  return `${negative ? '-' : ''}${text.slice(0, -2)}.${text.slice(-2)}`;
}

export function sumMoney(values) {
  const present = values.filter((value) => value != null);
  if (present.length === 0) return null;
  const total = present.reduce((sum, value) => sum + toCents(value.amount), 0n);
  return { amount: fromCents(total), currency: present[0].currency };
}

export function sumCounts(values) {
  const present = values.filter((value) => value != null);
  return present.length === 0 ? null : present.reduce((total, value) => total + Number(value), 0);
}

export function buildRows(metric, extra = {}) {
  return (metric?.months ?? []).map((month, index) => {
    const row = { month, label: formatShortMonth(month) };
    for (const series of metric.series) row[series.key] = series.values[index] ?? null;
    for (const [key, compute] of Object.entries(extra)) row[key] = compute(row);
    return row;
  });
}

export function keysWithPrefix(metric, prefix) {
  return (metric?.series ?? []).map((series) => series.key).filter((key) => key.startsWith(prefix));
}

export function deltaBetween(current, previous) {
  const now = numericValue(current);
  const before = numericValue(previous);
  if (now === null || before === null) return null;
  return now - before;
}

const decimal = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

export function formatPointsDelta(delta) {
  if (delta === null) return null;
  const sign = delta > 0 ? '+' : delta < 0 ? '−' : '';
  return `${sign}${decimal.format(Math.abs(delta))} pp`;
}

export function goalStatusAt(metric, month) {
  const index = metric?.months?.indexOf(month) ?? -1;
  const evaluation = metric?.goal?.evaluation?.[index];
  return evaluation === 'MET' || evaluation === 'BELOW' ? evaluation : null;
}

export function goalThreshold(metric) {
  const goal = metric?.goal;
  if (!goal) return null;
  if (goal.thresholdPercent != null) return { value: Number(goal.thresholdPercent), unit: 'percent' };
  if (goal.thresholdDays != null) return { value: Number(goal.thresholdDays), unit: 'days' };
  return null;
}

export function currentMonth(today = new Date()) {
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
}

function shiftMonth(month, offset) {
  const date = monthDate(month);
  date.setUTCMonth(date.getUTCMonth() + offset);
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

export const RANGE_OPTIONS = [
  { value: 'LAST_6', label: 'Últimos 6 meses' },
  { value: 'LAST_12', label: 'Últimos 12 meses' },
  { value: 'LAST_24', label: 'Últimos 24 meses' },
  { value: 'THIS_YEAR', label: 'Año en curso' },
];

export const DEFAULT_RANGE = 'LAST_12';

export function rangeFor(option, today = new Date()) {
  const to = currentMonth(today);
  if (option === 'THIS_YEAR') return { from: `${to.slice(0, 4)}-01`, to };
  const months = { LAST_6: 6, LAST_12: 12, LAST_24: 24 }[option] ?? 12;
  return { from: shiftMonth(to, 1 - months), to };
}
