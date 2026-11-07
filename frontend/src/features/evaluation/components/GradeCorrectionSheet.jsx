import { useState } from 'react';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { cn } from '@/lib/utils';
import { isCorrectableCell } from '../lib/gradeCorrection';
import { GRID_CONDITION, findSavedCell, formatScore, gradeColumnKey, gradeColumnLabel } from '../lib/gradeGrid';
import { GradeAuditSheetView } from './GradeAuditSheetView';
import { GradeCorrectionSteps } from './GradeCorrectionSteps';

function cellStatus(cell) {
  if (!cell || cell.score == null) return { tone: 'neutral', label: 'Sin nota' };
  return cell.published ? { tone: 'success', label: 'Publicada' } : { tone: 'warning', label: 'Sin publicar' };
}

function ItemRow({ column, cell, highlighted, periodEnded, onHistory, onCorrect }) {
  const label = gradeColumnLabel(column);
  const status = cellStatus(cell);
  const published = cell?.published === true && cell.score != null;
  const correctable = isCorrectableCell(cell) && !periodEnded;
  const reason = periodEnded ? 'El plazo de corrección terminó' : 'Solo se corrigen notas publicadas';
  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-2 rounded-2xl border border-border p-3', highlighted && 'bg-accent-soft')}>
      <div className="min-w-[96px] flex-1">
        <b className="text-sm text-ink">{label}</b>
      </div>
      <span className="min-w-10 text-right font-heading text-lg font-bold tabular-nums text-ink" aria-label={`Nota de ${label}: ${cell?.score == null ? 'sin nota' : formatScore(cell.score)}`}>
        {cell?.score == null ? '—' : formatScore(cell.score)}
      </span>
      <StatusPill tone={status.tone}>{status.label}</StatusPill>
      {published && (
        <Button type="button" variant="ghost" size="sm" aria-label={`Historial de ${label}`} onClick={() => onHistory(column)}>
          Historial
        </Button>
      )}
      {cell?.score != null &&
        (correctable ? (
          <Button type="button" variant="outline" size="sm" aria-label={`Corregir ${label}`} onClick={() => onCorrect(column)}>
            Corregir
          </Button>
        ) : (
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                <span tabIndex={0} className="inline-flex rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                  <Button type="button" variant="outline" size="sm" disabled aria-label={`Corregir ${label}`}>
                    Corregir
                  </Button>
                </span>
              </TooltipTrigger>
              <TooltipContent>{reason}</TooltipContent>
            </Tooltip>
          </TooltipProvider>
        ))}
    </div>
  );
}

export function GradeCorrectionSheet({ student, columns, anchorKey, sectionId, kind, periodEnded, onPeriodEnded, onClose }) {
  const compact = useMediaQuery('(max-width: 767px)');
  const [view, setView] = useState({ name: 'list' });
  const condition = student ? GRID_CONDITION[student.condition] : null;
  const column = view.columnKey ? columns.find((candidate) => gradeColumnKey(candidate.type, candidate.sequenceNumber) === view.columnKey) : null;
  const cell = student && column ? findSavedCell(student, column) : null;
  const backToList = () => setView({ name: 'list' });

  function openView(name, target) {
    setView({ name, columnKey: gradeColumnKey(target.type, target.sequenceNumber) });
  }

  function handleDone({ periodEnded: ended }) {
    if (ended) onPeriodEnded();
    backToList();
  }

  function handleOpenChange(open) {
    if (!open) {
      backToList();
      onClose();
    }
  }

  return (
    <Sheet open={Boolean(student)} onOpenChange={handleOpenChange}>
      <SheetContent side={compact ? 'bottom' : 'right'} className={cn('overflow-y-auto p-4', compact && 'max-h-[88vh] rounded-t-2xl')}>
        {student && (
          <>
            <SheetHeader className="p-0">
              <SheetTitle>{`${student.lastname}, ${student.name}`}</SheetTitle>
              <SheetDescription>{`DNI ${student.dni}`}</SheetDescription>
              {condition && <StatusPill tone={condition.tone}>{condition.label}</StatusPill>}
            </SheetHeader>
            {view.name === 'correct' && cell && (
              <GradeCorrectionSteps student={student} column={column} cell={cell} sectionId={sectionId} kind={kind} onBack={backToList} onDone={handleDone} />
            )}
            {view.name === 'history' && cell && <GradeAuditSheetView student={student} column={column} cell={cell} onBack={backToList} />}
            {(view.name === 'list' || !cell) && (
              <div className="flex flex-col gap-2">
                {columns.map((item) => {
                  const key = gradeColumnKey(item.type, item.sequenceNumber);
                  return (
                    <ItemRow
                      key={key}
                      column={item}
                      cell={findSavedCell(student, item)}
                      highlighted={key === anchorKey}
                      periodEnded={periodEnded}
                      onHistory={(target) => openView('history', target)}
                      onCorrect={(target) => openView('correct', target)}
                    />
                  );
                })}
              </div>
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
