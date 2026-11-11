import { StatusPill } from '@/components/StatusPill';
import { multiSelectFilter } from '@/components/table/multiSelectFilter';
import { BOARD_RESULT, boardRowResult } from '../lib/examBoardGrades';
import { ExamBoardCorrectButton, ExamBoardGradeCell, ExamBoardStudentCell, ExamBoardWithdrawButton } from './ExamBoardGradeCell';

export function buildBoardColumns() {
  return [
    {
      id: 'student',
      header: 'Alumno',
      accessorFn: (row) => row.studentName,
      sortingFn: (a, b) => a.original.studentName.localeCompare(b.original.studentName, 'es'),
      enableSorting: true,
      meta: { sticky: 'left' },
      cell: ({ row }) => <ExamBoardStudentCell row={row.original} />,
    },
    {
      id: 'grade',
      header: 'Nota del final / Ausente',
      enableSorting: false,
      meta: { align: 'left' },
      cell: ({ row, table }) => {
        const { drafts, rowErrors, mode, lockedReason, onChange } = table.options.meta.board;
        return (
          <ExamBoardGradeCell
            row={row.original}
            draft={drafts[row.original.id]}
            mode={mode}
            lockedReason={lockedReason}
            error={rowErrors[row.original.id]}
            onChange={onChange}
          />
        );
      },
    },
    {
      id: 'result',
      header: 'Resultado',
      accessorFn: (row) => boardRowResult(row),
      filterFn: multiSelectFilter,
      enableColumnFilter: true,
      enableSorting: false,
      meta: { align: 'left', filterLabel: (value) => BOARD_RESULT[value]?.label ?? value },
      cell: ({ row, table }) => {
        const result = BOARD_RESULT[boardRowResult(row.original, table.options.meta.board.drafts[row.original.id])];
        return <StatusPill tone={result.tone}>{result.label}</StatusPill>;
      },
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">Acciones</span>,
      enableSorting: false,
      meta: { sticky: 'right' },
      cell: ({ row, table }) => {
        const { canWithdraw, onWithdraw, withdrawing, correctionAccess, onCorrect } = table.options.meta.board;
        const access = correctionAccess(row.original);
        if (access !== 'none') return <ExamBoardCorrectButton row={row.original} access={access} onCorrect={onCorrect} />;
        return canWithdraw && row.original.status === 'REGISTERED' ? (
          <ExamBoardWithdrawButton row={row.original} onWithdraw={onWithdraw} pending={withdrawing} />
        ) : null;
      },
    },
  ];
}
