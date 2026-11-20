import { useState } from 'react';
import { Check, ChevronsUpDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';

export function OptionCombobox({ label, options, value, onChange, placeholder = 'Todas', disabled }) {
  const [open, setOpen] = useState(false);
  const selected = options.find((option) => option.value === value);
  return (
    <div className="flex min-w-[200px] flex-1 flex-col gap-1.5">
      <span className="text-size-13 font-semibold text-ink">{label}</span>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button type="button" variant="outline" role="combobox" aria-expanded={open} aria-label={label} disabled={disabled} className="h-auto min-h-9 justify-between py-1.5 whitespace-normal">
            <span className="min-w-0 text-left break-words">{selected?.label ?? placeholder}</span>
            <ChevronsUpDown className="size-4 shrink-0 opacity-60" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[--radix-popover-trigger-width] min-w-64 p-0">
          <Command>
            <CommandInput placeholder="Buscar…" />
            <CommandList>
              <CommandEmpty>Sin resultados.</CommandEmpty>
              <CommandGroup>
                <CommandItem
                  value="__all"
                  onSelect={() => {
                    onChange(null);
                    setOpen(false);
                  }}
                >
                  {placeholder}
                </CommandItem>
                {options.map((option) => (
                  <CommandItem
                    key={option.value}
                    value={option.label}
                    onSelect={() => {
                      onChange(option.value);
                      setOpen(false);
                    }}
                  >
                    <Check className={`size-4 ${option.value === value ? 'opacity-100' : 'opacity-0'}`} />
                    {option.label}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
