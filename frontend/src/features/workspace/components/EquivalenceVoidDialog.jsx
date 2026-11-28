import { useState } from 'react';
import { useForm, useFormState, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { EquivalenceFormField } from '@/features/workspace/components/EquivalenceFormField';
import { equivalenceTitle } from '@/features/workspace/lib/subjectEquivalences';
import { REASON_MAX_LENGTH, REASON_MIN_LENGTH, voidFormSchema } from '@/features/workspace/lib/equivalenceForm';
import { useEquivalenceAccess, useVoidSubjectEquivalence } from '@/features/enrollment';

const DENIED_MESSAGE = 'No tenés permiso para anular equivalencias.';

export function EquivalenceVoidDialog({ request, programEnrollmentId, onClose, onCloseAutoFocus }) {
  const { equivalence } = request;
  const access = useEquivalenceAccess();
  const voidEquivalence = useVoidSubjectEquivalence(programEnrollmentId);
  const [serverError, setServerError] = useState(null);
  const { register, handleSubmit, setError, setFocus, control } = useForm({
    resolver: zodResolver(voidFormSchema),
    defaultValues: { reason: '' },
  });
  const { errors } = useFormState({ control });
  const pending = voidEquivalence.isPending;
  const denied = !access.void;
  const conflict = Boolean(serverError?.conflict);
  const alertMessage = denied ? DENIED_MESSAGE : serverError?.message;
  const reasonLength = (useWatch({ control, name: 'reason' }) ?? '').length;

  function requestClose() {
    if (!pending) onClose();
  }

  function submit(values) {
    setServerError(null);
    voidEquivalence.mutate(
      { equivalenceId: equivalence.id, reason: values.reason.trim() },
      {
        onSuccess: () => {
          toast.success('Equivalencia anulada');
          onClose();
        },
        onError: (error) => {
          if (error.status === 422 && error.code === 'EQUIVALENCE_REASON_INVALID') {
            setError('reason', { type: 'server', message: describeError(error).message });
            setFocus('reason');
            return;
          }
          setServerError({ message: describeError(error).message, conflict: error.status === 409 });
        },
      },
    );
  }

  return (
    <Dialog open onOpenChange={(next) => !next && requestClose()}>
      <DialogContent showCloseButton={false} onCloseAutoFocus={onCloseAutoFocus} className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-md">
        <div className="flex flex-col gap-1 pr-12">
          <DialogTitle className="font-heading text-base font-medium">Anular equivalencia</DialogTitle>
          <DialogDescription>{equivalenceTitle(equivalence)}</DialogDescription>
        </div>
        <Button type="button" variant="ghost" size="icon" className="absolute top-2 right-2 size-11" disabled={pending} onClick={requestClose}>
          <X aria-hidden="true" />
          <span className="sr-only">Cerrar ventana</span>
        </Button>
        <form noValidate onSubmit={handleSubmit(submit)} className="flex flex-col gap-4">
          <p className="rounded-xl bg-warning-bg px-3 py-2.5 text-size-13 text-ink">
            Al anularla, la materia vuelve a su estado anterior. Las materias dependientes en curso o aprobadas no se modifican.
          </p>
          <fieldset disabled={pending} className="m-0 min-w-0 border-0 p-0">
            <EquivalenceFormField
              name="reason"
              label="Motivo de la anulación"
              hint={`${reasonLength} de ${REASON_MAX_LENGTH} caracteres · mínimo ${REASON_MIN_LENGTH}`}
              error={errors.reason?.message}
            >
              {(control) => <Textarea rows={3} {...control} {...register('reason')} />}
            </EquivalenceFormField>
          </fieldset>
          <span className="sr-only" aria-live="polite">
            {pending ? 'Guardando…' : ''}
          </span>
          {alertMessage && (
            <div role="alert" className="rounded-xl bg-danger-bg px-3 py-2.5 text-size-13 font-semibold text-ink">
              {alertMessage}
            </div>
          )}
          {conflict ? (
            <div className="flex justify-end">
              <Button type="button" variant="primary" className="min-h-11" onClick={onClose}>
                Cerrar
              </Button>
            </div>
          ) : (
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button type="button" variant="outline" className="min-h-11" disabled={pending} onClick={requestClose}>
                Cancelar
              </Button>
              <Button type="submit" variant="destructive" className="min-h-11" loading={pending} disabled={denied}>
                {pending ? 'Guardando…' : 'Anular equivalencia'}
              </Button>
            </div>
          )}
        </form>
      </DialogContent>
    </Dialog>
  );
}
