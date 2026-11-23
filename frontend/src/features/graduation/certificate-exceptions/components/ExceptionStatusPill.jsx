import { StatusPill } from '@/components/StatusPill';
import { exceptionStatusOf, formatDayMonth } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

export function ExceptionStatusPill({ status, validUntil }) {
  const { label, tone } = exceptionStatusOf(status);
  const suffix = status === 'APPROVED' && validUntil ? ` · Vence ${formatDayMonth(validUntil)}` : '';
  return <StatusPill tone={tone}>{`${label}${suffix}`}</StatusPill>;
}
