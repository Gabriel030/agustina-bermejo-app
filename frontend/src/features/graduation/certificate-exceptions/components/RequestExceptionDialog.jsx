import { useState } from 'react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { Notice } from '@/components/Notice';
import { ResponsiveFormDialog } from '@/features/graduation/certificate-exceptions/components/ResponsiveFormDialog';
import { OrganismFields, StudentDebtContext } from '@/features/graduation/certificate-exceptions/components/ExceptionFormFields';
import { useExceptionForm } from '@/features/graduation/certificate-exceptions/hooks/useExceptionForm';
import { useRequestCertificateException } from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';

const ALREADY_OPEN_MESSAGE = 'Ya hay un pedido abierto para esta inscripción. Revisá Excepciones antes de pedir otra.';

export function RequestExceptionDialog({ row, onClose }) {
  const form = useExceptionForm();
  const request = useRequestCertificateException();
  const [serverError, setServerError] = useState('');

  async function submit(event) {
    event.preventDefault();
    if (!form.isValid) return;
    setServerError('');
    try {
      await request.mutateAsync({
        enrollmentId: row.enrollmentId,
        kind: row.kind,
        organismKind: form.values.organismKind,
        organismReference: form.values.organismReference.trim(),
        reason: form.values.reason.trim(),
      });
      toast.success('Pedido enviado al Director');
      onClose();
    } catch (error) {
      setServerError(error?.code === 'CERTIFICATE_EXCEPTION_ALREADY_OPEN' ? ALREADY_OPEN_MESSAGE : describeError(error).message);
    }
  }

  return (
    <ResponsiveFormDialog title="Pedir excepción" description="El Director decide si el alumno puede pedir su constancia con deuda." pending={request.isPending} onClose={onClose}>
      <form className="flex flex-col gap-3" onSubmit={submit} noValidate>
        <StudentDebtContext
          studentName={row.studentName}
          programName={row.programName}
          totalDue={row.totalDue}
          overdueInstallments={row.overdueInstallments}
        />
        <OrganismFields values={form.values} errors={form.errors} onChange={form.onChange} onBlur={form.onBlur} />
        {serverError && (
          <Notice tone="danger" role="alert">
            {serverError}
          </Notice>
        )}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" className="min-h-11 md:min-h-0" disabled={request.isPending} onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" variant="primary" className="min-h-11 md:min-h-0" disabled={!form.isValid || request.isPending}>
            {request.isPending ? 'Enviando…' : 'Enviar al Director'}
          </Button>
        </div>
      </form>
    </ResponsiveFormDialog>
  );
}
