import { useEffect, useRef, useState } from 'react';
import { useForm, useFormState, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { toast } from 'sonner';
import { describeError } from '@/api/describeError';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { Stepper } from '@/components/Stepper';
import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet';
import { EquivalenceErrorSummary } from '@/features/workspace/components/EquivalenceFormField';
import { EquivalenceRecordFields } from '@/features/workspace/components/EquivalenceRecordFields';
import { EquivalenceReview } from '@/features/workspace/components/EquivalenceReview';
import { canRecordFor, computeEquivalenceEffect, equivalenceFieldForCode } from '@/features/workspace/lib/equivalenceEffect';
import {
  EMPTY_EQUIVALENCE_VALUES,
  EQUIVALENCE_FIELD_LABELS,
  EQUIVALENCE_FIELD_ORDER,
  equivalenceFieldId,
  equivalenceFormSchema,
  parseScore,
  toRecordPayload,
} from '@/features/workspace/lib/equivalenceForm';
import { useEquivalenceAccess, useRecordSubjectEquivalence, useStaffCurriculumProgress } from '@/features/enrollment';
import { useMediaQuery } from '@/hooks/useMediaQuery';

const STEPS = [
  { key: 'changes', label: 'Cambios' },
  { key: 'review', label: 'Confirmar' },
];

const DENIED_MESSAGE = 'No tenés permiso para cargar equivalencias.';

function CloseButton({ disabled, onClick }) {
  return (
    <Button type="button" variant="ghost" size="icon" className="absolute top-2 right-2 size-11" disabled={disabled} onClick={onClick}>
      <X aria-hidden="true" />
      <span className="sr-only">Cerrar ventana</span>
    </Button>
  );
}

function RecordFooter({ step, pending, denied, conflict, onCancel, onBack, onClose, onSubmitReview, onSave }) {
  if (conflict) {
    return (
      <div className="flex justify-end">
        <Button type="button" variant="primary" className="min-h-11" onClick={onClose}>
          Cerrar
        </Button>
      </div>
    );
  }
  return (
    <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
      <Button type="button" variant="outline" className="min-h-11" disabled={pending} onClick={onCancel}>
        Cancelar
      </Button>
      {step === 'review' && (
        <Button type="button" variant="outline" className="min-h-11" disabled={pending} onClick={onBack}>
          Volver
        </Button>
      )}
      {step === 'review' ? (
        <Button key="save" type="button" variant="primary" className="min-h-11" loading={pending} disabled={denied} onClick={onSave}>
          {pending ? 'Guardando…' : 'Cargar equivalencia'}
        </Button>
      ) : (
        <Button key="review" type="button" variant="primary" className="min-h-11" disabled={denied} onClick={onSubmitReview}>
          Revisar
        </Button>
      )}
    </div>
  );
}

export function EquivalenceRecordDialog({ request, studentId, programEnrollmentId, onClose, onCloseAutoFocus }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const progress = useStaffCurriculumProgress(programEnrollmentId);
  const access = useEquivalenceAccess();
  const record = useRecordSubjectEquivalence(programEnrollmentId);
  const [step, setStep] = useState('changes');
  const [summaryVisible, setSummaryVisible] = useState(false);
  const [serverError, setServerError] = useState(null);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const focusRequest = useRef(null);
  const form = useForm({
    resolver: zodResolver(equivalenceFormSchema),
    defaultValues: { ...EMPTY_EQUIVALENCE_VALUES, programSubjectId: request.subjectId != null ? String(request.subjectId) : '' },
  });
  const { register, handleSubmit, setError, setValue, getValues, control } = form;
  const { errors, isDirty } = useFormState({ control });
  const pending = record.isPending;
  const denied = !access.record;
  const planSubjects = progress.data?.subjects ?? [];
  const eligibleSubjects = planSubjects.filter(canRecordFor);
  const values = useWatch({ control });

  useEffect(() => {
    if (step === 'changes' && focusRequest.current) {
      document.getElementById(equivalenceFieldId(focusRequest.current))?.focus();
      focusRequest.current = null;
    }
  }, [step]);

  function requestClose() {
    if (pending) return;
    if (isDirty && !serverError?.conflict) setConfirmDiscard(true);
    else onClose();
  }

  function goToReview(submitted) {
    setSummaryVisible(false);
    setServerError(null);
    if (computeEquivalenceEffect(progress.data, Number(submitted.programSubjectId), parseScore(submitted.originScore))) setStep('review');
  }

  function rejectInvalid(invalid) {
    setSummaryVisible(true);
    const first = EQUIVALENCE_FIELD_ORDER.find((name) => invalid[name]);
    if (first) document.getElementById(equivalenceFieldId(first))?.focus();
  }

  function save() {
    const subject = planSubjects.find((candidate) => candidate.programSubjectId === Number(getValues('programSubjectId')));
    setServerError(null);
    record.mutate(toRecordPayload(getValues(), subject), {
      onSuccess: () => {
        toast.success('Equivalencia cargada');
        onClose();
      },
      onError: (error) => {
        const field = error.status === 422 ? equivalenceFieldForCode(error.code) : null;
        if (field) {
          setError(field, { type: 'server', message: describeError(error).message });
          setSummaryVisible(true);
          focusRequest.current = field;
          setStep('changes');
          return;
        }
        setServerError({ message: describeError(error).message, conflict: error.status === 409 });
      },
    });
  }

  const effect = step === 'review' && progress.data ? computeEquivalenceEffect(progress.data, Number(values.programSubjectId), parseScore(values.originScore)) : null;
  const alertMessage = denied ? DENIED_MESSAGE : serverError?.message;

  const body = progress.isLoading ? (
    <div className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando el plan">
      <Skeleton className="h-11" />
      <Skeleton className="h-11" />
      <Skeleton className="h-11" />
    </div>
  ) : progress.isError ? (
    <LoadErrorState error={progress.error} onRetry={progress.refetch} resource="el plan de estudio" bare distinguishForbidden />
  ) : (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        if (step === 'changes') handleSubmit(goToReview, rejectInvalid)();
      }}
      className="flex flex-col gap-4"
    >
      <Stepper pasos={STEPS} pasoActual={step} pasosVisitados={step === 'review' ? ['changes', 'review'] : ['changes']} />
      <p className="text-size-12 font-semibold text-ink-soft">Paso {step === 'changes' ? 1 : 2} de 2</p>
      <fieldset disabled={pending} className="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
        {step === 'changes' ? (
          <>
            {summaryVisible && <EquivalenceErrorSummary errors={errors} fieldLabels={EQUIVALENCE_FIELD_LABELS} fieldOrder={EQUIVALENCE_FIELD_ORDER} />}
            <EquivalenceRecordFields
              register={register}
              errors={errors}
              eligibleSubjects={eligibleSubjects}
              studentId={studentId}
              documentValue={values.studentDocumentId}
              onDocumentChange={(next) => setValue('studentDocumentId', next, { shouldDirty: true })}
              reasonLength={values.reason.length}
            />
          </>
        ) : (
          effect && <EquivalenceReview effect={effect} />
        )}
      </fieldset>
      <span className="sr-only" aria-live="polite">
        {pending ? 'Guardando…' : ''}
      </span>
      {alertMessage && (
        <div role="alert" className="rounded-xl bg-danger-bg px-3 py-2.5 text-size-13 font-semibold text-ink">
          {alertMessage}
        </div>
      )}
      <RecordFooter
        step={step}
        pending={pending}
        denied={denied}
        conflict={Boolean(serverError?.conflict)}
        onCancel={requestClose}
        onBack={() => setStep('changes')}
        onClose={onClose}
        onSubmitReview={() => handleSubmit(goToReview, rejectInvalid)()}
        onSave={save}
      />
    </form>
  );

  const title = 'Cargar equivalencia';
  const description = 'Reconocé una materia aprobada en otra institución.';
  const contentProps = { showCloseButton: false, onCloseAutoFocus };

  return (
    <>
      {compact ? (
        <Sheet open onOpenChange={(next) => !next && requestClose()}>
          <SheetContent side="bottom" {...contentProps} className="max-h-[92dvh] overflow-y-auto px-4 pb-6">
            <div className="flex flex-col gap-1 pr-12 pt-1">
              <SheetTitle>{title}</SheetTitle>
              <SheetDescription>{description}</SheetDescription>
            </div>
            <CloseButton disabled={pending} onClick={requestClose} />
            {body}
          </SheetContent>
        </Sheet>
      ) : (
        <Dialog open onOpenChange={(next) => !next && requestClose()}>
          <DialogContent {...contentProps} className="max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-xl">
            <div className="flex flex-col gap-1 pr-12">
              <DialogTitle className="font-heading text-base font-medium">{title}</DialogTitle>
              <DialogDescription>{description}</DialogDescription>
            </div>
            <CloseButton disabled={pending} onClick={requestClose} />
            {body}
          </DialogContent>
        </Dialog>
      )}
      <ConfirmActionDialog
        open={confirmDiscard}
        onOpenChange={setConfirmDiscard}
        title="¿Descartar lo cargado?"
        description="Si cerrás ahora se pierden los datos de la equivalencia."
        confirmLabel="Descartar"
        cancelLabel="Seguir editando"
        buttonClassName="min-h-11"
        onConfirm={() => {
          setConfirmDiscard(false);
          onClose();
        }}
      />
    </>
  );
}
