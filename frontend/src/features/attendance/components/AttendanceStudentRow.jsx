import { Paperclip } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { attendanceRisk } from '@/features/attendance/lib/attendanceRisk';
import { MARKABLE_STATUSES, STATUS_BY_VALUE, UNMARKED_STATUS } from '@/features/attendance/lib/attendanceStatuses';
import { cn } from '@/lib/utils';

const RISK_TEXT_CLASS = {
  danger: 'text-danger-ink',
  warning: 'text-warning-ink',
};

const STATUS_BUTTON_BASE =
  'h-auto min-h-13 gap-1.5 px-1.5 text-size-13 font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-(--focus-ring) disabled:cursor-not-allowed sm:min-h-11';

export function AttendanceStudentRow({ student, status, onChangeStatus, locked, onRequestChange, justification, onAttachProof }) {
  const config = status ? STATUS_BY_VALUE[status] : null;
  const risk = attendanceRisk(student);
  const showStatusPill = !status || !MARKABLE_STATUSES.some((option) => option.value === status);
  const pill = config ?? UNMARKED_STATUS;
  const PillIcon = pill.icon;
  const hasExtras = justification === 'abierta' || justification === 'vencida' || onRequestChange;

  return (
    <li
      data-status={status ?? ''}
      className={cn(
        'grid gap-2.5 border-t border-border px-4 py-3 first:border-t-0 sm:grid-cols-[minmax(0,1fr)_14rem_20.5rem] sm:items-center sm:gap-4 sm:py-2',
        config?.rowClass ?? 'bg-surface',
      )}
    >
      <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 font-heading text-size-14-5 font-semibold text-ink">
        <span>{student.studentName}</span>
        {showStatusPill && (
          <StatusPill tone={pill.tone} icon={PillIcon}>
            {pill.label}
          </StatusPill>
        )}
      </div>
      <div className={cn('flex flex-wrap items-center gap-x-2 gap-y-1 text-size-12-5 font-bold tabular-nums text-ink-faint', RISK_TEXT_CLASS[risk.level])}>
        <span>{risk.text}</span>
        {risk.pill && (
          <span title={risk.hint}>
            <StatusPill tone={risk.level} className="px-2 py-0.5">
              {risk.pill}
            </StatusPill>
          </span>
        )}
      </div>
      <div className="flex flex-col gap-2">
        <div role="group" aria-label={`Asistencia de ${student.studentName}`} className="grid grid-cols-3 gap-2 sm:gap-1.5">
          {MARKABLE_STATUSES.map((option) => {
            const Icon = option.icon;
            const pressed = status === option.value;
            return (
              <Button
                key={option.value}
                type="button"
                variant="subtle"
                disabled={locked}
                aria-pressed={pressed}
                onClick={() => onChangeStatus(student.enrollmentId, option.value)}
                className={cn(
                  STATUS_BUTTON_BASE,
                  pressed ? option.activeClass : 'border-border bg-surface text-ink-soft hover:bg-row-hover hover:text-ink-soft',
                )}
              >
                <Icon className="size-4 shrink-0" aria-hidden="true" />
                {option.label}
              </Button>
            );
          })}
        </div>
        {hasExtras && (
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-size-12-5 font-semibold text-ink-faint">
            {justification === 'abierta' && (
              <Button type="button" variant="outline" size="sm" className="min-h-11" onClick={onAttachProof}>
                <Paperclip aria-hidden="true" />
                Adjuntar comprobante
              </Button>
            )}
            {justification === 'vencida' && <span>Venció el plazo para justificar: lo resuelve el preceptor.</span>}
            {onRequestChange && (
              <Button type="button" variant="outline" size="sm" className="min-h-11" onClick={onRequestChange}>
                Pedir modificación
              </Button>
            )}
          </div>
        )}
      </div>
    </li>
  );
}
