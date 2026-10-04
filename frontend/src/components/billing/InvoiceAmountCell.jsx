import { formatMoney } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

export function InvoiceAmountCell({ invoice }) {
  const showBalance = moneyValue(invoice.remainingBalance) > 0 && invoice.status !== 'CANCELLED';
  return (
    <div className="flex flex-col items-end">
      <span className="font-mono font-semibold tabular-nums text-ink">{formatMoney(invoice.amountDue)}</span>
      {showBalance && (
        <span className="text-xs tabular-nums text-ink-faint">Saldo {formatMoney(invoice.remainingBalance)}</span>
      )}
    </div>
  );
}
