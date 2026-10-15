const decimalFormat = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 1 });
const integerFormat = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

export const EMPTY_VALUE = '—';

export function isMoney(value) {
  return value != null && typeof value === 'object' && 'amount' in value && 'currency' in value;
}

export function numericValue(value) {
  if (value == null) return null;
  const number = Number(isMoney(value) ? value.amount : value);
  return Number.isNaN(number) ? null : number;
}

export function detectCurrency(data, series) {
  for (const row of data ?? []) {
    for (const item of series) {
      if (isMoney(row[item.key])) return row[item.key].currency;
    }
  }
  return null;
}

export function normalizeChartData(data, series) {
  return (data ?? []).map((row) => {
    const normalized = { ...row };
    for (const item of series) {
      normalized[item.key] = numericValue(row[item.key]);
    }
    return normalized;
  });
}

export function formatChartValue(value, { unit = 'count', currency = 'ARS' } = {}) {
  const number = numericValue(value);
  if (number === null) return EMPTY_VALUE;
  if (isMoney(value)) return formatChartValue(number, { unit: 'money', currency: value.currency });
  if (unit === 'money') {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(number);
  }
  if (unit === 'percent') return `${decimalFormat.format(number)} %`;
  if (unit === 'days') return `${decimalFormat.format(number)} ${number === 1 ? 'día' : 'días'}`;
  return integerFormat.format(number);
}

export function formatAxisValue(value, { unit = 'count', currency = 'ARS' } = {}) {
  const number = numericValue(value);
  if (number === null) return '';
  if (unit === 'money') {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency,
      notation: 'compact',
      minimumFractionDigits: 0,
      maximumFractionDigits: 1,
    }).format(number);
  }
  if (unit === 'percent') return `${decimalFormat.format(number)} %`;
  return integerFormat.format(number);
}
