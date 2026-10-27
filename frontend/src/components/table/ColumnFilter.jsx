import { useState } from 'react';
import { ListFilter } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export function ColumnFilter({ label, options, selected, onChange }) {
  const [open, setOpen] = useState(false);
  const allValues = options.map((option) => option.value);
  const effectiveSelected = selected ?? allValues;
  const isFiltering = selected != null && effectiveSelected.length < allValues.length;
  const allChecked = effectiveSelected.length === allValues.length;

  function commit(nextValues) {
    onChange(nextValues.length === allValues.length ? undefined : nextValues);
  }

  function toggleAll() {
    commit(allChecked ? [] : allValues);
  }

  function toggleOne(value) {
    const next = effectiveSelected.includes(value)
      ? effectiveSelected.filter((item) => item !== value)
      : [...effectiveSelected, value];
    commit(allValues.filter((item) => next.includes(item)));
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label={`Filtrar por ${label}`}
          className={cn(
            'inline-flex size-6 items-center justify-center rounded-md text-ink-faint transition-colors hover:bg-neutral-bg hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            isFiltering && 'bg-accent-soft text-primary',
          )}
        >
          <ListFilter className="size-3.5" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-56 gap-1 bg-dialog-bg p-2">
        <label className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm font-semibold text-ink hover:bg-neutral-bg">
          <Checkbox checked={allChecked} onCheckedChange={toggleAll} />
          Seleccionar todo
        </label>
        <div className="flex max-h-60 flex-col overflow-y-auto border-t border-border pt-1">
          {options.map((option) => (
            <label
              key={String(option.value)}
              className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm text-ink hover:bg-neutral-bg"
            >
              <Checkbox checked={effectiveSelected.includes(option.value)} onCheckedChange={() => toggleOne(option.value)} />
              <span className="min-w-0 flex-1 truncate">{option.label}</span>
              {option.count != null && <span className="text-xs tabular-nums text-ink-faint">{option.count}</span>}
            </label>
          ))}
        </div>
        {isFiltering && (
          <Button type="button" variant="ghost" size="xs" className="self-start" onClick={() => onChange(undefined)}>
            Limpiar filtro
          </Button>
        )}
      </PopoverContent>
    </Popover>
  );
}
