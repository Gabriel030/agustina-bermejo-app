const decimalFormat = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 1 });
const integerFormat = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

export const EMPTY_VALUE = '—';

export function formatChartValue(value, { unit = 'count', currency = 'ARS' } = {}) {
  if (value == null || Number.isNaN(Number(value))) return EMPTY_VALUE;
  const number = Number(value);
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
  if (value == null) return '';
  const number = Number(value);
  if (unit === 'money') {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency,
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(number);
  }
  if (unit === 'percent') return `${decimalFormat.format(number)} %`;
  return integerFormat.format(number);
}
