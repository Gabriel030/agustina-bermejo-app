import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { formatDate, formatMoney } from '@/lib/billing';
import { REFUND_PART_STATUS, refundMethodLabel } from './refundRequests';

function partNote(part) {
  if (part.status === 'COMPLETED') {
    return [formatDate(part.completedOn), part.completionReference && `ref. ${part.completionReference}`].filter(Boolean).join(' · ');
  }
  if (part.status === 'FAILED') return part.failureReason;
  if (part.status === 'REJECTED') return part.rejectionReason;
  return null;
}

export function RefundPartsList({ parts, onComplete, onFail }) {
  return (
    <ul className="flex flex-col gap-1 p-2 text-left">
      {parts.map((part) => {
        const status = REFUND_PART_STATUS[part.status] ?? { label: part.status, tone: 'neutral' };
        const note = partNote(part);
        return (
          <li
            key={part.id}
            className="flex flex-col gap-2 rounded-2xl px-4 py-3 transition-colors hover:bg-neutral-bg/60 md:grid md:grid-cols-[minmax(0,1.4fr)_8rem_minmax(0,1.2fr)_13rem] md:items-center md:gap-x-6"
          >
            <div className="flex min-w-0 flex-col">
              <span className="text-sm font-medium text-ink">
                Pago #{part.paymentId} · {refundMethodLabel(part.methodType)}
              </span>
              <span className="break-words text-xs text-ink-faint">
                {[part.destinationAccount, part.destinationHolder].filter(Boolean).join(' · ') || 'Sin destino'}
              </span>
            </div>
            <span className="text-sm font-semibold tabular-nums text-ink md:text-right">{formatMoney(part.amount)}</span>
            <div className="flex min-w-0 flex-col items-start gap-1">
              <StatusPill tone={status.tone}>{status.label}</StatusPill>
              {note && <span className="max-w-xs break-words text-xs text-ink-faint">{note}</span>}
            </div>
            {part.status === 'APPROVED' ? (
              <div className="flex gap-2 md:justify-end">
                <Button type="button" variant="outline" size="sm" aria-label={`Marcar como hecho el pago #${part.paymentId}`} onClick={() => onComplete(part)}>
                  Marcar como hecho
                </Button>
                <Button type="button" variant="ghost" size="sm" aria-label={`No se pudo el pago #${part.paymentId}`} onClick={() => onFail(part)}>
                  No se pudo
                </Button>
              </div>
            ) : (
              <span className="hidden md:block" />
            )}
          </li>
        );
      })}
    </ul>
  );
}
