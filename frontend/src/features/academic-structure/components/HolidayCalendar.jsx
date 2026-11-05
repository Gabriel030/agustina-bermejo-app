import { ChevronLeft, ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { MonthGrid } from '@/components/MonthGrid';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { holidayTypeInfo, monthName } from '@/features/academic-structure/lib/holidays';

function toEntries(holidays) {
  return Object.fromEntries(holidays.map((holiday) => {
    const info = holidayTypeInfo(holiday.type);
    return [holiday.date, { label: holiday.name, typeLabel: info.label, chip: info.chip, cellClass: info.cellClass }];
  }));
}

export function HolidayCalendar({ year, month, holidays, loading, todayIso, selectedIso, onSelect, onNavigate }) {
  function shiftMonth(delta) {
    const target = new Date(year, month + delta, 1);
    onNavigate(target.getFullYear(), target.getMonth());
  }

  return (
    <GlassCard className="flex flex-col gap-2 p-4">
      <div className="flex items-center justify-center gap-2">
        <Button type="button" variant="ghost" size="icon-lg" aria-label="Mes anterior" onClick={() => shiftMonth(-1)}>
          <ChevronLeft />
        </Button>
        <h2 aria-live="polite" className="min-w-40 text-center font-heading text-lg font-bold text-ink">
          {monthName(month)} {year}
        </h2>
        <Button type="button" variant="ghost" size="icon-lg" aria-label="Mes siguiente" onClick={() => shiftMonth(1)}>
          <ChevronRight />
        </Button>
      </div>
      <p className="text-center text-xs text-ink-faint">Tocá un día para cargar un feriado o editar el que ya está.</p>
      {loading ? (
        <div aria-busy="true" className="grid grid-cols-7 gap-1.5">
          {Array.from({ length: 35 }, (_, index) => <Skeleton key={index} className="h-14 sm:h-20" />)}
        </div>
      ) : (
        <MonthGrid
          year={year}
          month={month}
          entries={toEntries(holidays)}
          todayIso={todayIso}
          selectedIso={selectedIso}
          onSelect={onSelect}
          onNavigate={onNavigate}
        />
      )}
    </GlassCard>
  );
}
