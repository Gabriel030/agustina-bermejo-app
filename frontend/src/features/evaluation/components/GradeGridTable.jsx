import { useEffect, useMemo, useRef, useState } from 'react';
import { flexRender, getCoreRowModel, getFacetedRowModel, getFacetedUniqueValues, getFilteredRowModel, getSortedRowModel, useReactTable } from '@tanstack/react-table';
import { ChevronDown, ChevronRight, ChevronUp, ChevronsUpDown, Lock } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { ColumnFilter } from '@/components/table/ColumnFilter';
import { multiSelectFilter } from '@/components/table/multiSelectFilter';
import { Button } from '@/components/ui/button';
import { TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';
import {
  GRID_CONDITION,
  draftKey,
  findSavedCell,
  formatScore,
  gradeColumnKey,
  gradeColumnLabel,
  columnLoadSummary,
  hasColumnDrafts,
  isColumnLocked,
  isColumnPublishable,
} from '../lib/gradeGrid';
import { GradeEntryCell } from './GradeEntryCell';
import { GradeReadCell } from './GradeReadCell';

const STICKY_LEFT = 'sticky left-0 z-20 w-[112px] min-w-[112px] max-w-[112px] bg-dialog-bg whitespace-normal shadow-(--sh-sticky-left) md:w-[170px] md:min-w-[170px] md:max-w-[170px]';
const STICKY_RIGHT = 'sticky right-0 z-20 min-w-[56px] bg-dialog-bg text-right shadow-(--sh-sticky-right) md:min-w-[72px]';
const FINAL_DISABLED_REASON = 'Se carga desde la mesa de examen';

function PublishHeader({ column, students, hasDrafts, onPublish }) {
  if (column.type === 'FINAL') return null;
  const label = gradeColumnLabel(column);
  if (isColumnLocked(column)) {
    return (
      <StatusPill tone="success" className="px-2 py-0.5 normal-case tracking-normal">
        <Lock aria-hidden="true" className="size-3" />
        Publicada
      </StatusPill>
    );
  }
  const { loaded, total } = columnLoadSummary(students, column);
  const reason = hasDrafts ? 'Guardá las notas antes de publicar' : loaded === 0 ? 'Cargá al menos una nota para publicar' : undefined;
  return (
    <div className="flex flex-col items-end gap-1">
      <StatusPill tone={loaded ? 'warning' : 'neutral'} className="px-2 py-0.5 normal-case tracking-normal">
        {loaded ? `Cargada ${loaded}/${total}` : 'Sin notas'}
      </StatusPill>
      {isColumnPublishable(column) && (
        <span title={reason}>
          <Button type="button" variant="outline" size="xs" aria-label={`Publicar ${label}`} disabled={Boolean(reason)} onClick={() => onPublish(column)}>
            Publicar
          </Button>
        </span>
      )}
    </div>
  );
}

export function GradeGridTable({ students, columns, drafts = {}, statuses = {}, readOnly = false, onChange, onSettle, onRetry, onOpenStudent, onOpenCell, onPublish }) {
  'use no memo';
  const scrollRef = useRef(null);
  const [moreToRight, setMoreToRight] = useState(false);

  const tableColumns = useMemo(
    () => [
      {
        id: 'student',
        header: 'Alumno',
        accessorFn: (row) => `${row.lastname}, ${row.name}`,
        sortingFn: (a, b) => a.original.lastname.localeCompare(b.original.lastname, 'es') || a.original.name.localeCompare(b.original.name, 'es'),
        enableSorting: true,
        meta: { sticky: 'left' },
        cell: ({ row, table: instance }) => {
          const { onOpenStudent } = instance.options.meta;
          const condition = GRID_CONDITION[row.original.condition];
          return (
            <div className="flex flex-col items-start gap-1">
              <Button
                variant="link"
                size="inline"
                type="button"
                onClick={() => onOpenStudent(row.original.enrollmentId)}
                className="text-left text-[13px] font-semibold leading-tight text-ink"
                >
                {`${row.original.lastname}, ${row.original.name}`}
              </Button>
              {condition && <StatusPill tone={condition.tone} className="px-2 py-0.5">{condition.label}</StatusPill>}
            </div>
          );
        },
      },
      {
        id: 'condition',
        accessorFn: (row) => row.condition,
        filterFn: multiSelectFilter,
        enableColumnFilter: true,
        meta: { filterLabel: (value) => GRID_CONDITION[value]?.label ?? value },
      },
      ...columns.map((column) => ({
        id: gradeColumnKey(column.type, column.sequenceNumber),
        header: gradeColumnLabel(column),
        enableSorting: false,
        meta: { column },
        cell: ({ row, table: instance }) => {
          const { drafts, statuses, onChange, onSettle, onRetry } = instance.options.meta;
          const student = row.original;
          const key = draftKey(student.enrollmentId, column.type, column.sequenceNumber);
          const saved = findSavedCell(student, column);
          if (instance.options.meta.readOnly) {
            return <GradeReadCell label={gradeColumnLabel(column)} studentName={`${student.lastname}, ${student.name}`} cell={saved} onOpen={() => instance.options.meta.onOpenCell(student.enrollmentId, column)} />;
          }
          return (
            <GradeEntryCell
              label={gradeColumnLabel(column)}
              studentName={`${student.lastname}, ${student.name}`}
              saved={saved}
              draft={drafts[key]}
              status={statuses[key]}
              disabledReason={column.type === 'FINAL' ? FINAL_DISABLED_REASON : null}
              locked={isColumnLocked(column)}
              onChange={(text) => onChange(key, saved, { score: text })}
              onBlur={() => onSettle(key)}
              onRetry={() => onRetry(key)}
            />
          );
        },
      })),
      {
        id: 'finalScore',
        header: 'Nota final',
        enableSorting: false,
        meta: { sticky: 'right' },
        cell: ({ row }) => <b className="text-ink">{row.original.finalScore == null ? '—' : formatScore(row.original.finalScore)}</b>,
      },
    ],
    [columns],
  );

  const table = useReactTable({
    data: students,
    columns: tableColumns,
    getRowId: (row) => String(row.enrollmentId),
    meta: { drafts, statuses, readOnly, onChange, onSettle, onRetry, onOpenStudent, onOpenCell },
    state: { columnVisibility: { condition: false } },
    initialState: { sorting: [{ id: 'student', desc: false }] },
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFacetedRowModel: getFacetedRowModel(),
    getFacetedUniqueValues: getFacetedUniqueValues(),
  });

  useEffect(() => {
    const node = scrollRef.current;
    if (!node) return undefined;
    const update = () => setMoreToRight(node.scrollWidth - node.clientWidth - node.scrollLeft > 6);
    update();
    node.addEventListener('scroll', update);
    window.addEventListener('resize', update);
    return () => {
      node.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, [columns.length, students.length]);

  const conditionColumn = table.getColumn('condition');
  const conditionOptions = Array.from(conditionColumn.getFacetedUniqueValues().entries())
    .map(([value, count]) => ({ value, count, label: GRID_CONDITION[value]?.label ?? value }))
    .sort((a, b) => a.label.localeCompare(b.label, 'es'));
  const sortDirection = table.getColumn('student').getIsSorted();
  const SortIcon = sortDirection === 'asc' ? ChevronUp : sortDirection === 'desc' ? ChevronDown : ChevronsUpDown;
  const rows = table.getRowModel().rows;

  return (
    <div className="relative min-w-0 max-w-full">
      <div ref={scrollRef} className="max-h-[62vh] max-w-full overflow-auto rounded-xl">
        <table className="w-max min-w-full border-separate border-spacing-0 text-sm">
          <TableHeader className="bg-dialog-bg">
            <TableRow className="hover:bg-transparent">
              {table.getFlatHeaders().map((header) => {
                const sticky = header.column.columnDef.meta?.sticky;
                const isStudent = header.column.id === 'student';
                return (
                  <TableHead
                    key={header.id}
                    className={cn(
                      'sticky top-0 z-10 bg-dialog-bg px-1.5',
                      header.column.id !== 'student' && header.column.id !== 'finalScore' && 'text-right',
                      sticky === 'left' && `${STICKY_LEFT} z-30`,
                      sticky === 'right' && `${STICKY_RIGHT} z-30 whitespace-normal`,
                    )}
                  >
                    {isStudent ? (
                      <div className="flex items-center gap-1">
                        <button
                          type="button"
                          onClick={header.column.getToggleSortingHandler()}
                          className="flex items-center gap-1 uppercase hover:text-foreground"
                        >
                          Alumno
                          <SortIcon className="size-3.5" />
                        </button>
                        {conditionOptions.length > 1 && (
                          <ColumnFilter
                            label="Condición"
                            options={conditionOptions}
                            selected={conditionColumn.getFilterValue()}
                            onChange={(value) => conditionColumn.setFilterValue(value)}
                          />
                        )}
                      </div>
                    ) : (
                      <div className="flex flex-col items-end gap-1">
                        <span>{flexRender(header.column.columnDef.header, header.getContext())}</span>
                        {header.column.columnDef.meta?.column?.date && (
                          <span className="text-[10.5px] font-medium normal-case tracking-normal text-ink-faint">
                            {header.column.columnDef.meta.column.date.slice(8, 10)}/{header.column.columnDef.meta.column.date.slice(5, 7)}
                          </span>
                        )}
                        {!readOnly && header.column.columnDef.meta?.column && (
                          <PublishHeader
                            column={header.column.columnDef.meta.column}
                            students={students}
                            hasDrafts={hasColumnDrafts(drafts, header.column.columnDef.meta.column)}
                            onPublish={onPublish}
                          />
                        )}
                      </div>
                    )}
                  </TableHead>
                );
              })}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} className="group">
                {row.getVisibleCells().map((cell) => {
                  const sticky = cell.column.columnDef.meta?.sticky;
                  return (
                    <TableCell
                      key={cell.id}
                      className={cn(
                        'border-t border-border px-1.5 py-1.5',
                        cell.column.id !== 'student' && 'text-right',
                        sticky === 'left' && STICKY_LEFT,
                        sticky === 'right' && STICKY_RIGHT,
                      )}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))}
            {!rows.length && (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={columns.length + 2} className="py-6 text-center text-ink-faint">
                  Ningún alumno con estos filtros.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </table>
      </div>
      <div
        aria-hidden="true"
        className={cn(
          'pointer-events-none absolute inset-y-0 right-[56px] flex w-9 items-center justify-end rounded-r-xl bg-gradient-to-r from-transparent to-dialog-bg pr-1 text-ink-soft transition-opacity md:right-[72px]',
          moreToRight ? 'opacity-100' : 'opacity-0',
        )}
      >
        <ChevronRight className="size-5" />
      </div>
    </div>
  );
}
