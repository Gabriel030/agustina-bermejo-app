import { ChevronLeft, ChevronRight } from 'lucide-react';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Button } from '@/components/ui/button';

const VIEWS = [
  { value: 'month', label: 'Mes' },
  { value: 'week', label: 'Semana' },
  { value: 'agenda', label: 'Agenda' },
];

export function CalendarToolbar({ view, title, onChangeView, onToday, onShift }) {
  const unitLabel = view === 'week' ? 'Semana' : 'Mes';
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-2">
        <Button variant="outline" onClick={onToday} className="max-md:min-h-11">
          Hoy
        </Button>
        <Button variant="outline" size="icon" className="max-md:size-11" aria-label={`${unitLabel} anterior`} onClick={() => onShift(-1)}>
          <ChevronLeft aria-hidden="true" />
        </Button>
        <Button variant="outline" size="icon" className="max-md:size-11" aria-label={`${unitLabel} siguiente`} onClick={() => onShift(1)}>
          <ChevronRight aria-hidden="true" />
        </Button>
        <h2 aria-live="polite" className="font-heading text-lg font-bold text-ink first-letter:uppercase">
          {title}
        </h2>
      </div>
      <div role="group" aria-label="Vista del calendario" className="flex gap-1">
        {VIEWS.map(({ value, label }) => (
          <ChoiceChip key={value} selected={view === value} onClick={() => onChangeView(value)} className="min-h-11 sm:min-h-0">
            {label}
          </ChoiceChip>
        ))}
      </div>
    </div>
  );
}
