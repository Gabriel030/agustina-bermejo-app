import { useEffect, useState } from 'react';
import { Check, ChevronsUpDown, Info } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

export const ALL_SCOPE = 'ALL';

function fold(text) {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

function matchesOffer(value, search, keywords = []) {
  return keywords.some((keyword) => fold(keyword).includes(fold(search.trim()))) ? 1 : 0;
}

function firstMatch(term, programs, courses, fallback) {
  if (!term.trim()) return fallback;
  const candidates = [
    ...(programs ?? []).map((program) => ({ value: `program:${program.id}`, name: program.name })),
    ...(courses ?? []).map((course) => ({ value: `course:${course.id}`, name: course.name })),
  ];
  return candidates.find((candidate) => matchesOffer(candidate.value, term, [candidate.name]))?.value ?? ALL_SCOPE;
}

function OfferItem({ value, name, selected, onSelect }) {
  return (
    <CommandItem value={value} keywords={[name]} onSelect={onSelect} className="min-h-11 px-3">
      <span className="min-w-0 flex-1 truncate">{name}</span>
      <Check className={cn('size-4 shrink-0', selected ? 'opacity-100' : 'opacity-0')} />
    </CommandItem>
  );
}

export function OfferCombobox({ value, onChange, programs, courses, selectedName }) {
  const [open, setOpen] = useState(false);
  const loading = programs.isLoading || courses.isLoading;
  const empty = !loading && !programs.data?.length && !courses.data?.length;
  const triggerLabel = value === ALL_SCOPE ? 'Todas las ofertas' : (selectedName ?? '…');

  const [search, setSearch] = useState('');
  const [active, setActive] = useState(value);

  const handleOpenChange = (next) => {
    if (next) {
      setSearch('');
      setActive(value);
    }
    setOpen(next);
  };

  useEffect(() => {
    if (open) setActive(firstMatch(search, programs.data, courses.data, value));
  }, [open, search, value, programs.data, courses.data]);

  const choose = (next) => {
    onChange(next);
    setOpen(false);
  };

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div className="flex items-center gap-1.5">
        <Label htmlFor="offer" className="text-[11px] font-bold uppercase tracking-wide text-ink-faint">
          Oferta
        </Label>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <button type="button" aria-label="Qué limita la oferta" className="text-ink-faint hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
                <Info className="size-3.5" />
              </button>
            </TooltipTrigger>
            <TooltipContent>Limita los números a una carrera o a un curso.</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>
      <Popover open={open} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>
          <Button id="offer" type="button" variant="outline" role="combobox" aria-expanded={open} className="h-10 w-full min-w-0 justify-between gap-2 font-normal">
            <span className="truncate">{triggerLabel}</span>
            <ChevronsUpDown className="opacity-60" />
          </Button>
        </PopoverTrigger>
        <PopoverContent align="start" className="w-[max(var(--radix-popover-trigger-width),16rem)] max-w-[calc(100vw-2rem)] gap-0 bg-dialog-bg p-0">
          <Command filter={matchesOffer} value={active} onValueChange={setActive}>
            <CommandInput value={search} onValueChange={setSearch} placeholder="Buscar carrera o curso…" aria-label="Buscar carrera o curso" />
            <CommandList className="max-h-64">
              <CommandEmpty>Sin resultados</CommandEmpty>
              <CommandGroup forceMount>
                <CommandItem value={ALL_SCOPE} forceMount onSelect={() => choose(ALL_SCOPE)} className="min-h-11 px-3">
                  <span className="min-w-0 flex-1 truncate">Todas las ofertas</span>
                  <Check className={cn('size-4 shrink-0', value === ALL_SCOPE ? 'opacity-100' : 'opacity-0')} />
                </CommandItem>
              </CommandGroup>
              {loading && <div className="px-3 py-3 text-sm text-ink-soft">Cargando ofertas…</div>}
              {empty && <div className="px-3 py-3 text-sm text-ink-soft">No hay carreras ni cursos cargados</div>}
              {!!programs.data?.length && (
                <CommandGroup heading="Carreras">
                  {programs.data.map((program) => (
                    <OfferItem key={program.id} value={`program:${program.id}`} name={program.name} selected={value === `program:${program.id}`} onSelect={() => choose(`program:${program.id}`)} />
                  ))}
                </CommandGroup>
              )}
              {!!courses.data?.length && (
                <CommandGroup heading="Cursos">
                  {courses.data.map((course) => (
                    <OfferItem key={course.id} value={`course:${course.id}`} name={course.name} selected={value === `course:${course.id}`} onSelect={() => choose(`course:${course.id}`)} />
                  ))}
                </CommandGroup>
              )}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
