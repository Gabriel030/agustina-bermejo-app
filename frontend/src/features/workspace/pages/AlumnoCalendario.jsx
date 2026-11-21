import { useState } from 'react';
import { CalendarPlus, SlidersHorizontal } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { CalendarDayAgenda } from '@/features/workspace/components/calendar/CalendarDayAgenda';
import { CalendarFilters } from '@/features/workspace/components/calendar/CalendarFilters';
import { CalendarMonthGrid, CalendarWeekColumns, CalendarWeekStrip } from '@/features/workspace/components/calendar/CalendarGrid';
import { CalendarLegend } from '@/features/workspace/components/calendar/CalendarLegend';
import { CalendarMonthAgenda } from '@/features/workspace/components/calendar/CalendarMonthAgenda';
import { CalendarLoadError, CalendarNoEnrollmentsBanner, CalendarSkeleton } from '@/features/workspace/components/calendar/CalendarStates';
import { CalendarToolbar } from '@/features/workspace/components/calendar/CalendarToolbar';
import { useStudentCalendarState } from '@/features/workspace/hooks/useStudentCalendarState';
import { formatDayLong, formatMonthTitle, formatUpdatedAt, formatWeekTitle } from '@/features/workspace/lib/studentCalendar';

function CalendarHeaderAction({ updatedLabel }) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      {updatedLabel && <span className="text-size-12 text-ink-soft tabular-nums">{updatedLabel}</span>}
      <Button variant="primary" disabled title="Próximamente">
        <CalendarPlus aria-hidden="true" />
        Agregar a mi calendario
      </Button>
    </div>
  );
}

function CalendarBody({ state, onSelectEvent }) {
  const { view, isDesktop, eventsByDay, todayKey, selectedKey, monthKey } = state;
  const common = { eventsByDay, todayKey, selectedKey, onSelectDay: state.selectDay, onSelectEvent };
  if (view === 'agenda') {
    return <CalendarMonthAgenda monthDays={state.monthDays} eventsByDay={eventsByDay} todayKey={todayKey} onSelectEvent={onSelectEvent} />;
  }
  if (view === 'week') {
    return isDesktop ? (
      <CalendarWeekColumns days={state.weekDays} {...common} />
    ) : (
      <CalendarWeekStrip days={state.weekDays} {...common} />
    );
  }
  return <CalendarMonthGrid days={state.gridDays} monthKey={monthKey} compact={!isDesktop} {...common} />;
}

function SelectedDayBlock({ state, onSelectEvent }) {
  return (
    <section aria-labelledby="calendar-selected-day" className="flex flex-col gap-3">
      <h2 id="calendar-selected-day" className="font-heading text-size-14 font-semibold text-ink first-letter:uppercase">
        {formatDayLong(state.selectedKey, state.todayKey)}
      </h2>
      <CalendarDayAgenda events={state.selectedDayEvents} onSelectEvent={onSelectEvent} />
    </section>
  );
}

function FiltersBlock({ state }) {
  return (
    <CalendarFilters
      activeTypes={state.activeTypes}
      counts={state.counts}
      subject={state.subject}
      subjects={state.subjects}
      onToggleType={state.toggleType}
      onChangeSubject={state.setSubject}
    />
  );
}

export function AlumnoCalendario() {
  const state = useStudentCalendarState();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const { query, view, isDesktop } = state;
  const onSelectEvent = (event) => state.selectDay(event.date);
  const showDayBlock = view !== 'agenda';
  const title = view === 'week' ? formatWeekTitle(state.selectedDate) : formatMonthTitle(state.selectedDate);

  let content;
  if (query.isPending) content = <CalendarSkeleton />;
  else if (query.isError) content = <CalendarLoadError onRetry={() => query.refetch()} />;
  else {
    content = (
      <div className="flex flex-col gap-4">
        {state.hasNoEnrollments && <CalendarNoEnrollmentsBanner />}
        <div className="grid items-start gap-4 md:grid-cols-[minmax(0,1fr)_300px]">
          <div className="flex min-w-0 flex-col gap-3">
            <GlassCard className="flex flex-col gap-3 p-3 sm:p-4">
              <CalendarBody state={state} onSelectEvent={onSelectEvent} />
            </GlassCard>
            <CalendarLegend />
            {!isDesktop && showDayBlock && (
              <GlassCard className="p-4">
                <SelectedDayBlock state={state} onSelectEvent={onSelectEvent} />
              </GlassCard>
            )}
          </div>
          {isDesktop && (
            <aside className="flex flex-col gap-4">
              <GlassCard className="p-4">
                <FiltersBlock state={state} />
              </GlassCard>
              {showDayBlock && (
                <GlassCard className="p-4">
                  <SelectedDayBlock state={state} onSelectEvent={onSelectEvent} />
                </GlassCard>
              )}
            </aside>
          )}
        </div>
      </div>
    );
  }

  return (
    <PageContainer title="Calendario" action={<CalendarHeaderAction updatedLabel={formatUpdatedAt(state.generatedAt, state.todayKey)} />}>
      <CalendarToolbar view={view} title={title} onChangeView={state.setView} onToday={state.goToToday} onShift={state.shift} />
      {!isDesktop && (
        <div className="flex flex-col gap-3">
          <Button
            variant="outline"
            aria-expanded={filtersOpen}
            aria-controls="calendar-mobile-filters"
            onClick={() => setFiltersOpen((open) => !open)}
            className="min-h-11 self-start"
          >
            <SlidersHorizontal aria-hidden="true" />
            Filtros
            {state.hasActiveFilter && <span role="img" aria-label="con filtros activos" className="size-2 rounded-full bg-brand" />}
          </Button>
          {filtersOpen && (
            <GlassCard id="calendar-mobile-filters" className="p-4">
              <FiltersBlock state={state} />
            </GlassCard>
          )}
        </div>
      )}
      {content}
    </PageContainer>
  );
}
