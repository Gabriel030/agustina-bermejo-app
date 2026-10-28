import { formatMoney } from '@/lib/billing';
import { refundMethodLabel } from './refundRequests';

function SummaryRow({ label, children }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="shrink-0 text-xs text-ink-faint">{label}</dt>
      <dd className="min-w-0 break-words text-right text-ink">{children}</dd>
    </div>
  );
}

export function RefundPartSummary({ refund }) {
  return (
    <dl className="flex flex-col gap-1.5 rounded-xl bg-neutral-bg px-4 py-3 text-sm">
      <SummaryRow label="Monto">
        <span className="font-semibold tabular-nums">{formatMoney(refund.amount)}</span>
      </SummaryRow>
      <SummaryRow label="Destino">{refund.destinationAccount || '—'}</SummaryRow>
      <SummaryRow label="Pago original">
        Pago #{refund.paymentId} · {refundMethodLabel(refund.methodType)}
      </SummaryRow>
    </dl>
  );
}
