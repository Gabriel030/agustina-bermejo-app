import { useState } from 'react';
import { ChangesTable } from '@/components/ChangesTable';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Textarea } from '@/components/ui/textarea';
import { Toggle } from '@/components/ui/toggle';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';
import { CORRECTION_REASON_MAX, CORRECTION_REASON_MIN } from '../lib/gradeCorrection';
import { boardCorrectionForm, correctionChangeRows } from '../lib/examBoardGrades';
import { formatScore } from '../lib/gradeGrid';

const STEPS = [
  { key: 'data', label: 'Datos' },
  { key: 'confirm', label: 'Confirmar' },
];

function CorrectionSteps({ row, saving, failure, onClose, onConfirm }) {
  const [step, setStep] = useState('data');
  const [scoreText, setScoreText] = useState('');
  const [absent, setAbsent] = useState(false);
  const [reasonText, setReasonText] = useState('');
  const form = boardCorrectionForm({ row, scoreText, absent, reasonText });
  const counterBad = form.reasonTooLong || form.reasonTooShort;

  return (
    <div className="flex flex-col gap-4">
      <Stepper pasos={STEPS} pasoActual={step} pasosVisitados={step === 'confirm' ? ['data', 'confirm'] : ['data']} />
      {failure && (
        <p role="alert" className="rounded-xl bg-danger-bg px-3 py-2.5 text-[13px] font-semibold text-pill-danger">
          {failure}
        </p>
      )}
      {step === 'data' ? (
        <>
          <div className="flex items-start gap-3">
            <div className="flex flex-1 flex-col gap-1.5">
              <Label htmlFor="board-correction-current">Nota actual</Label>
              <Input id="board-correction-current" value={formatScore(row.finalScore)} readOnly className="text-right" />
            </div>
            <div className="flex flex-1 flex-col gap-1.5">
              <Label htmlFor="board-correction-new">Nota nueva</Label>
              <Input
                id="board-correction-new"
                type="text"
                inputMode="decimal"
                autoComplete="off"
                placeholder={absent ? '—' : '1 a 10'}
                value={absent ? '' : scoreText}
                disabled={absent}
                aria-invalid={form.scoreInvalid || form.unchanged || undefined}
                onChange={(event) => setScoreText(event.target.value)}
                className="text-right"
              />
            </div>
          </div>
          <Toggle
            variant="outline"
            pressed={absent}
            aria-label={`Marcar Ausente a ${row.studentName}`}
            onPressedChange={(pressed) => {
              setAbsent(pressed);
              setScoreText('');
            }}
            className="h-10 w-fit px-3"
          >
            {absent ? 'Ausente ✓' : 'Marcar Ausente'}
          </Toggle>
          {form.scoreInvalid && <p className="text-xs text-danger">Ingresá una nota de 1 a 10 con hasta dos decimales.</p>}
          {form.unchanged && <p className="text-xs text-danger">La nota nueva es igual a la actual.</p>}
          {absent && <p className="text-xs text-ink-soft">Se quita la nota y queda en el historial. Después solo se puede volver a cargar una nota.</p>}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="board-correction-reason">Motivo</Label>
            <Textarea
              id="board-correction-reason"
              rows={3}
              placeholder="Ej.: error de carga en la planilla"
              value={reasonText}
              aria-invalid={counterBad || undefined}
              onChange={(event) => setReasonText(event.target.value)}
            />
            <span className={cn('text-right text-xs', counterBad ? 'text-danger' : 'text-ink-faint')}>{`${reasonText.length}/${CORRECTION_REASON_MAX} · mínimo ${CORRECTION_REASON_MIN}`}</span>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancelar
            </Button>
            <Button type="button" variant="gradient" disabled={!form.ready} onClick={() => setStep('confirm')}>
              Revisar cambios
            </Button>
          </div>
        </>
      ) : (
        <>
          <ChangesTable rows={correctionChangeRows(row, form)} />
          <div className="flex flex-col gap-1">
            <span className="text-xs font-semibold text-ink-soft">Motivo</span>
            <p className="whitespace-pre-wrap border-l-[3px] border-primary pl-3 text-[13px] text-ink-soft">{form.reason}</p>
          </div>
          <p className="border-l-[3px] border-border pl-3 text-[12.5px] leading-relaxed text-ink-soft">
            Se aplica al confirmar y queda en el historial. Hasta que se cierre el acta podés volver a corregirla.
          </p>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" disabled={saving} onClick={() => setStep('data')}>
              Volver
            </Button>
            <Button type="button" variant="gradient" loading={saving} onClick={() => onConfirm(form)}>
              Confirmar corrección
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

export function ExamBoardCorrectionSheet({ row, saving, failure, onClose, onConfirm }) {
  const compact = useMediaQuery('(max-width: 767px)');
  return (
    <Sheet open={Boolean(row)} onOpenChange={(open) => !open && !saving && onClose()}>
      <SheetContent side={compact ? 'bottom' : 'right'} className={cn('overflow-y-auto p-4', compact && 'max-h-[88vh] rounded-t-2xl')}>
        {row && (
          <>
            <SheetHeader className="p-0">
              <SheetTitle>Corregir nota del final</SheetTitle>
              <SheetDescription>{`${row.studentName} · DNI ${row.dni}`}</SheetDescription>
            </SheetHeader>
            <CorrectionSteps key={row.id} row={row} saving={saving} failure={failure} onClose={onClose} onConfirm={onConfirm} />
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
