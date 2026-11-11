import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { multiSelectFilter } from '@/components/table/multiSelectFilter';
import { cn } from '@/lib/utils';
import {
  CLOSURE_RESULT,
  RESULT_FILTER_ID,
  columnLabel,
  documentsFilterValue,
  effectiveScore,
  examColumns,
  findGrade,
  formatAverage,
  formatScore,
  isWithdrawn,
  paymentsFilterValue,
  resultFilterLabel,
  resultFilterValue,
  studentFullName,
  yesNoFilterLabel,
} from '../lib/closureSheet';
import { ClosureAlertsCell } from './ClosureAlertsCell';
import { ClosureHeaderAbbreviation } from './ClosureHeaderAbbreviation';
import { ClosureRequirementMark } from './ClosureRequirementMark';

function requirementColumn(id, short, full, read) {
  return {
    id,
    header: () => <ClosureHeaderAbbreviation short={short} full={full} />,
    enableSorting: false,
    accessorFn: (row) => (isWithdrawn(row) ? undefined : read(row)),
    cell: ({ row }) => (isWithdrawn(row.original) ? null : <ClosureRequirementMark label={full} value={read(row.original)} />),
  };
}

function gradeCell(row, column) {
  if (isWithdrawn(row)) return null;
  const grade = findGrade(row, column);
  const effective = effectiveScore(grade);
  if (effective == null) return <span className="text-ink-faint">—</span>;
  if (grade.retakeScore == null) return formatScore(effective);
  return (
    <span aria-label={`${formatScore(grade.retakeScore)}, recuperatorio, reemplaza al ${formatScore(grade.score)}`}>
      <b>{formatScore(grade.retakeScore)}</b> <s className="text-xs text-ink-faint">{formatScore(grade.score)}</s>
    </span>
  );
}

export function buildClosureColumns(sheet, { onOpenPrerequisites }) {
  const exams = examColumns(sheet);
  return [
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: studentFullName,
      sortingFn: (a, b) => studentFullName(a.original).localeCompare(studentFullName(b.original), 'es'),
      meta: { stickyStart: true, rowHeader: true },
      cell: ({ row }) => (
        <span className={cn('block min-w-[9rem] max-w-[13rem] truncate font-semibold text-ink', isWithdrawn(row.original) && 'font-normal text-ink-faint line-through decoration-danger')}>
          {studentFullName(row.original)}
        </span>
      ),
    },
    {
      id: RESULT_FILTER_ID,
      header: 'Condición final',
      accessorFn: resultFilterValue,
      filterFn: multiSelectFilter,
      enableColumnFilter: true,
      enableSorting: false,
      meta: { filterable: true, filterTitle: 'Condición final', filterLabel: resultFilterLabel },
    },
    {
      id: 'result',
      header: 'Condición final',
      accessorFn: (row) => (isWithdrawn(row) ? undefined : CLOSURE_RESULT[row.suggestedResult]?.order),
      sortUndefined: 'last',
      meta: { filterColumnId: RESULT_FILTER_ID },
      cell: ({ row }) => {
        if (isWithdrawn(row.original)) return <StatusPill tone="neutral">Baja</StatusPill>;
        const config = CLOSURE_RESULT[row.original.suggestedResult];
        return <StatusPill tone={config.tone}>{config.label}</StatusPill>;
      },
    },
    ...exams.map((column) => ({
      id: `exam-${column.sequenceNumber}`,
      header: columnLabel(column),
      accessorFn: (row) => (isWithdrawn(row) ? undefined : (effectiveScore(findGrade(row, column)) ?? undefined)),
      sortUndefined: 'last',
      sortDescFirst: true,
      meta: { align: 'right' },
      cell: ({ row }) => gradeCell(row.original, column),
    })),
    {
      id: 'retakes',
      header: 'Recup. usados',
      enableSorting: false,
      cell: ({ row }) => (isWithdrawn(row.original) ? null : `${row.original.retakesUsed}/${sheet.scheme.retakeQuota}`),
    },
    {
      id: 'average',
      header: 'Promedio',
      accessorFn: (row) => (isWithdrawn(row) ? undefined : (row.average ?? undefined)),
      sortUndefined: 'last',
      sortDescFirst: true,
      meta: { align: 'right' },
      cell: ({ row }) => (isWithdrawn(row.original) ? null : formatAverage(row.original.average)),
    },
    {
      id: 'attendance',
      header: `Asist. (mín. ${100 - sheet.scheme.maxAbsenceRate}%)`,
      accessorFn: (row) => (isWithdrawn(row) ? undefined : row.attendancePercent),
      sortUndefined: 'last',
      sortDescFirst: true,
      meta: { align: 'right' },
      cell: ({ row }) =>
        isWithdrawn(row.original) ? null : (
          <span className={cn(row.original.requirements.attendance === false && 'font-semibold text-danger')}>{formatScore(row.original.attendancePercent)}%</span>
        ),
    },
    requirementColumn('coursework', 'TP', 'TP aprobados', (row) => row.requirements.coursework),
    requirementColumn('correlatives', 'Co', 'Correlativas', (row) => row.requirements.correlatives),
    requirementColumn('attendanceOk', 'As', 'Asistencia', (row) => row.requirements.attendance),
    requirementColumn('gradeOk', 'No', 'Ambos parciales con nota de promoción', (row) => row.requirements.grade),
    {
      ...requirementColumn('documents', 'Le', 'Legajo completo', (row) => row.administrative.documentsComplete),
      accessorFn: (row) => (isWithdrawn(row) ? 'WITHDRAWN' : documentsFilterValue(row)),
      filterFn: multiSelectFilter,
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'Legajo completo', filterLabel: (value) => (value === 'WITHDRAWN' ? 'Baja' : yesNoFilterLabel(value)) },
    },
    {
      ...requirementColumn('payments', 'Pa', 'Pagos al día', (row) => ({ OK: true, NOT_OK: false }[row.administrative.payments] ?? null)),
      accessorFn: (row) => (isWithdrawn(row) ? 'WITHDRAWN' : paymentsFilterValue(row)),
      filterFn: multiSelectFilter,
      enableColumnFilter: true,
      meta: { filterable: true, filterTitle: 'Pagos al día', filterLabel: (value) => (value === 'WITHDRAWN' ? 'Baja' : yesNoFilterLabel(value)) },
    },
    {
      id: 'alerts',
      header: 'Alertas',
      enableSorting: false,
      cell: ({ row }) => <ClosureAlertsCell row={row.original} onOpenPrerequisites={onOpenPrerequisites} />,
    },
  ];
}
