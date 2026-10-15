import { GlassCard } from '@/components/GlassCard';
import { AmountBreakdown } from '@/components/alumno/AmountBreakdown';
import { ADJUSTMENT_TYPE_LABELS, formatMoney, invoiceConcept } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

export function InvoiceSummary({ breakdown }) {
  const alreadyPaid = (breakdown.paymentsApplied ?? []).reduce((sum, payment) => sum + moneyValue(payment.amountApplied), 0);
  const lines = [
    ...(breakdown.adjustments ?? []).map((adjustment) => ({
      label: ADJUSTMENT_TYPE_LABELS[adjustment.type] ?? adjustment.type,
      amount: adjustment.amount,
    })),
    ...(alreadyPaid > 0 ? [{ label: 'Pagos ya registrados', amount: { amount: (-alreadyPaid).toFixed(2), currency: breakdown.baseAmount.currency } }] : []),
  ];

  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-faint">Estás pagando</p>
        <p className="mt-0.5 font-semibold text-ink">{invoiceConcept(breakdown.feeType, breakdown.period)}</p>
      </div>
      <AmountBreakdown baseAmount={breakdown.baseAmount} lines={lines} />
      <div className="flex items-baseline justify-between gap-4 border-t border-border pt-3">
        <span className="text-sm font-semibold text-ink">Total a pagar</span>
        <span className="font-mono text-xl font-bold tabular-nums text-ink">{formatMoney(breakdown.remainingBalance)}</span>
      </div>
    </GlassCard>
  );
}
