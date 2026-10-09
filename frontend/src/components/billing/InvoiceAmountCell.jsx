import { formatMoney } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

function amountSuffix(invoice) {
  const lines = invoice.lines ?? [];
  if (lines.some((line) => line.discount != null)) return 'con descuento';
  if (lines.some((line) => line.feeType === 'LATE_FEE')) return 'incluye recargo';
  return null;
}

export function InvoiceAmountCell({ invoice }) {
  const showBalance = moneyValue(invoice.outstandingAmount) > 0 && invoice.paymentStatus !== 'CANCELLED';
  const suffix = amountSuffix(invoice);
  return (
    <div className="flex flex-col items-end">
      <span className="font-mono font-semibold tabular-nums text-ink">{formatMoney(invoice.totalAmount)}</span>
      {suffix && <span className="text-xs text-ink-faint">{suffix}</span>}
      {showBalance && (
        <span className="text-xs tabular-nums text-ink-faint">Saldo {formatMoney(invoice.outstandingAmount)}</span>
      )}
    </div>
  );
}
