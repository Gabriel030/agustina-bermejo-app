import { LayoutGrid, List } from 'lucide-react';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';

const ITEM_CLASS =
  'min-h-11 min-w-11 gap-1.5 px-3 text-ink-soft data-[state=on]:bg-accent-soft data-[state=on]:text-brand';

export function PlanViewSwitch({ value, onChange }) {
  return (
    <ToggleGroup
      type="single"
      variant="outline"
      spacing={0}
      value={value}
      onValueChange={(next) => next && onChange(next)}
      aria-label="Vista del plan"
    >
      <ToggleGroupItem value="mapa" className={ITEM_CLASS}>
        <LayoutGrid aria-hidden="true" />
        Mapa
      </ToggleGroupItem>
      <ToggleGroupItem value="lista" className={ITEM_CLASS}>
        <List aria-hidden="true" />
        Lista
      </ToggleGroupItem>
    </ToggleGroup>
  );
}
