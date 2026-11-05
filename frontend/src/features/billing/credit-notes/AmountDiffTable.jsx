import { formatMoney } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

function signedMoney(money) {
  const value = moneyValue(money);
  const formatted = formatMoney({ amount: Math.abs(value).toFixed(2), currency: money.currency });
  if (value > 0) return `+ ${formatted}`;
  if (value < 0) return `− ${formatted}`;
  return formatted;
}

export function AmountDiffTable({ rows, unchanged = [] }) {
  return (
    <div className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-3 text-sm sm:grid-cols-[1.4fr_1fr_1fr_1fr]">
      <span className="hidden text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block" aria-hidden="true" />
      <span className="hidden text-right text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Hoy</span>
      <span className="hidden text-right text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Nuevo</span>
      <span className="hidden text-right text-xs font-semibold uppercase tracking-wide text-ink-faint sm:block">Diferencia</span>
      {rows.map((row) => (
        <div key={row.key} role="group" aria-label={row.label} className="col-span-full grid grid-cols-subgrid items-baseline gap-y-1 border-t border-border pt-3 first:border-t-0 first:pt-0">
          <span className="col-span-full font-medium text-ink sm:col-span-1">{row.label}</span>
          <AmountCell label="Hoy">{formatMoney(row.today)}</AmountCell>
          <AmountCell label="Nuevo">{formatMoney(row.next)}</AmountCell>
          <AmountCell label="Diferencia" strong>
            {signedMoney(row.diff)}
          </AmountCell>
        </div>
      ))}
      {unchanged.map((row) => (
        <div key={row.key} className="col-span-full grid grid-cols-subgrid items-baseline gap-y-1 border-t border-border pt-3">
          <span className="col-span-full font-medium text-ink sm:col-span-1">{row.label}</span>
          <AmountCell label="Hoy">{row.value}</AmountCell>
          <AmountCell label="Nuevo">{row.value}</AmountCell>
          <AmountCell label="Diferencia">Sin cambios</AmountCell>
        </div>
      ))}
    </div>
  );
}

function AmountCell({ label, strong = false, children }) {
  return (
    <span className="col-span-full flex items-baseline justify-between gap-3 sm:col-span-1 sm:justify-end">
      <span className="text-xs text-ink-faint sm:hidden">{label}</span>
      <span className={strong ? 'font-semibold tabular-nums text-ink' : 'tabular-nums text-ink-soft'}>{children}</span>
    </span>
  );
}
