import { ListFilter } from 'lucide-react';
import { Select, SelectTrigger, SelectContent, SelectItem, SelectValue } from '@/components/ui/select';
import { TABS_ESTADO } from '@/lib/notas';
import { cn } from '@/lib/utils';

export function PlanStatusFilter({ value, onChange, className }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger aria-label="Filtrar por estado" className={cn('data-[size=default]:h-11 gap-1.5', className)}>
        <ListFilter className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent align="end">
        {TABS_ESTADO.map((option) => (
          <SelectItem key={option.id} value={option.id}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
