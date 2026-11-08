import { GlassCard } from '@/components/GlassCard';
import { cn } from '@/lib/utils';

export function StickyActionBar({ summary, hint, children, className }) {
  return (
    <GlassCard
      className={cn(
        'sticky bottom-[calc(14px+env(safe-area-inset-bottom,0px))] z-5 flex flex-wrap items-center gap-3.5 px-4 py-3.5',
        className,
      )}
    >
      <div className="min-w-44 flex-1">
        <b className="text-lg tabular-nums text-ink">{summary}</b>
        {hint && <small className="block text-xs text-ink-faint">{hint}</small>}
      </div>
      <div className="flex w-full flex-col-reverse gap-2 sm:w-auto sm:flex-row sm:items-center">{children}</div>
    </GlassCard>
  );
}
