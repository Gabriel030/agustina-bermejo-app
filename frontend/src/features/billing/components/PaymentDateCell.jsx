import { formatDate } from '@/lib/billing';

export function PaymentDateCell({ invoice }) {
  if (!invoice.paymentDate) {
    return <span className="text-ink-faint">—</span>;
  }
  const count = invoice.payments?.length ?? 0;
  return (
    <div className="flex flex-col">
      <span className="text-ink-soft">{formatDate(invoice.paymentDate)}</span>
      {count > 1 && <span className="text-xs text-ink-faint">{count} pagos</span>}
    </div>
  );
}
