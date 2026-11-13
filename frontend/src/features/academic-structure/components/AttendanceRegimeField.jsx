import { useId } from 'react';
import { Info } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { evaluateAttendanceRegime, helperFor } from '@/features/academic-structure/attendanceRegime';

const TONE_CLASSES = {
  ok: 'bg-success-bg text-success',
  warn: 'bg-warning-bg text-warning',
  bad: 'bg-danger-bg text-danger',
};

export function AttendanceRegimeField({ value, onChange, lockUnlimited = false }) {
  const fieldId = useId();
  const evaluation = evaluateAttendanceRegime(value);
  const helper = value.mode === 'limit' ? helperFor(evaluation) : null;
  const invalid = evaluation.status === 'invalid' || evaluation.status === 'empty';

  return (
    <fieldset className="mt-3 flex min-w-0 flex-col gap-3 rounded-2xl border border-surface-border px-4 py-3.5">
      <legend className="px-2 text-size-11 font-semibold uppercase tracking-wide text-ink-faint">Régimen de asistencia</legend>
      <div className="flex items-center gap-2">
        <b className="text-size-13-5">Máximo de ausentismo</b>
        <Popover>
          <PopoverTrigger asChild>
            <button
              type="button"
              aria-label="Qué significa el máximo de ausentismo"
              className="flex h-6 w-6 items-center justify-center rounded-full border border-surface-border text-ink-soft hover:border-accent"
            >
              <Info className="h-3.5 w-3.5" />
            </button>
          </PopoverTrigger>
          <PopoverContent align="start" className="w-72 text-size-12-5">
            Se calcula sobre las clases ya dictadas. Las ausencias justificadas no cuentan. Al superar el máximo, el alumno figura como libre en esta comisión.
          </PopoverContent>
        </Popover>
      </div>

      <RadioGroup
        value={value.mode}
        onValueChange={(mode) => onChange({ ...value, mode })}
        aria-label="Régimen de asistencia"
        className="gap-3"
      >
        <Label
          htmlFor={`${fieldId}-none`}
          className={`w-auto items-start gap-2.5 ${lockUnlimited ? 'cursor-not-allowed opacity-55' : 'cursor-pointer'}`}
        >
          <RadioGroupItem id={`${fieldId}-none`} value="none" disabled={lockUnlimited} className="mt-0.5" />
          <span className="flex flex-col">
            <b className="text-size-13-5">Sin límite</b>
            <span className="text-size-12-5 font-normal text-ink-soft">Nadie queda libre por inasistencias.</span>
          </span>
        </Label>
        <Label htmlFor={`${fieldId}-limit`} className="w-auto cursor-pointer items-start gap-2.5">
          <RadioGroupItem id={`${fieldId}-limit`} value="limit" className="mt-0.5" />
          <b className="text-size-13-5">Con límite de ausentismo</b>
        </Label>
      </RadioGroup>

      {value.mode === 'limit' && (
        <div className="flex flex-col gap-2 pl-6">
          <div className="relative w-36">
            <Input
              type="number"
              min="0"
              max="100"
              step="any"
              inputMode="decimal"
              placeholder="25"
              aria-label="Porcentaje máximo de ausentismo"
              aria-describedby={`${fieldId}-help`}
              aria-invalid={invalid ? 'true' : undefined}
              value={value.percent}
              onChange={(e) => onChange({ ...value, percent: e.target.value })}
              className="pr-9"
            />
            <span aria-hidden="true" className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-faint">%</span>
          </div>
          <p id={`${fieldId}-help`} aria-live="polite" className={`rounded-xl px-3 py-2 text-size-12-5 ${TONE_CLASSES[helper.tone]}`}>
            {helper.text}
          </p>
          <p className="rounded-xl bg-warning-bg px-3 py-2 text-size-12-5 text-warning">
            Una vez guardado, el límite se puede cambiar pero no quitar. Para volver a &ldquo;Sin límite&rdquo; hay que crear una comisión nueva.
          </p>
        </div>
      )}
    </fieldset>
  );
}
