import { cn } from '@/lib/utils';

export function KpiFilterChip({ label, count, pressed, onClick }) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        'flex min-h-11 items-baseline gap-2 rounded-xl border border-border bg-surface px-3.5 py-2 text-left transition-colors hover:bg-neutral-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        pressed && 'border-primary bg-accent-soft ring-1 ring-primary hover:bg-accent-soft',
      )}
    >
      <b className="text-[22px] leading-tight font-bold tabular-nums text-ink">{count}</b>{' '}
      <span className={cn('text-sm text-ink-soft', pressed && 'font-semibold text-primary')}>{label}</span>
    </button>
  );
}
