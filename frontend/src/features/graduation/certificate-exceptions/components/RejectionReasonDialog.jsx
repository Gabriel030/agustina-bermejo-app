import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/Skeleton';
import { Notice } from '@/components/Notice';
import { ResponsiveFormDialog } from '@/features/graduation/certificate-exceptions/components/ResponsiveFormDialog';
import { useCertificateException } from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';

export function RejectionReasonDialog({ exceptionId, studentName, onClose }) {
  const query = useCertificateException(exceptionId);
  return (
    <ResponsiveFormDialog title="Motivo del rechazo" description={`Excepción pedida para ${studentName}`} onClose={onClose}>
      {query.isLoading && <Skeleton className="h-16 w-full" />}
      {query.isError && <Notice tone="danger" role="alert">No pudimos cargar el motivo. Probá de nuevo en un momento.</Notice>}
      {query.data && <p className="whitespace-pre-wrap text-size-13-5 text-ink">{query.data.decisionReason}</p>}
      <div className="flex justify-end">
        <Button type="button" variant="outline" className="min-h-11 md:min-h-0" onClick={onClose}>
          Cerrar
        </Button>
      </div>
    </ResponsiveFormDialog>
  );
}
