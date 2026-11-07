import { formatScore } from '../lib/gradeGrid';

export function GradeReadCell({ label, studentName, cell, onOpen }) {
  const hasScore = cell?.score != null;
  const unpublished = hasScore && cell.published !== true;
  const description = `${label} de ${studentName}: ${hasScore ? formatScore(cell.score) : 'sin nota'}${cell?.corrected ? ', corregida' : ''}${unpublished ? ', sin publicar' : ''}`;
  return (
    <button
      type="button"
      aria-label={description}
      onClick={onOpen}
      className={`inline-flex min-h-10 min-w-11 items-center justify-end gap-1 rounded-lg px-1.5 text-sm tabular-nums hover:bg-surface ${hasScore && !unpublished ? 'font-semibold text-ink' : 'text-ink-faint'}`}
    >
      {hasScore ? formatScore(cell.score) : '—'}
      {cell?.corrected && <span aria-hidden="true" className="size-[7px] rounded-full bg-primary" />}
      {unpublished && <span aria-hidden="true" className="size-[7px] rounded-full border-[1.5px] border-warning" />}
    </button>
  );
}
