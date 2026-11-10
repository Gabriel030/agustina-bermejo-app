const LOCALE = 'es-AR';

export function formatDecimal(value, { minimumFractionDigits = 0, maximumFractionDigits = 2, useGrouping = true } = {}) {
  if (!Number.isFinite(value)) return '';
  return new Intl.NumberFormat(LOCALE, { minimumFractionDigits, maximumFractionDigits, useGrouping }).format(value);
}

export function formatPercent(points, { maximumFractionDigits = 1 } = {}) {
  if (!Number.isFinite(points)) return '';
  return `${formatDecimal(points, { maximumFractionDigits })} %`;
}
