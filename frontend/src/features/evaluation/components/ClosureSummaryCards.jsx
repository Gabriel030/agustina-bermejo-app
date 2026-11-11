import { cn } from '@/lib/utils';
import { activeResultFilter, kpiCards } from '../lib/closureSheet';

export function ClosureSummaryCards({ summary, columnFilters, onToggle }) {
  const selected = activeResultFilter(columnFilters);
  const pressedKey = selected?.length === 1 ? selected[0] : null;
  return (
    <div className="flex flex-wrap gap-1">
      {kpiCards(summary).map((card) => (
        <button
          key={card.key}
          type="button"
          aria-pressed={pressedKey === card.key}
          aria-label={`${card.label}: ${card.count}. Filtrar la planilla`}
          onClick={() => onToggle(card.key)}
          className={cn(
            'flex min-h-11 items-baseline gap-2 rounded-lg border border-transparent px-3 py-1.5 text-left transition-colors hover:bg-neutral-bg/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            pressedKey === card.key && 'border-border bg-surface ring-2 ring-primary',
          )}
        >
          <span className="font-heading text-xl font-bold tabular-nums text-ink">{card.count}</span>
          <span className="text-sm text-ink-soft">{card.label}</span>
        </button>
      ))}
    </div>
  );
}
