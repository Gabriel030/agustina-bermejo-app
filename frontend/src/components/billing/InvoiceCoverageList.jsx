import { Check, Info } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';
import { formatDate, formatMoney } from '@/lib/billing';
import { coverageOf, selectedTotal } from '@/lib/bankTransferReceipt';

function dueNote(invoice) {
  if (!invoice.dueDate) return null;
  return `${invoice.paymentStatus === 'OVERDUE' ? 'Venció el' : 'Vence el'} ${formatDate(invoice.dueDate)}`;
}

function rowPill(invoice, selected, state, insufficient) {
  if (selected && state === 'covered') return <StatusPill tone="success">Cubierta</StatusPill>;
  if (selected && insufficient && state === 'none') return <StatusPill tone="neutral">No se cubre</StatusPill>;
  if (invoice.paymentStatus === 'OVERDUE') return <StatusPill tone="danger">Vencida</StatusPill>;
  return <StatusPill tone="neutral">Por vencer</StatusPill>;
}

export function InvoiceCoverageList({ invoices, underReview = [], count, amount, onPick }) {
  const coverage = coverageOf(invoices.slice(0, count), amount);
  const insufficient = amount < selectedTotal(invoices, count);

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-1.5" aria-label="Cuotas que cubrís">
        {invoices.map((invoice, index) => {
          const selected = index < count;
          const entry = selected ? coverage[index] : null;
          const noteId = `invoice-note-${invoice.id}`;
          const short = entry?.state === 'short';
          return (
            <li key={invoice.id}>
              <button
                type="button"
                aria-pressed={selected}
                aria-describedby={noteId}
                onClick={() => onPick(index + 1 === count ? Math.max(1, index) : index + 1)}
                className={cn(
                  'flex w-full items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-colors',
                  selected ? 'border-brand/40 bg-accent-soft/50' : 'border-border hover:bg-neutral-bg/60',
                )}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    'flex size-5 shrink-0 items-center justify-center rounded-md border',
                    selected ? 'border-transparent bg-gradient-to-br from-brand to-accent-2 text-white' : 'border-border',
                  )}
                >
                  {selected && <Check className="size-3.5" />}
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="text-sm font-semibold text-ink sm:truncate">{invoice.title}</span>
                  <span id={noteId} className={cn('text-xs', short ? 'font-semibold text-warning' : 'text-ink-faint')}>
                    {short
                      ? `Faltan ${formatMoney({ amount: entry.missing.toFixed(2), currency: invoice.outstanding.currency })} para cubrirla`
                      : dueNote(invoice)}
                  </span>
                </span>
                <span className="flex shrink-0 flex-col items-end gap-1 sm:flex-row sm:items-center sm:gap-3">
                  <span className="text-sm font-semibold tabular-nums text-ink">{formatMoney(invoice.outstanding)}</span>
                  {rowPill(invoice, selected, entry?.state ?? 'none', insufficient)}
                </span>
              </button>
            </li>
          );
        })}
        {underReview.map((invoice) => {
          const noteId = `invoice-note-${invoice.id}`;
          return (
            <li
              key={invoice.id}
              aria-describedby={noteId}
              className="flex items-center gap-3 rounded-2xl border border-dashed border-border px-4 py-3"
            >
              <span aria-hidden="true" className="size-5 shrink-0 rounded-md border border-dashed border-border" />
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="truncate text-sm font-semibold text-ink-soft">{invoice.title}</span>
                <span id={noteId} className="text-xs text-ink-faint">Ya está en un comprobante en revisión</span>
              </span>
              <StatusPill tone="warning">En revisión</StatusPill>
            </li>
          );
        })}
      </ul>
      {invoices.length > 1 && (
        <p className="flex items-start gap-2 text-xs text-ink-faint">
          <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
          Se pagan en orden, de la más vieja a la más nueva. Tocá hasta qué cuota cubrís.
        </p>
      )}
    </div>
  );
}
