import { cn } from '@/lib/utils';

export function GlassCard({ className, interactive = false, ...props }) {
  return (
    <div
      className={cn(
        'rounded-card border border-border bg-surface shadow-card backdrop-blur-(--surface-blur) backdrop-saturate-(--surface-saturate)',
        interactive &&
          'cursor-pointer transition-[transform,border-color,box-shadow] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] hover:translate-y-(--card-lift-y) hover:border-brand/40',
        className,
      )}
      {...props}
    />
  );
}
