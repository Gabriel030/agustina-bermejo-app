import { useState } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { useGradeItemAudit } from '../hooks/useGradeCorrection';
import { formatAuditDate, sortAuditNewestFirst } from '../lib/gradeCorrection';
import { formatScore, gradeColumnLabel } from '../lib/gradeGrid';

export function GradeAuditSheetView({ student, column, cell, onBack }) {
  const { data, isLoading, isError, refetch } = useGradeItemAudit(cell.gradeItemId);
  const [ascending, setAscending] = useState(false);
  const label = gradeColumnLabel(column);
  const corrections = sortAuditNewestFirst((data ?? []).filter((entry) => entry.kind === 'CORRECTION'));
  const entries = ascending ? [...corrections].reverse() : corrections;
  const SortIcon = ascending ? ArrowUp : ArrowDown;

  return (
    <div className="flex flex-col gap-4">
      <button type="button" onClick={onBack} className="w-fit text-left text-[13px] font-semibold text-primary hover:underline">
        {`← ${student.lastname}, ${student.name}`}
      </button>
      <div>
        <h3 className="font-heading text-lg font-semibold text-ink">{`Historial · ${label}`}</h3>
        <p className="text-[12.5px] text-ink-faint">Correcciones de esta nota</p>
      </div>
      {isLoading ? (
        <div aria-busy="true" aria-label="Cargando historial" className="flex flex-col gap-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : isError ? (
        <LoadErrorState resource="el historial" onRetry={() => refetch()} bare />
      ) : entries.length === 0 ? (
        <EmptyState title="Esta nota no tiene correcciones" />
      ) : (
        <>
          <Button type="button" variant="ghost" size="sm" className="w-fit" aria-label="Ordenar por fecha" onClick={() => setAscending((current) => !current)}>
            Fecha
            <SortIcon aria-hidden="true" className="size-3.5" />
          </Button>
          <ul className="flex flex-col gap-2">
            {entries.map((entry) => (
              <li key={entry.id} className="flex flex-col gap-1 rounded-2xl border border-border p-3">
                <div className="flex items-center justify-between gap-2 text-[12.5px] text-ink-faint">
                  <span>{formatAuditDate(entry.changedAt)}</span>
                  <span>Dirección</span>
                </div>
                <div className="text-sm font-semibold text-ink">{`${formatScore(entry.previousScore)} → ${formatScore(entry.newScore)}`}</div>
                <p className="whitespace-pre-wrap text-[13px] text-ink-soft">{entry.reason}</p>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
