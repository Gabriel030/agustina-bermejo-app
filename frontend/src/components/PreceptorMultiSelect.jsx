import { useId, useMemo, useState } from 'react';
import { Check, Search, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { usePreceptors } from '@/hooks/usePreceptors';
import { cn } from '@/lib/utils';

const COMPACT_VIEWPORT_QUERY = '(width < 48rem)';
const MAX_VISIBLE_CHIPS = 4;
const COLLAPSED_CHIPS = 3;

function preceptorName(preceptor) {
  return `${preceptor.nombre} ${preceptor.apellido}`.trim();
}

function initialsOf(name) {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}

function PickerTrigger({ label, expanded, ...props }) {
  return (
    <Button
      type="button"
      variant="text"
      aria-haspopup="listbox"
      aria-expanded={expanded}
      className="min-h-8 rounded-full px-2.5 font-medium hover:bg-accent-soft"
      {...props}
    >
      <Search className="h-3.5 w-3.5" aria-hidden="true" />
      {label}
    </Button>
  );
}

function PreceptorPickerList({ label, preceptors, selectedIds, isLoading, isError, onToggle, onDone }) {
  const [query, setQuery] = useState('');
  const normalized = query.trim().toLowerCase();
  const visible = preceptors.filter(
    (p) => !normalized || preceptorName(p).toLowerCase().includes(normalized) || (p.email ?? '').toLowerCase().includes(normalized),
  );
  const selectedCount = selectedIds.length;

  return (
    <div className="flex min-h-0 flex-col gap-2">
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-ink-faint" aria-hidden="true" />
        <Input
          className="pl-8"
          placeholder="Buscar por nombre"
          aria-label="Buscar preceptor"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>
      {isLoading ? (
        <p className="px-2 py-4 text-size-13 text-ink-faint">Cargando preceptores…</p>
      ) : isError ? (
        <p role="alert" className="px-2 py-4 text-size-13 text-danger">
          No pudimos cargar los preceptores.
        </p>
      ) : visible.length === 0 ? (
        <div className="px-2 py-4 text-center">
          <p className="text-size-13 font-medium text-ink">Sin resultados</p>
          <p className="text-size-12 text-ink-faint">Solo aparece personal con rol preceptor.</p>
        </div>
      ) : (
        <div role="listbox" aria-multiselectable="true" aria-label={label} className="flex max-h-60 flex-col gap-0.5 overflow-y-auto">
          {visible.map((preceptor) => {
            const selected = selectedIds.includes(preceptor.id);
            const name = preceptorName(preceptor);
            return (
              <Button
                key={preceptor.id}
                type="button"
                variant="ghost"
                role="option"
                aria-selected={selected}
                onClick={() => onToggle(preceptor.id)}
                className="h-auto min-h-11 w-full justify-start gap-2.5 px-2 py-1.5 text-left font-normal md:min-h-0"
              >
                <span
                  className={cn(
                    'flex h-4 w-4 shrink-0 items-center justify-center rounded border border-input',
                    selected && 'border-accent bg-accent text-on-accent',
                  )}
                  aria-hidden="true"
                >
                  {selected && <Check className="h-3 w-3" />}
                </span>
                <span
                  className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent-soft text-size-11 font-bold text-brand"
                  aria-hidden="true"
                >
                  {initialsOf(name)}
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-size-13 font-medium text-ink">{name}</span>
                  <span className="block truncate text-size-12 text-ink-faint">{preceptor.email}</span>
                </span>
              </Button>
            );
          })}
        </div>
      )}
      <div className="flex items-center justify-between gap-2 border-t border-border pt-2">
        <span className="text-size-12 text-ink-faint">
          {selectedCount === 0 ? 'Ninguno elegido' : selectedCount === 1 ? '1 elegido' : `${selectedCount} elegidos`}
        </span>
        <Button type="button" size="sm" variant="primary" onClick={onDone}>
          Listo
        </Button>
      </div>
    </div>
  );
}

export function PreceptorMultiSelect({ label = 'Responsable', value, onChange, required = false, disabled = false, error, hint, id }) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const isCompact = useMediaQuery(COMPACT_VIEWPORT_QUERY);
  const { data, isLoading, isError } = usePreceptors();
  const preceptors = useMemo(() => data ?? [], [data]);

  function nameOf(preceptorId) {
    const found = preceptors.find((p) => p.id === preceptorId);
    return found ? preceptorName(found) : `Preceptor ${preceptorId}`;
  }

  const showAll = expanded || value.length <= MAX_VISIBLE_CHIPS;
  const shown = showAll ? value : value.slice(0, COLLAPSED_CHIPS);
  const triggerLabel = value.length ? 'Agregar preceptor' : 'Buscar preceptor';

  function toggle(preceptorId) {
    onChange(value.includes(preceptorId) ? value.filter((v) => v !== preceptorId) : [...value, preceptorId]);
  }

  const picker = (
    <PreceptorPickerList
      label={label}
      preceptors={preceptors}
      selectedIds={value}
      isLoading={isLoading}
      isError={isError}
      onToggle={toggle}
      onDone={() => setOpen(false)}
    />
  );

  return (
    <div className="flex flex-col gap-1.5">
      <span id={`${fieldId}-label`} className="text-size-12 font-medium uppercase tracking-wide text-ink-faint">
        {label}
        {required && (
          <span aria-hidden="true" className="ml-1 text-danger">
            *
          </span>
        )}
      </span>
      <div
        role="group"
        aria-labelledby={`${fieldId}-label`}
        aria-describedby={error ? `${fieldId}-error` : undefined}
        className={cn(
          'flex min-h-10 flex-wrap items-center gap-1.5 rounded-control border border-input bg-control px-2 py-1.5 dark:bg-input/30',
          error && 'border-destructive ring-3 ring-destructive/20',
          disabled && 'bg-(--disabled-bg) text-(--disabled-fg)',
        )}
      >
        {shown.map((preceptorId) => (
          <span key={preceptorId} className="inline-flex items-center gap-1 rounded-full bg-accent-soft py-0.5 pl-2.5 pr-1 text-size-13 text-ink">
            {nameOf(preceptorId)}
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              disabled={disabled}
              aria-label={`Quitar a ${nameOf(preceptorId)}`}
              onClick={() => onChange(value.filter((v) => v !== preceptorId))}
              className="size-5 rounded-full text-ink-soft"
            >
              <X className="h-3 w-3" aria-hidden="true" />
            </Button>
          </span>
        ))}
        {value.length > MAX_VISIBLE_CHIPS && (
          <Button
            type="button"
            variant="secondary"
            size="xs"
            onClick={() => setExpanded((current) => !current)}
            className="rounded-full px-2.5 text-size-13"
          >
            {expanded ? 'Ver menos' : `+${value.length - COLLAPSED_CHIPS} más`}
          </Button>
        )}
        {isCompact ? (
          <>
            <PickerTrigger label={triggerLabel} expanded={open} disabled={disabled} onClick={() => setOpen(true)} />
            <Sheet open={open} onOpenChange={setOpen}>
              <SheetContent side="bottom" className="max-h-[85dvh] rounded-t-xl p-4">
                <SheetHeader className="p-0">
                  <SheetTitle>{label}</SheetTitle>
                </SheetHeader>
                {picker}
              </SheetContent>
            </Sheet>
          </>
        ) : (
          <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
              <PickerTrigger label={triggerLabel} expanded={open} disabled={disabled} />
            </PopoverTrigger>
            <PopoverContent align="start" className="w-80">
              {picker}
            </PopoverContent>
          </Popover>
        )}
      </div>
      {error && (
        <p id={`${fieldId}-error`} role="alert" className="text-size-12-5 text-danger">
          {error}
        </p>
      )}
      {hint && !error && <p className="text-size-11 text-ink-faint">{hint}</p>}
    </div>
  );
}
