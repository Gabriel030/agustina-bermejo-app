import { Lock, Pencil, UserMinus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Toggle } from '@/components/ui/toggle';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { formatScore, scoreTextState } from '../lib/gradeGrid';
import { isBlockedRow, isGradableRow } from '../lib/examBoardGrades';

export const NOT_REGULAR_REASON = 'Requiere condición regular: perdió la regularidad';
export const CLOSED_CORRECTION_REASON = 'El acta está cerrada: la nota ya no se puede corregir';
export const SAVED_ABSENT_HINT = 'Ausente guardado: solo puede pasar a una nota';

function focusSibling(input, step) {
  const grid = input.closest('[data-board-grid]');
  const inputs = [...grid.querySelectorAll('input[data-board-grade]:not(:disabled)')];
  inputs[inputs.indexOf(input) + step]?.focus();
}

function WithReason({ reason, children }) {
  if (!reason) return <>{children}</>;
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span tabIndex={0} aria-label={reason} className="inline-flex items-center gap-2">
            {children}
          </span>
        </TooltipTrigger>
        <TooltipContent>{reason}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

export function ExamBoardStudentCell({ row }) {
  return (
    <div className="flex flex-col items-start gap-0.5">
      <span className="flex items-center gap-1.5 text-size-13 font-semibold leading-tight text-ink">
        {row.studentName}
        {isBlockedRow(row) && (
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span tabIndex={0} role="img" aria-label={NOT_REGULAR_REASON} className="inline-flex text-warning">
                  <Lock aria-hidden="true" className="size-3.5" />
                </span>
              </TooltipTrigger>
              <TooltipContent>{NOT_REGULAR_REASON}</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        )}
      </span>
      <span className="text-size-11 font-normal text-ink-faint">
        DNI {row.dni} · {row.attemptNumber}.º intento
      </span>
    </div>
  );
}

export function ExamBoardGradeCell({ row, draft, mode, lockedReason, error, onChange }) {
  if (!isGradableRow(row)) {
    return <span className="text-sm font-semibold text-ink">{row.finalScore == null ? '—' : formatScore(row.finalScore)}</span>;
  }
  if (mode === 'read') return <span className="text-sm text-ink-faint">—</span>;

  const locked = mode === 'locked';
  const absent = draft ? draft.absent : row.status === 'ABSENT';
  const text = draft?.text ?? '';
  const invalid = Boolean(draft) && !absent && scoreTextState(text) === 'invalid';
  const errorId = `board-error-${row.id}`;
  const showError = invalid || error;

  function handleKeyDown(event) {
    if (event.key === 'Enter' || event.key === 'ArrowDown') {
      event.preventDefault();
      focusSibling(event.currentTarget, event.shiftKey ? -1 : 1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      focusSibling(event.currentTarget, -1);
    } else if (event.key === 'a' || event.key === 'A') {
      event.preventDefault();
      const input = event.currentTarget;
      onChange(row, { absent: true, text: '' });
      focusSibling(input, 1);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <WithReason reason={locked ? lockedReason : null}>
        <Input
          type="text"
          inputMode="decimal"
          autoComplete="off"
          data-board-grade=""
          aria-label={`Nota del final de ${row.studentName}`}
          aria-invalid={showError ? true : undefined}
          aria-describedby={showError ? errorId : undefined}
          placeholder={absent ? '—' : '1 a 10'}
          value={absent ? '' : text}
          disabled={locked || absent}
          onChange={(event) => onChange(row, { text: event.target.value, absent: false })}
          onKeyDown={handleKeyDown}
          className="h-10 w-20 px-1.5 text-center text-sm font-semibold"
        />
        <Toggle
          variant="outline"
          pressed={absent}
          disabled={locked}
          aria-label={`Marcar ausente a ${row.studentName}`}
          onPressedChange={(pressed) => onChange(row, { absent: pressed, text: '' })}
          className="h-10 px-3"
        >
          {absent ? 'Ausente ✓' : 'Ausente'}
        </Toggle>
      </WithReason>
      {!locked && !draft && row.status === 'ABSENT' && <span className="text-size-12 text-ink-faint">{SAVED_ABSENT_HINT}</span>}
      {showError && (
        <span id={errorId} role={error ? 'alert' : undefined} className="text-size-12 text-danger">
          {error ?? 'Entre 1 y 10'}
        </span>
      )}
    </div>
  );
}

export function ExamBoardWithdrawButton({ row, onWithdraw, pending }) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            disabled={pending}
            aria-label={`Dar de baja a ${row.studentName}`}
            onClick={() => onWithdraw(row.id)}
            className="text-ink-soft"
          >
            <UserMinus aria-hidden="true" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Dar de baja de la mesa</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

export function ExamBoardCorrectButton({ row, access, onCorrect }) {
  if (access === 'enabled') {
    return (
      <Button type="button" variant="outline" size="sm" aria-label={`Corregir la nota de ${row.studentName}`} onClick={() => onCorrect(row)}>
        <Pencil aria-hidden="true" />
        <span className="sr-only md:not-sr-only">Corregir</span>
      </Button>
    );
  }
  return (
    <WithReason reason={CLOSED_CORRECTION_REASON}>
      <Button type="button" variant="outline" size="sm" disabled aria-label={`Corregir la nota de ${row.studentName}`}>
        <Pencil aria-hidden="true" />
        <span className="sr-only md:not-sr-only">Corregir</span>
      </Button>
    </WithReason>
  );
}
