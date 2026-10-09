import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { Copy, Receipt } from 'lucide-react';
import { toast } from 'sonner';
import { StatusPill } from '@/components/StatusPill';
import {
  INVOICE_DOCUMENT_STATUS_LABELS,
  RECEIPT_STATUS_CONFIG,
  discountLabel,
  formatDate,
  formatDateTime,
  formatMoney,
  formatSignedMoney,
  invoiceConcept,
  negatedMoney,
  paymentMethodLabel,
} from '@/lib/billing';
import { moneyValue } from '@/lib/utils';

async function copyReference(reference) {
  try {
    await navigator.clipboard.writeText(reference);
    toast.success('Referencia copiada');
  } catch {
    toast.error('No pudimos copiar la referencia.');
  }
}

function PaymentBlock({ payment }) {
  const partial = moneyValue(payment.paymentTotal) > moneyValue(payment.amountApplied);
  return (
    <div className="rounded-xl border border-border p-3 text-sm">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <span className="font-mono font-semibold tabular-nums text-ink">{formatMoney(payment.amountApplied)}</span>
        <span className="text-ink-soft">
          {paymentMethodLabel(payment.method, payment.cardType, payment.cardInstallments)}
        </span>
        <span className="text-ink-faint">{formatDateTime(payment.paidAt)}</span>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-faint">
        {payment.externalReference && (
          <span className="inline-flex items-center gap-1">
            Ref. {payment.externalReference}
            <button
              type="button"
              aria-label="Copiar referencia"
              onClick={(event) => {
                event.stopPropagation();
                copyReference(payment.externalReference);
              }}
              className="rounded p-0.5 text-ink-faint transition-colors hover:text-ink"
            >
              <Copy size={12} />
            </button>
          </span>
        )}
        {payment.registeredByName && <span>Registrado por {payment.registeredByName}</span>}
        {payment.receiptId && <span>Origen: comprobante subido por el alumno</span>}
        {partial && (
          <span>
            Aplicado a esta cuota {formatMoney(payment.amountApplied)} de un pago de {formatMoney(payment.paymentTotal)}
          </span>
        )}
      </div>
      {payment.notes && <p className="mt-1 text-xs text-ink-soft">Nota: {payment.notes}</p>}
    </div>
  );
}

function ReceiptBlock({ receipt, onViewReceipt }) {
  const status = RECEIPT_STATUS_CONFIG[receipt.status] ?? { label: receipt.status, tone: 'neutral' };
  return (
    <div className="rounded-xl border border-border p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1.5 text-ink-soft">
          <Receipt size={14} />
          Comprobante subido el {formatDate(receipt.uploadedAt)}
          {receipt.uploadedByName ? ` por ${receipt.uploadedByName}` : ''}
        </span>
        <span className="flex items-center gap-3">
          <StatusPill tone={status.tone}>{status.label}</StatusPill>
          {onViewReceipt && (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation();
                onViewReceipt(receipt.id);
              }}
              className="text-brand hover:underline"
            >
              Ver comprobante
            </button>
          )}
        </span>
      </div>
      {receipt.reviewedByName && receipt.reviewedAt && (
        <p className="mt-1 text-xs text-ink-faint">
          Revisado por {receipt.reviewedByName} el {formatDate(receipt.reviewedAt)}
        </p>
      )}
      {receipt.status === 'REJECTED' && receipt.rejectionReason && (
        <p className="mt-1 text-xs text-danger">Motivo del rechazo: {receipt.rejectionReason}</p>
      )}
    </div>
  );
}

export function InvoiceLinesBreakdown({ invoice }) {
  const lines = invoice.lines ?? [];
  const hasDiscount = lines.some((line) => line.discount != null);
  return (
    <div className="flex flex-col gap-1.5 text-sm">
      {lines.map((line) => (
        <Fragment key={line.lineNumber}>
          <div className="flex justify-between gap-4">
            <span className="text-ink-faint">{line.description || invoiceConcept(line.feeType, line.period)}</span>
            <span className="font-mono tabular-nums text-ink">{formatMoney(line.amount)}</span>
          </div>
          {line.discount && (
            <div className="flex justify-between gap-4">
              <span className="text-ink-faint">{discountLabel(line.discount)}</span>
              <span className="font-mono tabular-nums text-success">
                {formatSignedMoney(negatedMoney(line.discount.discountAmount))}
              </span>
            </div>
          )}
        </Fragment>
      ))}
      {(hasDiscount || lines.length > 1) && (
        <div className="flex justify-between gap-4 border-t border-border pt-1.5">
          <span className="font-medium text-ink-soft">Total</span>
          <span className="font-mono font-semibold tabular-nums text-ink">{formatMoney(invoice.totalAmount)}</span>
        </div>
      )}
    </div>
  );
}

export function InvoiceDetail({ invoice, onViewReceipt, reviewLink = false, showDocumentStatus = false }) {
  const lines = invoice.lines ?? [];
  const showBreakdown = lines.some((line) => line.discount != null) || lines.length > 1;
  const payments = invoice.payments ?? [];
  const receipts = invoice.receipts ?? [];
  const pendingReceipt = receipts.some((receipt) => receipt.status === 'PENDING_REVIEW');

  return (
    <div className="flex flex-col gap-3 py-2">
      {invoice.invoiceNumber && (
        <p className="text-xs text-ink-faint">
          Factura {invoice.invoiceNumber}
          {showDocumentStatus ? ` · ${INVOICE_DOCUMENT_STATUS_LABELS[invoice.documentStatus] ?? invoice.documentStatus}` : ''}
          {invoice.issueDate ? ` · emitida el ${formatDate(invoice.issueDate)}` : ''}
        </p>
      )}
      {invoice.documentStatus === 'DISCARDED' && (
        <p className="text-sm text-ink-soft">
          Factura anulada
          {invoice.discardedReason ? `. Motivo: ${invoice.discardedReason}` : ''}
        </p>
      )}
      {pendingReceipt && reviewLink && (
        <p className="rounded-xl bg-warning-bg px-3 py-2 text-sm text-warning">
          Hay un comprobante pendiente de revisión.{' '}
          <Link to={{ search: '?tab=comprobantes' }} className="font-semibold underline">
            Revisar en Comprobantes
          </Link>
        </p>
      )}
      {showBreakdown && <InvoiceLinesBreakdown invoice={invoice} />}
      {payments.map((payment) => (
        <PaymentBlock key={payment.paymentId} payment={payment} />
      ))}
      {receipts.map((receipt) => (
        <ReceiptBlock key={receipt.id} receipt={receipt} onViewReceipt={onViewReceipt} />
      ))}
    </div>
  );
}
