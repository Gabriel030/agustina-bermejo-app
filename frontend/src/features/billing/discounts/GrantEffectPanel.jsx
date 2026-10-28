import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { InfoTip } from '@/components/charts/InfoTip';
import { cn } from '@/lib/utils';
import { describeGrantEffect } from './grantForm';

const DOT_TONES = {
  success: 'bg-success',
  warning: 'bg-warning',
  neutral: 'bg-ink-faint',
};

export function GrantEffectPanel({ preview, values, isLoading, isReady }) {
  let body;
  if (!isReady) {
    body = <p className="text-sm text-ink-faint">Cargá el valor y el período para ver qué va a pasar.</p>;
  } else if (isLoading || !preview) {
    body = <Skeleton className="h-16 w-full rounded-xl" />;
  } else if (!preview.allowed) {
    body = <p className="text-sm text-ink-faint">Corregí los problemas marcados para ver qué va a pasar.</p>;
  } else {
    body = (
      <ul className="flex flex-col gap-2">
        {describeGrantEffect(preview, values).map((line) => (
          <li key={line.text} className="grid grid-cols-[8px_minmax(0,1fr)] items-start gap-2.5 text-[13px] leading-snug text-ink">
            <span aria-hidden="true" className={cn('mt-1.5 size-2 rounded-full', DOT_TONES[line.tone])} />
            <span>{line.text}</span>
          </li>
        ))}
      </ul>
    );
  }
  return (
    <GlassCard className="flex flex-col gap-3 p-5">
      <div className="flex items-center gap-2">
        <h2 className="font-heading text-base font-semibold text-ink">Qué pasa</h2>
        <InfoTip>Se calcula con el servidor. La cuota real puede variar si cambia el precio o si otro descuento gana ese mes.</InfoTip>
      </div>
      <div aria-live="polite">{body}</div>
    </GlassCard>
  );
}
