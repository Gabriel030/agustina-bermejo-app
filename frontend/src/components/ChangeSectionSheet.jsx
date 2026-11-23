import { useEffect, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Stepper } from '@/components/Stepper';
import { ChangesTable } from '@/components/ChangesTable';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';
import { useChangeSectionCandidates } from '@/hooks/useChangeSectionCandidates';
import { SectionTargetPicker } from '@/components/SectionTargetPicker';
import {
  REASON_MAX_LENGTH,
  REASON_MIN_LENGTH,
  buildChangeRows,
  isFinalError,
  reasonError,
  splitCandidates,
} from '@/lib/enrollmentChanges';

const CHOOSE_STEPS = [
  { key: 'choose', label: 'Elegir comisión' },
  { key: 'confirm', label: 'Confirmar' },
];
const SUBJECT_STEPS = [{ key: 'subject', label: 'Elegir materia' }, ...CHOOSE_STEPS];

const ACTION_BUTTON = 'h-auto min-h-11 md:h-8 md:min-h-0';

function SubjectPicker({ rows, loading, error, onRetry, onPick, onClose }) {
  return (
    <div className="flex flex-col gap-4">
      {loading ? (
        <div className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando materias">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      ) : error ? (
        <EmptyState
          icon="⚠️"
          title="No pudimos cargar tus materias"
          description="Intentá de nuevo en un momento."
          action={
            <Button type="button" variant="outline" className={ACTION_BUTTON} onClick={onRetry}>
              Reintentar
            </Button>
          }
        />
      ) : rows.length === 0 ? (
        <EmptyState icon="🗂️" title="No hay materias para cambiar" description="El cambio de comisión se pide sobre materias que estás cursando." />
      ) : (
        <ul className="flex flex-col gap-1" aria-label="Materias en curso">
          {rows.map((candidateRow) => (
            <li key={candidateRow.id}>
              <Button type="button" variant="row-card" size="row" className="min-h-14 border" onClick={() => onPick(candidateRow.id)}>
                <span className="flex flex-col items-start gap-0.5">
                  <b className="text-size-14 text-ink">{candidateRow.subjectName}</b>
                  <span className="text-size-12 text-ink-faint">{`En curso · Com. ${candidateRow.sectionName}`}</span>
                </span>
                <ChevronRight aria-hidden="true" className="text-ink-faint" />
              </Button>
            </li>
          ))}
        </ul>
      )}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" className={ACTION_BUTTON} onClick={onClose}>
          Cancelar
        </Button>
      </div>
    </div>
  );
}

export function ChangeSectionSheet({
  row: fixedRow,
  rows,
  rowsLoading = false,
  rowsError = false,
  onRetryRows,
  staleTarget = false,
  own = false,
  showReason = true,
  confirmNotice,
  onClose,
  onSubmit,
  pending,
  serverError,
  canChooseAnother = true,
  onChooseAnother,
}) {
  const compact = useMediaQuery('(max-width: 767px)');
  const picksSubject = !fixedRow && !!rows;
  const [step, setStep] = useState(picksSubject ? 'subject' : 'choose');
  const [subjectId, setSubjectId] = useState(null);
  const [targetId, setTargetId] = useState(null);
  const [reason, setReason] = useState('');
  const [reasonShown, setReasonShown] = useState(false);
  const row = fixedRow ?? rows?.find((candidateRow) => candidateRow.id === subjectId) ?? null;
  const candidates = useChangeSectionCandidates(row?.id, !!row, { own });
  const { refetch: refetchCandidates } = candidates;

  const [staleSeen, setStaleSeen] = useState(false);
  if (staleTarget !== staleSeen) {
    setStaleSeen(staleTarget);
    if (staleTarget) {
      setTargetId(null);
      setStep('choose');
    }
  }

  useEffect(() => {
    if (staleTarget) refetchCandidates();
  }, [staleTarget, refetchCandidates]);

  if (!fixedRow && !rows) return null;
  const list = candidates.data ?? [];
  const { available } = splitCandidates(list);
  const target = list.find((candidate) => candidate.sectionId === targetId);
  const localReasonError = showReason ? reasonError(reason) : null;
  const blockingCode = isFinalError(candidates.error?.code) ? candidates.error.code : null;

  function submit() {
    setReasonShown(true);
    if (localReasonError || !target) return;
    onSubmit({
      sectionEnrollmentId: row.id,
      newSectionId: target.sectionId,
      ...(showReason ? { reason: reason.trim() } : {}),
    });
  }

  function pickSubject(id) {
    setSubjectId(id);
    setTargetId(null);
    setStep('choose');
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
          <SheetDescription>{row ? `${row.subjectName} · hoy en Com. ${row.sectionName}` : 'Pasá de una comisión a otra en una materia que cursás.'}</SheetDescription>
        </SheetHeader>
        <Stepper pasos={picksSubject ? SUBJECT_STEPS : CHOOSE_STEPS} pasoActual={step} />

        {step === 'subject' && <SubjectPicker rows={rows} loading={rowsLoading} error={rowsError} onRetry={onRetryRows} onPick={pickSubject} onClose={onClose} />}

        {step === 'choose' && row && (
          <div className="flex flex-col gap-4">
            {candidates.isLoading && (
              <div className="flex flex-col gap-2" aria-busy="true" aria-label="Cargando comisiones">
                <Skeleton className="h-16 w-full" />
                <Skeleton className="h-16 w-full" />
              </div>
            )}
            {candidates.isError && blockingCode && (
              <EmptyState icon="🔒" title="No se puede cambiar de comisión" description={describeError(candidates.error).message} />
            )}
            {candidates.isError && !blockingCode && (
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
            {staleTarget && (
              <Notice tone="danger" role="alert" title="La comisión se llenó mientras confirmabas.">
                Actualizamos la lista: elegí otra.
              </Notice>
            )}
            {candidates.isSuccess && list.length > 0 && (
              <SectionTargetPicker
                candidates={list}
                value={targetId}
                onChange={(next) => {
                  setTargetId(next);
                  if (staleTarget) onChooseAnother?.();
                }}
              />
            )}
            <div className="flex justify-end gap-2">
              {picksSubject && (
                <Button type="button" variant="outline" className={ACTION_BUTTON} onClick={() => setStep('subject')}>
                  Volver
                </Button>
              )}
              <Button type="button" variant="outline" className={ACTION_BUTTON} onClick={onClose}>
                Cancelar
              </Button>
              <Button type="button" variant="primary" className={ACTION_BUTTON} disabled={!target} onClick={() => setStep('confirm')}>
                Continuar
              </Button>
            </div>
          </div>
        )}

        {step === 'confirm' && row && target && (
          <div className="flex flex-col gap-4">
            <ChangesTable rows={buildChangeRows(row, target, { own })} />
            {showReason && (
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
            )}
            {showReason && <p className="text-size-12 text-ink-faint">Se avisa al alumno y al docente.</p>}
            {confirmNotice}
            {serverError && (
              <div role="alert" className="flex flex-col items-start gap-2 text-size-12 font-semibold text-danger">
                {serverError}
                {canChooseAnother && (
                  <Button type="button" variant="outline" size="sm" className={ACTION_BUTTON} onClick={chooseAnother}>
                    Elegir otra comisión
                  </Button>
                )}
              </div>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" className={ACTION_BUTTON} disabled={pending} onClick={() => setStep('choose')}>
                Volver
              </Button>
              <Button type="button" variant="primary" className={ACTION_BUTTON} loading={pending} disabled={!!serverError && !canChooseAnother} onClick={submit}>
                Confirmar el cambio
              </Button>
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
