import { formatDate } from '@/lib/billing';

const MONTH_DIVIDER = 'border-r-2 border-r-ink-faint/30';
const YEAR_DIVIDER = 'border-r-[3px] border-r-ink/50';

export function historyDateDivider(date, monthEnds, yearEnds) {
  if (yearEnds.has(date)) return YEAR_DIVIDER;
  if (monthEnds.has(date)) return MONTH_DIVIDER;
  return '';
}

export function rejectionText(rejection) {
  const date = formatDate(rejection.rejectedAt);
  return `Justificación rechazada: ${rejection.reason}${date ? ` (${date})` : ''}`;
}

