import { useEffect, useRef, useState } from 'react';
import { Plus } from 'lucide-react';
import { NOMBRES_DIA_SEMANA, construirGrillaDeMes } from '@/lib/calendario';
import { cn } from '@/lib/utils';

function toIso(year, month, day) {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function chunkWeeks(cells) {
  const weeks = [];
  for (let i = 0; i < cells.length; i += 7) {
    const week = cells.slice(i, i + 7);
    while (week.length < 7) week.push(null);
    weeks.push(week);
  }
  return weeks;
}

const KEY_STEPS = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };

export function MonthGrid({ year, month, entries, todayIso, selectedIso, onSelect, onNavigate, label }) {
  const { nombre, celdas } = construirGrillaDeMes(year, month);
  const weeks = chunkWeeks(celdas);
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const [focusedDay, setFocusedDay] = useState(1);
  const focusRequested = useRef(false);
  const gridRef = useRef(null);

  const selectedInMonth = selectedIso?.startsWith(`${year}-${String(month + 1).padStart(2, '0')}-`)
    ? Number(selectedIso.slice(8, 10))
    : null;
  const tabDay = Math.min(focusedDay, daysInMonth);

  useEffect(() => {
    if (!focusRequested.current) return;
    focusRequested.current = false;
    gridRef.current?.querySelector(`[data-day="${tabDay}"]`)?.focus();
  }, [year, month, tabDay]);

  function moveTo(date) {
    focusRequested.current = true;
    setFocusedDay(date.getDate());
    if (date.getFullYear() !== year || date.getMonth() !== month) {
      onNavigate(date.getFullYear(), date.getMonth());
    }
  }

  function handleKeyDown(event, day) {
    const weekdayIndex = (new Date(year, month, day).getDay() + 6) % 7;
    let target = null;
    if (KEY_STEPS[event.key]) target = new Date(year, month, day + KEY_STEPS[event.key]);
    else if (event.key === 'Home') target = new Date(year, month, day - weekdayIndex);
    else if (event.key === 'End') target = new Date(year, month, day + (6 - weekdayIndex));
    else if (event.key === 'PageUp') target = new Date(year, month - 1, Math.min(day, new Date(year, month, 0).getDate()));
    else if (event.key === 'PageDown') target = new Date(year, month + 1, Math.min(day, new Date(year, month + 2, 0).getDate()));
    if (!target) return;
    event.preventDefault();
    moveTo(target);
  }

  return (
    <div
      ref={gridRef}
      role="grid"
      aria-label={label ?? `${nombre} de ${year}`}
      className="flex flex-col gap-1.5"
    >
      <div role="row" className="grid grid-cols-7 gap-1.5">
        {NOMBRES_DIA_SEMANA.map((name, index) => (
          <span
            key={`${name}-${index}`}
            role="columnheader"
            className="py-1 text-center text-size-11 font-bold uppercase tracking-widest text-ink-faint"
          >
            {name}
          </span>
        ))}
      </div>
      {weeks.map((week, weekIndex) => (
        <div key={weekIndex} role="row" className="grid grid-cols-7 gap-1.5">
          {week.map((day, dayIndex) => {
            if (day === null) return <div key={dayIndex} role="gridcell" className="min-h-14 sm:min-h-20" />;
            const iso = toIso(year, month, day);
            const entry = entries[iso];
            const selected = selectedInMonth === day;
            const today = iso === todayIso;
            const weekend = dayIndex >= 5;
            return (
              <div key={dayIndex} role="gridcell" aria-selected={selected} className="min-w-0">
                <button
                  type="button"
                  data-day={day}
                  tabIndex={tabDay === day ? 0 : -1}
                  aria-label={`${day} de ${nombre.toLowerCase()}${entry ? `: ${entry.label}, ${entry.typeLabel}` : ', sin feriado'}`}
                  onClick={() => {
                    setFocusedDay(day);
                    onSelect(iso);
                  }}
                  onKeyDown={(event) => handleKeyDown(event, day)}
                  className={cn(
                    'group relative flex border-transparent min-h-14 w-full flex-col items-start gap-0.5 overflow-hidden rounded-xl border p-1.5 text-left transition-colors sm:min-h-20 sm:p-2',
                    'outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
                    entry ? entry.cellClass : weekend ? 'border-transparent bg-neutral-bg/70 hover:border-border hover:bg-surface' : 'border-transparent bg-neutral-bg/35 hover:border-border hover:bg-surface',
                    entry && 'hover:brightness-95',
                    selected && 'border-2 border-ink shadow-popover',
                  )}
                >
                  <span
                    className={cn(
                      'grid size-6 flex-none place-items-center rounded-full text-size-13 font-semibold tabular-nums text-ink',
                      today && 'bg-ink font-bold text-background',
                    )}
                  >
                    {day}
                  </span>
                  {entry && (
                    <>
                      <span className="line-clamp-2 hidden text-size-11-5 font-semibold leading-tight sm:block" title={entry.label}>
                        {entry.label}
                      </span>
                      <span
                        aria-hidden="true"
                        className={cn(
                          'grid h-5 sm:absolute sm:right-1 sm:top-1 min-w-5 place-items-center rounded-md border-[1.5px] px-0.5 text-size-10-5 font-extrabold leading-none',
                          'border-current',
                        )}
                      >
                        {entry.chip}
                      </span>
                    </>
                  )}
                  {!entry && (
                    <Plus
                      aria-hidden="true"
                      className="absolute bottom-1.5 right-1.5 size-4 text-ink-faint opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100"
                    />
                  )}
                </button>
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}
