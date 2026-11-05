import { Clock } from 'lucide-react';
import { invoiceTitle } from '@/lib/billing';
import { PendingRequestActions } from './PendingRequestActions';
import { firstPendingInvoice, pendingAuthorizationOf, pendingOperationLabel } from './creditNotes';

export function PendingAuthorizationBanner({ invoices, studentName }) {
  const invoice = firstPendingInvoice(invoices);
  if (!invoice) return null;
  const pending = pendingAuthorizationOf(invoice);
  return (
    <div role="status" className="flex flex-col gap-3 rounded-2xl border border-warning/30 bg-warning-bg px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-start gap-3">
        <Clock className="mt-0.5 size-4 shrink-0 text-warning" aria-hidden="true" />
        <div className="flex flex-col gap-0.5">
          <span className="font-semibold text-ink">Pedido enviado, todavía no se aplicó</span>
          <span className="text-ink-soft">
            {invoiceTitle(invoice)} sigue vigente hasta que la dirección autorice {pendingOperationLabel(pending)}.
          </span>
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        <PendingRequestActions pending={pending} subject={studentName} />
      </div>
    </div>
  );
}
