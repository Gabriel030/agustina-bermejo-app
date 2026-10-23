import { useEffect, useRef, useState } from 'react';
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

const YEARS_PER_PAGE = 12;
const NO_DATA_REASON = 'Sin datos de este mes';
const FUTURE_REASON = 'Todavía no ocurrió';
const NO_DATA_YEAR_REASON = 'Sin datos de este año';
const FUTURE_YEAR_REASON = 'Todavía no llegó';

const longName = new Intl.DateTimeFormat('es-AR', { month: 'long', timeZone: 'UTC' });
const shortName = new Intl.DateTimeFormat('es-AR', { month: 'short', timeZone: 'UTC' });
const MONTH_LONG = Array.from({ length: 12 }, (_, index) => longName.format(new Date(Date.UTC(2000, index, 1))));
const MONTH_SHORT = Array.from({ length: 12 }, (_, index) => shortName.format(new Date(Date.UTC(2000, index, 1))).replace('.', ''));

function parse(month) {
  const [year, number] = month.split('-').map(Number);
  return { year, number };
}

function toMonth(year, number) {
  return `${year}-${String(number).padStart(2, '0')}`;
}

function shiftMonth(month, delta) {
  const { year, number } = parse(month);
  const index = year * 12 + number - 1 + delta;
  return toMonth(Math.floor(index / 12), (index % 12) + 1);
}

function todayMonth() {
  const now = new Date();
  return toMonth(now.getFullYear(), now.getMonth() + 1);
}

function describe(month) {
  const { year, number } = parse(month);
  return `${MONTH_LONG[number - 1]} ${year}`;
}

function clamp(month, minMonth, maxMonth) {
  if (minMonth && month < minMonth) return minMonth;
  if (maxMonth && month > maxMonth) return maxMonth;
  return month;
}

const ARROW_STEP = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -3, ArrowDown: 3 };

function Guarded({ reason, children }) {
  if (!reason) return children;
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent>{reason}</TooltipContent>
    </Tooltip>
  );
}

function StepButton({ label, reason, onClick, children }) {
  return (
    <Guarded reason={reason}>
      <button
        type="button"
        aria-label={reason ? `${label}, ${reason.toLowerCase()}` : label}
        aria-disabled={reason ? 'true' : undefined}
        onClick={reason ? undefined : onClick}
        className={cn(
          'flex h-10 w-11 items-center justify-center rounded-[10px] text-ink-soft transition-colors hover:bg-accent-soft hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          reason && 'cursor-not-allowed opacity-40 hover:bg-transparent hover:text-ink-soft',
        )}
      >
        {children}
      </button>
    </Guarded>
  );
}

function Cell({ label, name, reason, selected, current, focusable, cellRef, onSelect }) {
  return (
    <Guarded reason={reason}>
      <button
        ref={cellRef}
        type="button"
        aria-label={reason ? `${label}, ${reason.toLowerCase()}` : label}
        aria-pressed={selected}
        aria-current={current ? 'date' : undefined}
        aria-disabled={reason ? 'true' : undefined}
        tabIndex={focusable ? 0 : -1}
        onClick={reason ? undefined : onSelect}
        className={cn(
          'relative h-11 rounded-xl border-[1.5px] border-transparent text-sm font-bold lowercase tabular-nums transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          !selected && !reason && 'hover:bg-accent-soft',
          current && !selected && 'border-primary',
          selected && 'bg-gradient-to-br from-brand to-accent-2 text-white',
          reason && 'cursor-not-allowed text-ink-faint opacity-50',
        )}
      >
        {name}
        {current && (
          <span
            aria-hidden="true"
            className={cn('absolute bottom-[5px] left-1/2 size-[5px] -translate-x-1/2 rounded-full', selected ? 'bg-white' : 'bg-primary')}
          />
        )}
      </button>
    </Guarded>
  );
}

