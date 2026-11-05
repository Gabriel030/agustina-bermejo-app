import { GlassCard } from '@/components/GlassCard';
import { ProgressBar } from '@/components/ProgressBar';
import { StatusPill } from '@/components/StatusPill';
import { InfoTip } from '@/components/charts/InfoTip';
import { cn } from '@/lib/utils';
import { formatMoney } from '@/lib/billing';
import { APPLIED_TYPE_LABELS, formatMonth, formatPercent } from './discountLabels';
import { discountDefinitionSubtitle, discountPeriod, discountProgress, isMonthlyDiscount, lostPeriodsOf } from './studentDiscountView';

const MY_STATUS = {
  ACTIVE: { label: 'Vigente', tone: 'success' },
  EXHAUSTED: { label: 'Usado completo', tone: 'neutral' },
  ENDED: { label: 'Finalizado', tone: 'neutral' },
  REVOKED: { label: 'Revocado', tone: 'danger' },
};

const MY_APPLIES = {
  MONTHLY_INSTALLMENT: 'Cuotas mensuales',
  ENROLLMENT_FEE: 'Matrícula',
  ANNUAL_UPFRONT: 'Pago anual',
  EXAM_FEE: 'Examen final',
  RETAKEN_SUBJECT_FEE: 'Materia recursada',
  DEGREE_ISSUANCE_FEE: 'Emisión de título',
  ADMINISTRATIVE_FEE: 'Trámite administrativo',
};

function studentValue(calculation) {
  return calculation.type === 'PROPORTIONAL' ? formatPercent(calculation.ratePercent) : formatMoney(calculation.amount);
}

export function MyDiscountCard({ discount }) {
  const status = MY_STATUS[discount.status] ?? { label: discount.status, tone: 'neutral' };
  const kind = APPLIED_TYPE_LABELS[discount.type] ?? discount.type;
  const { applied, total } = discountProgress(discount);
  const lost = lostPeriodsOf(discount);
  const inactive = discount.status !== 'ACTIVE';
  const monthly = isMonthlyDiscount(discount);
  const appliesLabel = MY_APPLIES[discount.applicableInvoiceType] ?? discount.applicableInvoiceType;
  const subtitle = [discountDefinitionSubtitle(discount), discount.enrollmentName]
    .filter(Boolean)
    .join(' · ');
  const progressLabel = `${monthly ? 'Cuotas con descuento' : 'Matrícula'}: ${applied} de ${total}`;

  return (
    <GlassCard className={cn('flex flex-col gap-3 p-5', inactive && 'opacity-85')} aria-label={`${kind}${subtitle ? `, ${subtitle}` : ''}`} role="article">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-heading text-base font-semibold text-ink">{kind}</h3>
          {subtitle && <p className="text-xs text-ink-faint">{subtitle}</p>}
        </div>
        <StatusPill tone={status.tone}>{status.label}</StatusPill>
      </div>
      <div>
        <p className="font-heading text-2xl font-bold text-ink">
          {studentValue(discount.calculation)} <span className="text-sm font-semibold text-ink-soft">de descuento</span>
        </p>
        <p className="text-xs text-ink-faint">{appliesLabel} · {discountPeriod(discount)}</p>
      </div>
      {(total > 1 || applied > 0) && <ProgressBar value={(applied / total) * 100} label={progressLabel} showValue={false} />}
      {lost.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl bg-warning-bg px-3 py-2 text-xs text-ink">
          <span>
            {lost.length} {lost.length === 1 ? 'mes perdido' : 'meses perdidos'}: {lost.map(formatMonth).join(', ')}
          </span>
          <InfoTip>En esos meses otro descuento te ahorraba más y se aplicó ese. Un mes perdido no se corre al siguiente.</InfoTip>
        </div>
      )}
      {discount.endCauseText && (
        <p className="text-xs text-ink-faint">
          {discount.endCauseText}
          {discount.revokedFromPeriod && `. Dejó de aplicarse desde ${formatMonth(discount.revokedFromPeriod)}`}.
        </p>
      )}
      {!inactive && applied === 0 && !monthly && (
        <p className="text-xs text-ink-faint">Se aplica cuando se emita la factura de {appliesLabel.toLowerCase()}.</p>
      )}
    </GlassCard>
  );
}
