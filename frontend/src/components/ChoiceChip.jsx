import { cva } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const SHAPE = {
  pill: 'rounded-full',
  rounded: 'rounded-lg',
};

const SIZE = {
  xs: 'px-3 py-1 text-size-12',
  sm: 'px-3 py-1 text-size-13',
  md: 'px-4 py-1.5 text-size-13',
  lg: 'px-4 py-2 text-size-13',
};

const TONE = {
  soft: {
    selected: 'bg-accent-soft text-brand',
    idle: 'bg-neutral-bg/60 text-ink-soft hover:bg-neutral-bg',
  },
  solid: {
    selected: 'bg-accent text-on-accent',
    idle: 'bg-neutral-bg/60 text-ink-soft hover:bg-neutral-bg',
  },
  text: {
    selected: 'bg-accent-soft text-brand',
    idle: 'text-ink-faint hover:text-ink',
  },
};

const chipBase = cva('transition-colors', {
  variants: {
    shape: SHAPE,
    size: SIZE,
    strong: { true: 'font-medium', false: '' },
  },
  defaultVariants: { shape: 'pill', size: 'sm', strong: false },
});

export function ChoiceChip({ selected, onClick, shape, size, tone = 'soft', strong, className, children, ...rest }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={cn(
        chipBase({ shape, size, strong }),
        selected ? ['font-medium', TONE[tone].selected] : TONE[tone].idle,
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}
