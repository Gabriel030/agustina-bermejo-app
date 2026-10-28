import { Input } from '@/components/ui/input';
import { discountLabel, formatMoney, invoiceConcept } from '@/lib/billing';
import { fromCents } from '../refunds/refundForm';

export function CreditNoteLinesForm({ invoice, draft, amounts, onChange }) {
  const lines = invoice.lines ?? [];
  return (
    <div className="flex flex-col gap-3">
      <div className="hidden grid-cols-[1fr_8rem_8rem_11rem] gap-3 px-1 text-xs font-semibold uppercase tracking-wide text-ink-faint sm:grid">
        <span>Renglón</span>
        <span className="text-right">Importe</span>
        <span className="text-right">Máximo</span>
        <span className="text-right">A acreditar</span>
      </div>
      {lines.map((line, index) => {
        const state = draft.lines[index];
        const name = line.description || invoiceConcept(line.feeType, line.period);
        const exhausted = state.maxCents === 0;
        const inputId = `credit-line-${line.lineNumber}`;
        return (
          <div key={line.lineNumber} className="flex flex-col gap-2 rounded-2xl border border-border bg-surface p-3 sm:grid sm:grid-cols-[1fr_8rem_8rem_11rem] sm:items-start sm:gap-3 sm:border-0 sm:bg-transparent sm:p-1">
            <div className="flex min-w-0 flex-col">
              <label htmlFor={inputId} className="text-sm font-medium text-ink">
                {name}
              </label>
              <span className="text-xs text-ink-faint">{line.discount ? discountLabel(line.discount) : `Renglón ${line.lineNumber}`}</span>
            </div>
            <div className="flex items-baseline justify-between sm:justify-end">
              <span className="text-xs text-ink-faint sm:hidden">Importe</span>
              <span className="text-sm tabular-nums text-ink-soft">{formatMoney(line.amount)}</span>
            </div>
            <div className="flex items-baseline justify-between sm:justify-end">
              <span className="text-xs text-ink-faint sm:hidden">Máximo</span>
              <span className="text-sm tabular-nums text-ink-soft">{formatMoney(fromCents(state.maxCents, draft.currency))}</span>
            </div>
            <div className="flex flex-col gap-1">
              {exhausted ? (
                <span id={`${inputId}-hint`} className="text-xs text-ink-faint sm:text-right">
                  Ya acreditado
                </span>
              ) : (
                <Input
                  id={inputId}
                  inputMode="decimal"
                  autoComplete="off"
                  placeholder="$ 0"
                  className="tabular-nums sm:text-right"
                  value={amounts[line.lineNumber] ?? ''}
                  onChange={(event) => onChange(line.lineNumber, event.target.value)}
                  aria-label={`Importe a acreditar de ${name}`}
                  aria-invalid={Boolean(state.error)}
                  aria-describedby={state.error ? `${inputId}-error` : undefined}
                />
              )}
              {state.error && (
                <p id={`${inputId}-error`} className="text-xs text-danger sm:text-right">
                  {state.error}
                </p>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
