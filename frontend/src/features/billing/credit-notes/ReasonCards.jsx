import { cn } from '@/lib/utils';

export function ReasonCards({ labelledBy, options, value, onChange }) {
  const selectedIndex = Math.max(0, options.findIndex((option) => option.value === value));

  function handleKeyDown(event) {
    const forward = event.key === 'ArrowRight' || event.key === 'ArrowDown';
    const backward = event.key === 'ArrowLeft' || event.key === 'ArrowUp';
    if (!forward && !backward) return;
    event.preventDefault();
    const next = (selectedIndex + (forward ? 1 : options.length - 1)) % options.length;
    onChange(options[next].value);
    event.currentTarget.querySelectorAll('[role="radio"]')[next]?.focus();
  }

  return (
    <div role="radiogroup" aria-labelledby={labelledBy} onKeyDown={handleKeyDown} className="grid grid-cols-1 gap-2 sm:grid-cols-2">
      {options.map((option, index) => {
        const checked = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={index === selectedIndex ? 0 : -1}
            onClick={() => onChange(option.value)}
            className={cn(
              'flex flex-col gap-0.5 rounded-2xl border px-4 py-3 text-left transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
              checked ? 'border-brand bg-accent-soft' : 'border-border bg-surface hover:bg-neutral-bg/60',
            )}
          >
            <span className="text-sm font-semibold text-ink">{option.title}</span>
            <span className="text-xs text-ink-soft">{option.description}</span>
          </button>
        );
      })}
    </div>
  );
}
