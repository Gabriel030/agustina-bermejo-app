import { cva } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const chip = cva(
  'flex min-h-11 items-baseline gap-2 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
  {
    variants: {
      variant: {
        card: 'rounded-xl border border-border bg-surface px-3.5 py-2 hover:bg-neutral-bg',
        inline: 'rounded-lg border border-transparent px-3 py-1.5 hover:bg-neutral-bg/60',
      },
      pressed: { true: '', false: '' },
    },
    compoundVariants: [
      { variant: 'card', pressed: true, className: 'border-primary bg-accent-soft ring-1 ring-primary hover:bg-accent-soft' },
      { variant: 'inline', pressed: true, className: 'border-border bg-surface ring-2 ring-primary' },
    ],
    defaultVariants: { variant: 'card', pressed: false },
  },
);

const COUNT = {
  card: 'text-[22px] leading-tight font-bold tabular-nums text-ink',
  inline: 'font-heading text-xl font-bold tabular-nums text-ink',
};

export function KpiFilterChip({ label, count, pressed, onClick, variant = 'card', ariaLabel }) {
  const isCard = variant === 'card';
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={ariaLabel}
      onClick={onClick}
      className={chip({ variant, pressed: !!pressed })}
    >
      {isCard ? <b className={COUNT.card}>{count}</b> : <span className={COUNT.inline}>{count}</span>}
      {isCard && ' '}
      <span className={cn('text-sm text-ink-soft', isCard && pressed && 'font-semibold text-primary')}>{label}</span>
    </button>
  );
}
