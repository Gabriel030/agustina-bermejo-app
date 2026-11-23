import { Download } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Notice } from '@/components/Notice';
import { formatearFecha } from '@/lib/comisiones';
import { documentTypeLabel } from '@/features/graduation/debt-exceptions/lib/debtException';

export function IssuedDebtException({ exception, downloading, onDownload, onIssueAnother }) {
  return (
    <div className="flex flex-col gap-4">
      <Notice tone="success" role="status" title="Constancia emitida por excepción.">
        {documentTypeLabel(exception.documentType)} para {exception.studentName}. Se puede usar una sola vez y vence el{' '}
        {formatearFecha(exception.expiresAt)}. Queda registrada de forma destacada en Auditoría.
      </Notice>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button type="button" variant="primary" className="min-h-11" loading={downloading} onClick={onDownload}>
          <Download aria-hidden="true" />
          Descargar para entregar
        </Button>
        <Button type="button" variant="outline" className="min-h-11" disabled={downloading} onClick={onIssueAnother}>
          Emitir otra
        </Button>
      </div>
    </div>
  );
}
