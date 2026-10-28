import { AlertTriangle } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { formatDate, formatMoney } from '@/lib/billing';
import { REFUND_PART_STATUS } from '../refunds/refundRequests';

export function PendingRefundsNotice({ title, message, parts }) {
  return (
    <div className="flex flex-col gap-3">
      <div role="alert" className="flex items-start gap-3 rounded-2xl border border-warning/30 bg-warning-bg px-4 py-3">
        <AlertTriangle className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
        <div className="flex min-w-0 flex-1 flex-col gap-0.5 text-sm">
          <span className="font-semibold text-ink">{title}</span>
          <span className="text-ink-soft">{message}</span>
        </div>
      </div>
      {parts.length > 0 && (
        <section aria-label="Reembolsos pendientes de este pago" className="flex flex-col gap-2 rounded-xl bg-neutral-bg px-4 py-3 text-sm">
          <h3 className="font-heading text-sm font-semibold text-ink">Reembolsos pendientes de este pago</h3>
          {parts.map((part) => {
            const status = REFUND_PART_STATUS[part.status] ?? { label: part.status, tone: 'neutral' };
            return (
              <div key={part.refundId} className="flex items-center justify-between gap-3">
                <span className="min-w-0 text-ink">
                  Reembolso #{part.refundId}
                  <span className="text-ink-faint"> · {formatDate(part.requestedOn)}</span>
                </span>
                <span className="flex shrink-0 items-center gap-2">
                  <span className="font-semibold tabular-nums">{formatMoney(part.amount)}</span>
                  <StatusPill tone={status.tone}>{status.label}</StatusPill>
                </span>
              </div>
            );
          })}
        </section>
      )}
    </div>
  );
}
