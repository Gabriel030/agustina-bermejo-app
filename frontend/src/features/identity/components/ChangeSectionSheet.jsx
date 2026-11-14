import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Stepper } from '@/components/Stepper';
import { ChangesTable } from '@/components/ChangesTable';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';
import { useChangeSectionCandidates } from '@/features/identity/hooks/useEnrollmentChanges';
import { SectionTargetPicker } from '@/features/identity/components/SectionTargetPicker';
import {
  REASON_MAX_LENGTH,
  REASON_MIN_LENGTH,
  buildChangeRows,
  reasonError,
  splitCandidates,
} from '@/features/identity/lib/enrollmentChanges';

const STEPS = [
  { key: 'choose', label: 'Elegir comisión' },
  { key: 'confirm', label: 'Confirmar' },
];

export function ChangeSectionSheet({ row, onClose, onSubmit, pending, serverError, onChooseAnother }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const [step, setStep] = useState('choose');
  const [targetId, setTargetId] = useState(null);
  const [reason, setReason] = useState('');
  const [reasonShown, setReasonShown] = useState(false);
  const candidates = useChangeSectionCandidates(row?.id, !!row);

  if (!row) return null;
  const list = candidates.data ?? [];
  const { available } = splitCandidates(list);
  const target = list.find((candidate) => candidate.sectionId === targetId);
  const localReasonError = reasonError(reason);

  function submit() {
    setReasonShown(true);
    if (localReasonError || !target) return;
    onSubmit({ sectionEnrollmentId: row.id, newSectionId: target.sectionId, reason: reason.trim() });
  }

  function chooseAnother() {
    setStep('choose');
    onChooseAnother?.();
  }

  return (
    <Sheet open onOpenChange={(open) => !open && !pending && onClose()}>
      <SheetContent side={compact ? 'bottom' : 'right'} className={cn('overflow-y-auto p-4', compact && 'max-h-[88vh] rounded-t-2xl')}>
        <SheetHeader>
          <SheetTitle>Cambiar de comisión</SheetTitle>
          <SheetDescription>{`${row.subjectName} · hoy en Com. ${row.sectionName}`}</SheetDescription>
        </SheetHeader>
        <Stepper pasos={STEPS} pasoActual={step} />

        {step === 'choose' && (
          <div className="flex flex-col gap-4">
            {candidates.isLoading && (
              <div className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando comisiones">
                <Skeleton className="h-16 w-full" />
                <Skeleton className="h-16 w-full" />
              </div>
            )}
            {candidates.isError && (
              <EmptyState
                icon="⚠️"
                title="No pudimos cargar las comisiones"
                description="Intentá de nuevo en un momento."
                action={
                  <Button type="button" variant="outline" onClick={() => candidates.refetch()}>
                    Reintentar
                  </Button>
                }
              />
            )}
            {candidates.isSuccess && available.length === 0 && (
              <EmptyState icon="🗂️" title="No hay otra comisión disponible" description="Esta materia no tiene otra comisión a la que se pueda pasar." />
            )}
            {candidates.isSuccess && list.length > 0 && <SectionTargetPicker candidates={list} value={targetId} onChange={setTargetId} />}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={onClose}>
                Cancelar
              </Button>
              <Button type="button" variant="gradient" disabled={!target} onClick={() => setStep('confirm')}>
                Continuar
              </Button>
            </div>
          </div>
        )}

        {step === 'confirm' && target && (
          <div className="flex flex-col gap-4">
            <ChangesTable rows={buildChangeRows(row, target)} />
            <div className="flex flex-col gap-1">
              <Label htmlFor="change-section-reason">Motivo</Label>
              <Textarea
                id="change-section-reason"
                rows={3}
                maxLength={REASON_MAX_LENGTH}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                placeholder="Motivo del cambio"
                aria-invalid={reasonShown && !!localReasonError}
              />
              <div className="text-right text-xs tabular-nums text-ink-faint" aria-live="polite">
                <span className="mr-2">{`mínimo ${REASON_MIN_LENGTH}`}</span>
                <span>
                  {reason.length}/{REASON_MAX_LENGTH}
                </span>
              </div>
              {reasonShown && localReasonError && (
                <span role="alert" className="text-size-12 font-semibold text-danger">
                  {localReasonError}
                </span>
              )}
            </div>
            <p className="text-size-12 text-ink-faint">Se avisa al alumno y al docente.</p>
            {serverError && (
              <div role="alert" className="flex flex-col items-start gap-2 text-size-12 font-semibold text-danger">
                {serverError}
                <Button type="button" variant="outline" size="sm" onClick={chooseAnother}>
                  Elegir otra comisión
                </Button>
              </div>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" disabled={pending} onClick={() => setStep('choose')}>
                Volver
              </Button>
              <Button type="button" variant="gradient" loading={pending} onClick={submit}>
                Confirmar el cambio
              </Button>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
