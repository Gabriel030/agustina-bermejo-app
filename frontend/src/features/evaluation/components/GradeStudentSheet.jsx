import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import {
  GRID_CONDITION,
  draftKey,
  findSavedCell,
  formatScore,
  gradeColumnKey,
  gradeColumnLabel,
  isColumnLocked,
  scoreTextState,
} from '../lib/gradeGrid';

const FINAL_DISABLED_REASON = 'Se carga desde la mesa de examen';

function SheetRow({ student, column, draft, saved, onPatch }) {
  const label = gradeColumnLabel(column);
  const locked = isColumnLocked(column);
  const disabled = column.type === 'FINAL';
  const scoreText = draft ? draft.score : formatScore(saved?.score);
  const hasScore = scoreTextState(scoreText) !== 'empty';
  const comments = draft ? draft.comments : (saved?.comments ?? '');
  const day = draft ? draft.date : (saved?.date ? saved.date.slice(0, 10) : '');
  const id = `${student.enrollmentId}-${gradeColumnKey(column.type, column.sequenceNumber)}`;

  return (
    <div className="flex flex-col gap-2 rounded-2xl border border-border p-3">
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={`${id}-score`}>{label}</Label>
        {locked ? (
          <span className="text-sm text-ink" aria-label={`Nota de ${label}: ${scoreText || 'sin nota'}, publicada`}>
            {scoreText || '—'} · Publicada
          </span>
        ) : disabled ? (
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="text-xs text-ink-faint" tabIndex={0}>
                  {FINAL_DISABLED_REASON}
                </span>
              </TooltipTrigger>
              <TooltipContent>La nota del final se registra desde la mesa de examen.</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        ) : (
          <Input
            id={`${id}-score`}
            type="text"
            inputMode="decimal"
            aria-label={`Nota de ${label}`}
            value={scoreText}
            aria-invalid={scoreTextState(scoreText) === 'invalid' || undefined}
            onChange={(event) => onPatch({ score: event.target.value })}
            className="h-10 w-20 text-right"
          />
        )}
      </div>
      {!disabled && !locked && (
        <>
          <Input
            type="date"
            aria-label={`Fecha de ${label}`}
            value={day}
            disabled={!hasScore}
            onChange={(event) => onPatch({ date: event.target.value })}
            className="h-10"
          />
          <Input
            type="text"
            aria-label={`Comentario de ${label}`}
            placeholder="Comentario (opcional)"
            value={comments}
            disabled={!hasScore}
            onChange={(event) => onPatch({ comments: event.target.value })}
            className="h-10"
          />
        </>
      )}
    </div>
  );
}

export function GradeStudentSheet({ student, columns, drafts, onPatch, onClose }) {
  const condition = student ? GRID_CONDITION[student.condition] : null;
  return (
    <Sheet open={Boolean(student)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="overflow-y-auto p-4">
        {student && (
          <>
            <SheetHeader className="p-0">
              <SheetTitle>{`${student.lastname}, ${student.name}`}</SheetTitle>
              <SheetDescription>DNI {student.dni}</SheetDescription>
              {condition && <StatusPill tone={condition.tone}>{condition.label}</StatusPill>}
            </SheetHeader>
            <div className="flex flex-col gap-3">
              {columns.map((column) => {
                const key = draftKey(student.enrollmentId, column.type, column.sequenceNumber);
                return (
                  <SheetRow
                    key={key}
                    student={student}
                    column={column}
                    draft={drafts[key]}
                    saved={findSavedCell(student, column)}
                    onPatch={(patch) => onPatch(key, findSavedCell(student, column), patch)}
                  />
                );
              })}
            </div>
            <Button type="button" variant="outline" onClick={onClose}>
              Listo
            </Button>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
