import { formatMoney } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

export function InvoiceAmountCell({ invoice }) {
  const showBalance =
    moneyValue(invoice.outstandingAmount) > 0 &&
    moneyValue(invoice.paidAmount) + moneyValue(invoice.creditedAmount) > 0 &&
    invoice.paymentStatus !== 'CANCELLED';
  return (
    <div className="flex flex-col items-end">
      <span className="font-mono font-semibold tabular-nums text-ink">{formatMoney(invoice.totalAmount)}</span>
      {showBalance && (
        <span className="text-xs tabular-nums text-ink-faint">Saldo {formatMoney(invoice.outstandingAmount)}</span>
      )}
    </div>
  );
}
