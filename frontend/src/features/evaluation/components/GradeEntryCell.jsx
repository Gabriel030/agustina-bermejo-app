import { Check, Loader2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { correctionOf, formatScore, scoreTextState } from '../lib/gradeGrid';

export function GradeEntryCell({ label, studentName, saved, draft, status, disabledReason, locked = false, onChange, onBlur, onRetry }) {
  const text = draft ? draft.score : formatScore(saved?.score);
  const invalid = draft ? scoreTextState(draft.score) === 'invalid' : false;
  const correction = correctionOf(draft, saved);
  const disabled = Boolean(disabledReason) || status === 'saving';

  const input = (
    <Input
      type="text"
      inputMode="decimal"
      aria-label={`${label} de ${studentName}`}
      aria-invalid={invalid || undefined}
      value={text}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value)}
      onBlur={onBlur}
      className={`h-10 w-14 px-1.5 text-right text-sm ${draft && !invalid ? 'border-primary' : ''}`}
    />
  );

  if (locked) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <Input
              type="text"
              readOnly
              aria-label={`${label} de ${studentName}`}
              aria-describedby="published-grade-note"
              value={text}
              className="h-10 w-14 cursor-not-allowed px-1.5 text-right text-sm opacity-70"
            />
          </TooltipTrigger>
          <TooltipContent>Publicada: solo Dirección puede corregirla</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  return (
    <div className="inline-flex flex-col items-end gap-0.5">
      {disabledReason ? (
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <span tabIndex={0} aria-label={disabledReason}>
                {input}
              </span>
            </TooltipTrigger>
            <TooltipContent>{disabledReason}</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      ) : (
        input
      )}
      {correction && (
        <span className="whitespace-nowrap text-size-10 text-ink-faint">
          {formatScore(correction.previous)} → {formatScore(correction.next)}
        </span>
      )}
      {status === 'saving' && <Loader2 aria-label="Guardando" className="size-3 animate-spin text-ink-faint" />}
      {status === 'saved' && <Check aria-label="Guardada" className="size-3 text-success" />}
      {status === 'error' && (
        <Button type="button" variant="ghost" size="xs" className="h-7 px-1.5 text-size-11 text-danger" onClick={onRetry}>
          Reintentar
        </Button>
      )}
    </div>
  );
}
