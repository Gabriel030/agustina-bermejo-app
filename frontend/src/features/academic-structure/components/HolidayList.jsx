import { ChevronRight } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { HolidayTypePill } from '@/features/academic-structure/components/HolidayTypePill';
import { groupHolidaysByMonth, holidayCountLabel, isoDay, monthName, weekdayShort } from '@/features/academic-structure/lib/holidays';
import { cn } from '@/lib/utils';

function HolidayRow({ holiday, selected, onSelect }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={() => onSelect(holiday)}
      className={cn(
        'flex min-h-14 w-full items-center gap-3 rounded-2xl px-3 py-1.5 text-left transition-colors hover:bg-neutral-bg/60',
        selected && 'bg-accent-soft hover:bg-accent-soft',
      )}
    >
      <span className="flex w-10 flex-none flex-col tabular-nums">
        <span className="font-heading text-[17px] font-bold leading-tight text-ink">{isoDay(holiday.date)}</span>
        <span className="text-[11px] uppercase tracking-wider text-ink-faint">{weekdayShort(holiday.date)}</span>
      </span>
      <span className="flex min-w-0 flex-1 flex-col items-start gap-1">
        <span className="w-full truncate text-[13.5px] font-medium text-ink" title={holiday.name}>{holiday.name}</span>
        <HolidayTypePill type={holiday.type} />
      </span>
      <ChevronRight aria-hidden="true" className="size-4 flex-none text-ink-faint" />
    </button>
  );
}

export function HolidayList({ holidays, year, loading, scopeMonth, selectedId, onSelect, emptyAction }) {
  const visible = scopeMonth === null ? holidays : holidays.filter((holiday) => Number(holiday.date.slice(5, 7)) - 1 === scopeMonth);
  const groups = groupHolidaysByMonth(visible);

  return (
    <GlassCard className="flex max-h-[640px] flex-col gap-1 overflow-auto p-2">
      <h2 className="px-3 pb-1 pt-2 font-heading text-[15px] font-semibold text-ink">
        {scopeMonth === null ? `Feriados de ${year}` : `Feriados de ${monthName(scopeMonth).toLowerCase()}`}
      </h2>
      {loading && (
        <div className="flex flex-col gap-2 p-2" aria-busy="true">
          {Array.from({ length: 5 }, (_, index) => <Skeleton key={index} className="h-12" />)}
        </div>
      )}
      {!loading && holidays.length === 0 && (
        <EmptyState
          icon="📅"
          title={holidayCountLabel(0, year)}
          description="Cargá los feriados para poder crear períodos y cohortes de ese año."
          action={emptyAction}
        />
      )}
      {!loading && holidays.length > 0 && visible.length === 0 && (
        <EmptyState title="Este mes no tiene feriados" description="Tocá un día del calendario para cargar uno." />
      )}
      {!loading && groups.map((group) => (
        <section key={group.month} aria-label={monthName(group.month)} className="flex flex-col gap-1">
          <h3 className="sticky top-0 z-[1] bg-popover px-3 pb-1 pt-3 text-[11px] font-bold uppercase tracking-widest text-ink-faint">
            {monthName(group.month)}
          </h3>
          {group.holidays.map((holiday) => (
            <HolidayRow key={holiday.id} holiday={holiday} selected={holiday.id === selectedId} onSelect={onSelect} />
          ))}
        </section>
      ))}
    </GlassCard>
  );
}
