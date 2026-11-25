import { memo } from 'react';
import { attendancePercentToPoints } from '@/lib/attendancePercent';
import { STATUS_BY_VALUE } from '@/features/attendance/lib/attendanceStatuses';
import { historyDateDivider, rejectionText } from '@/features/attendance/lib/attendanceHistory';

const STICKY_CELL = 'sticky left-0 z-10';

function AttendanceHistoryRowComponent({
  row,
  classDates,
  highlighted,
  monthEnds,
  yearEnds,
  returnRequest,
  onRowEnter,
  onRowLeave,
  onRowToggle,
  onCellSelect,
}) {
  const returnTarget = returnRequest && row.studentName === returnRequest.studentName ? returnRequest.classDate : null;
  return (
    <tr
      onMouseEnter={() => onRowEnter(row.studentId)}
      onMouseLeave={onRowLeave}
      onClick={() => onRowToggle(row.studentId)}
      className="cursor-pointer"
    >
      <td className={`${STICKY_CELL} whitespace-nowrap border-y-2 px-3 py-1 font-medium ${highlighted ? 'border-brand bg-brand text-on-accent' : 'border-transparent bg-surface text-ink'}`}>
        {row.studentName}
      </td>
      {classDates.map((date) => {
        const cell = row.cellsByDate[date];
        const config = cell ? STATUS_BY_VALUE[cell.status] : null;
        return (
          <td
            key={date}
            data-date={date}
            onClick={(e) => {
              if (!cell) return;
              e.stopPropagation();
              onCellSelect({ studentName: row.studentName, fecha: date, ...cell });
            }}
            className={`w-7 border-y-[3px] px-0.5 py-1 text-center data-hl:shadow-(--sh-highlight-row) ${cell ? 'cursor-pointer' : ''} ${config ? config.rowClass : ''} ${historyDateDivider(date, monthEnds, yearEnds)} ${highlighted ? 'border-brand' : 'border-transparent'} ${returnTarget === date ? 'ring-2 ring-inset ring-brand' : ''}`}
            title={cell?.rejection ? rejectionText(cell.rejection) : (config?.label ?? 'Sin cargar')}
            tabIndex={cell?.rejection ? 0 : undefined}
            aria-label={cell?.rejection ? `${config.label}. ${rejectionText(cell.rejection)}` : undefined}
          >
            {config ? config.label[0] : '·'}
            {cell?.rejection && <span aria-hidden="true" className="ml-0.5 text-danger">!</span>}
          </td>
        );
      })}
      <td className={`border-y-2 px-2 py-1 text-center font-semibold ${highlighted ? 'border-brand bg-brand text-on-accent' : 'border-transparent text-ink'}`}>
        {row.attendancePercent == null ? '-' : `${Math.round(attendancePercentToPoints(row.attendancePercent))}%`}
      </td>
    </tr>
  );
}

export const AttendanceHistoryRow = memo(AttendanceHistoryRowComponent);
