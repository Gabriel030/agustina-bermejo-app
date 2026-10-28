export const ENTRY_REASON_LABELS = {
  DAYS_OVERDUE: 'Por días de atraso',
  OVERDUE_INSTALLMENTS: 'Por cuotas vencidas',
  DAYS_AND_INSTALLMENTS: 'Por días y cuotas',
};

export const DAYS_BUCKETS = ['0-29', '30-59', '60+'];
export const INSTALLMENT_BUCKETS = ['1', '2', '3+'];

export function daysBucket(days) {
  if (days >= 60) return '60+';
  if (days >= 30) return '30-59';
  return '0-29';
}

export function daysBucketLabel(bucket) {
  return { '0-29': 'Hasta 29 días', '30-59': '30 a 59 días', '60+': '60 días o más' }[bucket];
}

export function installmentsBucket(count) {
  if (count >= 3) return '3+';
  return String(Math.max(count, 1));
}

export function installmentsBucketLabel(bucket) {
  return { 1: '1 cuota', 2: '2 cuotas', '3+': '3 cuotas o más' }[bucket];
}

export function daysTone(days) {
  if (days >= 60) return 'danger';
  if (days >= 30) return 'warning';
  return 'neutral';
}

export function lastReminderBucket(item) {
  return item.lastReminderDate ? 'WITH_REMINDER' : 'NEVER';
}

export function lastReminderBucketLabel(bucket) {
  return bucket === 'NEVER' ? 'Nunca' : 'Con aviso';
}

export function matchesSearch(item, term) {
  const needle = term.trim().toLowerCase();
  if (!needle) return true;
  const digits = needle.replace(/\D/g, '');
  const matchesDni = digits !== '' && item.dni.replace(/\D/g, '').includes(digits);
  return item.studentName.toLowerCase().includes(needle) || matchesDni;
}

export function criterionText(settings) {
  if (!settings) return null;
  return `${settings.contactListDaysOverdue} días de atraso o ${settings.contactListOverdueInstallments} cuotas vencidas`;
}
