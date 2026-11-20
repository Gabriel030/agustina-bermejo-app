import { KpiFilterChip } from '@/components/KpiFilterChip';
import { activeResultFilter, kpiCards } from '../lib/closureSheet';

export function ClosureSummaryCards({ summary, columnFilters, onToggle }) {
  const selected = activeResultFilter(columnFilters);
  const pressedKey = selected?.length === 1 ? selected[0] : null;
  return (
    <div className="flex flex-wrap gap-1">
      {kpiCards(summary).map((card) => (
        <KpiFilterChip
          key={card.key}
          variant="inline"
          label={card.label}
          count={card.count}
          pressed={pressedKey === card.key}
          ariaLabel={`${card.label}: ${card.count}. Filtrar la planilla`}
          onClick={() => onToggle(card.key)}
        />
      ))}
    </div>
  );
}
