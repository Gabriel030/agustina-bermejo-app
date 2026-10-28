import { cn } from '@/lib/utils';

export function TabCount({ count, className }) {
  if (!count || count <= 0) return null;
  return (
    <span
      data-slot="tab-count"
      className={cn(
        'inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-accent-soft px-1 text-[0.6875rem] font-bold leading-none tabular-nums text-brand',
        '[[data-state=active]_&]:bg-white/25 [[data-state=active]_&]:text-white',
        className,
      )}
    >
      {count}
    </span>
  );
}
