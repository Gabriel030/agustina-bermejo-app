import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { FULL_DEBT_CODE, RECEIPT_REVIEW_CODES, STALE_INVOICE_CODES } from './creditNotes';

export function CreditNoteViolation({ error, receiptsTo, onReload, onCancelInstead }) {
  const ref = useRef(null);
  useEffect(() => {
    if (error) ref.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [error]);
  if (!error) return null;
  const reviewReceipts = RECEIPT_REVIEW_CODES.includes(error.code);
  const stale = STALE_INVOICE_CODES.includes(error.code);
  return (
    <div ref={ref} role="alert" className="flex scroll-mb-28 flex-col gap-2 rounded-2xl border border-danger/30 bg-danger-bg px-4 py-3 text-sm">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="font-semibold text-ink">{error.title}</span>
          <span className="text-ink-soft">{error.detail}</span>
          {error.code && <span className="text-xs text-ink-faint">{error.code}</span>}
        </div>
      </div>
      {reviewReceipts && receiptsTo && (
        <Button asChild variant="outline" size="sm" className="self-start">
          <Link to={receiptsTo}>Revisar en Comprobantes</Link>
        </Button>
      )}
      {error.code === FULL_DEBT_CODE && onCancelInstead && (
        <Button type="button" variant="outline" size="sm" className="self-start" onClick={onCancelInstead}>
          Cancelar cuota
        </Button>
      )}
      {stale && onReload && (
        <Button type="button" variant="outline" size="sm" className="self-start" onClick={onReload}>
          Recargar la cuota
        </Button>
      )}
    </div>
  );
}
