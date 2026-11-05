import { formatChartValue } from '@/components/charts/chartFormat';
import { cn } from '@/lib/utils';

const TONE_CLASSES = {
  accent: 'bg-brand',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
};

export function ProgressBar({ value, label, tone = 'accent', showValue = true, className }) {
  const clamped = Math.min(100, Math.max(0, Number(value) || 0));
  const text = formatChartValue(clamped, { unit: 'percent' });

  return (
    <div className={cn('flex flex-col gap-1', className)}>
      {(label || showValue) && (
        <div className="flex items-center justify-between gap-2 text-xs">
          {label && <span className="text-ink-soft">{label}</span>}
          {showValue && <span className="ml-auto font-medium tabular-nums text-ink">{text}</span>}
        </div>
      )}
      <div
        role="progressbar"
        aria-label={label ?? 'Progreso'}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={clamped}
        aria-valuetext={text}
        className="h-2 w-full overflow-hidden rounded-full bg-neutral-bg"
      >
        <div
          className={cn('h-full rounded-full transition-[width] duration-500 motion-reduce:transition-none', TONE_CLASSES[tone] ?? TONE_CLASSES.accent)}
          style={{ width: `${clamped}%` }}
        />
      </div>
    </div>
  );
}
