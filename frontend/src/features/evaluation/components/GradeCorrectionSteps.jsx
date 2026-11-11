import { useState } from 'react';
import { toast } from 'sonner';
import { Bell, RefreshCw } from 'lucide-react';
import { describeError } from '@/api/describeError';
import { ChangesTable } from '@/components/ChangesTable';
import { Stepper } from '@/components/Stepper';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useCorrectGradeItem } from '../hooks/useGradeCorrection';
import { CORRECTION_REASON_MAX, CORRECTION_REASON_MIN, correctionDifference, correctionErrorOutcome, correctionFormState } from '../lib/gradeCorrection';
import { GRID_CONDITION, formatScore, gradeColumnLabel } from '../lib/gradeGrid';

const STEPS = [
  { key: 'data', label: 'Datos' },
  { key: 'confirm', label: 'Confirmar' },
];

export function GradeCorrectionSteps({ student, column, cell, sectionId, kind, onBack, onDone }) {
  const [step, setStep] = useState('data');
  const [scoreText, setScoreText] = useState('');
  const [reasonText, setReasonText] = useState('');
  const correct = useCorrectGradeItem(sectionId, kind);
  const label = gradeColumnLabel(column);
  const studentName = `${student.lastname}, ${student.name}`;
  const form = correctionFormState({ scoreText, reasonText, currentScore: cell.score });
  const counterBad = form.reasonTooLong || form.reasonTooShort;

  async function confirm() {
    try {
      await correct.mutateAsync({ gradeItemId: cell.gradeItemId, newScore: form.value, reason: form.reason });
      toast.success(`Nota corregida. ${student.name} recibió un aviso y se recalculó su condición.`);
      onDone({ periodEnded: false });
    } catch (error) {
      const outcome = correctionErrorOutcome(error);
      toast.error(outcome ? outcome.message : describeError(error).message);
      onDone({ periodEnded: outcome?.periodEnded ?? false });
    }
  }

  const rows = [
    { key: 'score', label, today: formatScore(cell.score), next: formatScore(form.value), difference: step === 'confirm' ? correctionDifference(cell.score, form.value) : '' },
    { key: 'condition', label: 'Condición', today: GRID_CONDITION[student.condition]?.label ?? '—', next: 'Se recalcula', difference: '—', unchanged: true },
  ];

  return (
    <div className="flex flex-col gap-4">
      <Button variant="link" size="inline" type="button" onClick={onBack} disabled={correct.isPending} className="w-fit text-left text-[13px] font-semibold disabled:opacity-50">
        {`← ${studentName}`}
      </Button>
      <Stepper pasos={STEPS} pasoActual={step} pasosVisitados={step === 'confirm' ? ['data', 'confirm'] : ['data']} />
      <div>
        <h3 className="font-heading text-lg font-semibold text-ink">{step === 'data' ? 'Corregir nota' : 'Confirmar corrección'}</h3>
        <p className="text-[12.5px] text-ink-faint">{`${studentName} · ${label}`}</p>
      </div>
      {step === 'data' ? (
        <>
          <div className="flex items-start gap-3">
            <div className="flex flex-1 flex-col gap-1.5">
              <Label htmlFor="correction-current">Nota actual</Label>
              <Input id="correction-current" value={formatScore(cell.score)} readOnly className="text-right" />
            </div>
            <div className="flex flex-1 flex-col gap-1.5">
              <Label htmlFor="correction-new">Nota nueva</Label>
              <Input
                id="correction-new"
                type="text"
                inputMode="decimal"
                placeholder="1 a 10"
                value={scoreText}
                aria-invalid={form.scoreInvalid || form.unchanged || undefined}
                onChange={(event) => setScoreText(event.target.value)}
                className="text-right"
              />
            </div>
          </div>
          {form.scoreInvalid && <p className="text-xs text-danger">Ingresá una nota de 1 a 10 con hasta dos decimales.</p>}
          {form.unchanged && <p className="text-xs text-danger">La nota nueva es igual a la actual.</p>}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="correction-reason">Motivo</Label>
            <Textarea
              id="correction-reason"
              rows={3}
              placeholder="Ej.: error de carga en la planilla"
              value={reasonText}
              aria-invalid={counterBad || undefined}
              onChange={(event) => setReasonText(event.target.value)}
            />
            <span className={`text-right text-xs ${counterBad ? 'text-danger' : 'text-ink-faint'}`}>{`${reasonText.length}/${CORRECTION_REASON_MAX} · mínimo ${CORRECTION_REASON_MIN}`}</span>
          </div>
          <div className="flex flex-col gap-1.5 text-[12.5px] text-ink-soft">
            <span className="flex items-center gap-2">
              <Bell aria-hidden="true" className="size-3.5" />
              El alumno recibe un aviso con la nota y el motivo.
            </span>
            <span className="flex items-center gap-2">
              <RefreshCw aria-hidden="true" className="size-3.5" />
              Se recalcula su condición.
            </span>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onBack}>
              Cancelar
            </Button>
            <Button type="button" variant="gradient" disabled={!form.ready} onClick={() => setStep('confirm')}>
              Revisar cambios
            </Button>
          </div>
        </>
      ) : (
        <>
          <ChangesTable rows={rows} />
          <div className="flex flex-col gap-1">
            <span className="text-xs font-semibold text-ink-soft">Motivo</span>
            <p className="whitespace-pre-wrap border-l-[3px] border-primary pl-3 text-[13px] text-ink-soft">{form.reason}</p>
          </div>
          <p className="border-l-[3px] border-border pl-3 text-[12.5px] leading-relaxed text-ink-soft">
            Se aplica al confirmar y queda en el historial. Para revertirlo hay que hacer otra corrección.
          </p>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" disabled={correct.isPending} onClick={() => setStep('data')}>
              Volver
            </Button>
            <Button type="button" variant="gradient" disabled={correct.isPending} onClick={confirm}>
              {correct.isPending ? 'Confirmando…' : 'Confirmar corrección'}
            </Button>
          </div>
        </>
      )}
    </div>
  );
}
