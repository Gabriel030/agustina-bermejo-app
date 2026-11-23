import { formatMoney, formatSignedMoney } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

export function AmountBreakdown({ baseAmount, lines }) {
  return (
    <div className="flex flex-col gap-1.5 text-sm">
      <div className="flex justify-between gap-4">
        <span className="text-ink-faint">Importe de la cuota</span>
        <span className="font-mono tabular-nums text-ink">{formatMoney(baseAmount)}</span>
      </div>
      {lines.map((line, index) => (
        <div key={`${line.label}-${index}`} className="flex justify-between gap-4">
          <span className="text-ink-faint">{line.label}</span>
          <span className={`font-mono tabular-nums ${moneyValue(line.amount) > 0 ? 'text-danger' : 'text-success'}`}>
            {formatSignedMoney(line.amount)}
          </span>
        </div>
      ))}
    </div>
  );
}