export function MonthPicker({ value, onChange, minMonth, maxMonth, currentMonth, disabled = false, disabledReason = 'Todavía no hay meses con datos', className }) {
  const current = currentMonth ?? todayMonth();
  const [open, setOpen] = useState(false);
  const [view, setView] = useState('months');
  const [year, setYear] = useState(() => parse(value).year);
  const [focusedMonth, setFocusedMonth] = useState(value);
  const [focusedYear, setFocusedYear] = useState(() => parse(value).year);
  const cellRefs = useRef({});
  const refocus = useRef(false);

  const minYear = minMonth ? parse(minMonth).year : null;
  const maxYear = maxMonth ? parse(maxMonth).year : null;
  const anchorYear = maxYear ?? parse(current).year;
  const pageIndex = Math.max(0, Math.ceil((anchorYear - year + 1) / YEARS_PER_PAGE) - 1);
  const pageStart = anchorYear - YEARS_PER_PAGE + 1 - pageIndex * YEARS_PER_PAGE;
  const pageYears = Array.from({ length: YEARS_PER_PAGE }, (_, index) => pageStart + index);

  useEffect(() => {
    if (!open || !refocus.current) return;
    refocus.current = false;
    cellRefs.current[view === 'months' ? focusedMonth : focusedYear]?.focus();
  });

  const monthReason = (month) => {
    if (minMonth && month < minMonth) return NO_DATA_REASON;
    if (maxMonth && month > maxMonth) return FUTURE_REASON;
    return null;
  };
  const yearReason = (candidate) => {
    if (minYear !== null && candidate < minYear) return NO_DATA_YEAR_REASON;
    if (maxYear !== null && candidate > maxYear) return FUTURE_YEAR_REASON;
    return null;
  };

  const handleOpenChange = (next) => {
    if (next) {
      setView('months');
      setYear(parse(value).year);
      setFocusedMonth(value);
      setFocusedYear(parse(value).year);
    }
    setOpen(next);
  };

  const pick = (month) => {
    onChange(month);
    setOpen(false);
  };

  const moveYear = (delta) => {
    const nextYear = year + delta;
    setYear(nextYear);
    setFocusedMonth(clamp(toMonth(nextYear, parse(focusedMonth).number), minMonth, maxMonth));
  };

  const pickYear = (picked) => {
    setYear(picked);
    setFocusedMonth(clamp(toMonth(picked, parse(focusedMonth).number), minMonth, maxMonth));
    setView('months');
    refocus.current = true;
  };

  const handleGridKey = (event) => {
    if (view === 'months') {
      const step = ARROW_STEP[event.key];
      const jump = event.key === 'PageUp' ? -12 : event.key === 'PageDown' ? 12 : null;
      if (step === undefined && jump === null) return;
      event.preventDefault();
      const next = clamp(shiftMonth(focusedMonth, step ?? jump), minMonth, maxMonth);
      setFocusedMonth(next);
      setYear(parse(next).year);
      refocus.current = true;
      return;
    }
    const step = ARROW_STEP[event.key];
    const jump = event.key === 'PageUp' ? -YEARS_PER_PAGE : event.key === 'PageDown' ? YEARS_PER_PAGE : null;
    if (step === undefined && jump === null) return;
    event.preventDefault();
    let next = focusedYear + (step ?? jump);
    if (minYear !== null) next = Math.max(next, minYear);
    if (maxYear !== null) next = Math.min(next, maxYear);
    setFocusedYear(next);
    setYear(next);
    refocus.current = true;
  };

  const closeWhenTabLeaves = (event) => {
    if (event.key !== 'Tab') return;
    const items = [...event.currentTarget.querySelectorAll('button')].filter((node) => node.tabIndex >= 0);
    const edge = event.shiftKey ? items[0] : items[items.length - 1];
    if (document.activeElement === edge) setOpen(false);
  };

  const previous = shiftMonth(current, -1);
  const label = describe(value);

  const trigger = (
    <Button
      type="button"
      variant="outline"
      aria-label={`Mes, ${label}`}
      aria-disabled={disabled ? 'true' : undefined}
      onClick={disabled ? (event) => event.preventDefault() : undefined}
      className={cn('h-10 min-w-0 justify-start gap-2 font-semibold', disabled && 'cursor-not-allowed opacity-55', className)}
    >
      <CalendarDays />
      <span className="truncate first-letter:uppercase">{label}</span>
      <ChevronDown className="ml-auto opacity-60" />
    </Button>
  );

  if (disabled) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>{trigger}</TooltipTrigger>
          <TooltipContent>{disabledReason}</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  const shortcuts = [
    { key: 'current', month: current, text: 'Este mes', aria: `Este mes, ${describe(current)}` },
    { key: 'previous', month: previous, text: `Mes anterior · ${MONTH_SHORT[parse(previous).number - 1]}`, aria: `Mes anterior, ${describe(previous)}` },
  ];

  return (
    <TooltipProvider>
      <Popover open={open} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>{trigger}</PopoverTrigger>
        <PopoverContent
          align="start"
          className="w-[min(18rem,calc(100vw-2rem))] gap-0 rounded-2xl bg-dialog-bg p-3"
          onKeyDown={closeWhenTabLeaves}
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            cellRefs.current[value]?.focus();
          }}
        >
          <div className="mb-2 flex items-center justify-between gap-1">
            {view === 'months' ? (
              <>
                <StepButton label="Año anterior" reason={yearReason(year - 1)} onClick={() => moveYear(-1)}>
                  <ChevronLeft className="size-4" />
                </StepButton>
                <button
                  type="button"
                  aria-label={`Elegir año, ${year}`}
                  onClick={() => {
                    setFocusedYear(year);
                    setView('years');
                    refocus.current = true;
                  }}
                  className="inline-flex h-10 items-center gap-1.5 rounded-[10px] border-[1.5px] border-border px-3 font-heading text-base font-bold tabular-nums hover:bg-accent-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {year}
                  <ChevronDown className="size-4 opacity-60" />
                </button>
                <StepButton label="Año siguiente" reason={yearReason(year + 1)} onClick={() => moveYear(1)}>
                  <ChevronRight className="size-4" />
                </StepButton>
              </>
            ) : (
              <>
                <StepButton label="Años anteriores" reason={minYear !== null && pageStart <= minYear ? NO_DATA_YEAR_REASON : null} onClick={() => setYear(pageStart - 1)}>
                  <ChevronLeft className="size-4" />
                </StepButton>
                <span className="font-heading text-[15px] font-bold tabular-nums">
                  {pageStart} – {pageStart + YEARS_PER_PAGE - 1}
                </span>
                <StepButton label="Años siguientes" reason={pageStart + YEARS_PER_PAGE - 1 >= anchorYear ? FUTURE_YEAR_REASON : null} onClick={() => setYear(pageStart + YEARS_PER_PAGE)}>
                  <ChevronRight className="size-4" />
                </StepButton>
              </>
            )}
          </div>

          <div role="group" aria-label={view === 'months' ? `Meses de ${year}` : 'Años'} className="grid grid-cols-3 gap-1.5" onKeyDown={handleGridKey}>
            {view === 'months'
              ? MONTH_SHORT.map((name, index) => {
                  const month = toMonth(year, index + 1);
                  return (
                    <Cell
                      key={month}
                      name={name}
                      label={describe(month)}
                      reason={monthReason(month)}
                      selected={month === value}
                      current={month === current}
                      focusable={month === focusedMonth}
                      cellRef={(node) => {
                        cellRefs.current[month] = node;
                      }}
                      onSelect={() => pick(month)}
                    />
                  );
                })
              : pageYears.map((candidate) => (
                  <Cell
                    key={candidate}
                    name={String(candidate)}
                    label={String(candidate)}
                    reason={yearReason(candidate)}
                    selected={candidate === parse(value).year}
                    current={candidate === parse(current).year}
                    focusable={candidate === focusedYear}
                    cellRef={(node) => {
                      cellRefs.current[candidate] = node;
                    }}
                    onSelect={() => pickYear(candidate)}
                  />
                ))}
          </div>

          <div className="mt-2.5 flex gap-1.5 border-t border-border pt-2.5">
            {shortcuts.map((shortcut) => {
              const reason = monthReason(shortcut.month);
              return (
                <Guarded key={shortcut.key} reason={reason}>
                  <Button
                    type="button"
                    variant="ghost"
                    aria-label={reason ? `${shortcut.aria}, ${reason.toLowerCase()}` : shortcut.aria}
                    aria-disabled={reason ? 'true' : undefined}
                    onClick={reason ? undefined : () => pick(shortcut.month)}
                    className={cn('h-10 flex-1 px-2 text-[13px] font-bold', reason && 'cursor-not-allowed opacity-50')}
                  >
                    {shortcut.text}
                  </Button>
                </Guarded>
              );
            })}
          </div>
        </PopoverContent>
      </Popover>
    </TooltipProvider>
  );
}
