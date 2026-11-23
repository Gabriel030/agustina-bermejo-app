import { useState } from 'react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { Notice } from '@/components/Notice';
import { ResponsiveFormDialog } from '@/features/graduation/certificate-exceptions/components/ResponsiveFormDialog';
import { ReasonField } from '@/features/graduation/certificate-exceptions/components/ExceptionFormFields';
import {
  useApproveCertificateException,
  useRejectCertificateException,
} from '@/features/graduation/certificate-exceptions/hooks/useCertificateExceptions';
import { reasonError } from '@/features/graduation/certificate-exceptions/lib/certificateExceptions';

const COPY = {
  approve: {
    title: 'Aprobar excepción',
    label: 'Aprobar excepción',
    variant: 'primary',
    success: 'Excepción aprobada',
    placeholder: 'Contá por qué aprobás la excepción',
  },
  reject: {
    title: 'Rechazar excepción',
    label: 'Rechazar excepción',
    variant: 'destructive',
    success: 'Excepción rechazada',
    placeholder: 'Contá por qué rechazás la excepción',
  },
};

function descriptionOf(mode, exception) {
  if (mode === 'approve') {
    return `${exception.studentName} va a poder pedir su constancia aunque tenga deuda. Vale 7 días y se usa una sola vez.`;
  }
  return `${exception.requesterName} va a ver este motivo en la campanita.`;
}

export function DecideExceptionDialog({ mode, exception, onClose }) {
  const approve = useApproveCertificateException();
  const reject = useRejectCertificateException();
  const mutation = mode === 'approve' ? approve : reject;
  const copy = COPY[mode];
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [serverError, setServerError] = useState('');
  const error = touched ? reasonError(reason) : '';
  const valid = !reasonError(reason);

  async function submit(event) {
    event.preventDefault();
    if (!valid) return;
    setServerError('');
    try {
      await mutation.mutateAsync({ id: exception.id, reason: reason.trim() });
      toast.success(copy.success);
      onClose();
    } catch (failure) {
      setServerError(describeError(failure).message);
    }
  }

  return (
    <ResponsiveFormDialog title={copy.title} description={descriptionOf(mode, exception)} pending={mutation.isPending} onClose={onClose}>
      <form className="flex flex-col gap-3" onSubmit={submit} noValidate>
        <ReasonField
          id="decision-reason"
          label="Motivo"
          value={reason}
          error={error}
          placeholder={copy.placeholder}
          onChange={(value) => {
            setReason(value);
            setTouched(true);
          }}
          onBlur={() => setTouched(true)}
        />
        {serverError && (
          <Notice tone="danger" role="alert">
            {serverError}
          </Notice>
        )}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" className="min-h-11 md:min-h-0" disabled={mutation.isPending} onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" variant={copy.variant} className="min-h-11 md:min-h-0" disabled={!valid} loading={mutation.isPending}>
            {copy.label}
          </Button>
        </div>
      </form>
    </ResponsiveFormDialog>
  );
}
