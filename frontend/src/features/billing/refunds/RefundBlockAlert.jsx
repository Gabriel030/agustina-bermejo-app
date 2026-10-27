import { AlertTriangle, Info } from 'lucide-react';
import { cn } from '@/lib/utils';

const TONES = {
  danger: { box: 'border-danger/30 bg-danger-bg', icon: 'text-danger', Icon: AlertTriangle },
  neutral: { box: 'border-border bg-neutral-bg', icon: 'text-ink-soft', Icon: Info },
};

export function RefundBlockAlert({ id, block, className, children }) {
  const tone = TONES[block.tone] ?? TONES.neutral;
  return (
    <div id={id} className={cn('flex items-start gap-3 rounded-2xl border px-4 py-3', tone.box, className)}>
      <tone.Icon className={cn('mt-0.5 size-4 shrink-0', tone.icon)} aria-hidden="true" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5 text-sm">
        <span className="font-semibold text-ink">{block.title}</span>
        <span className="text-ink-soft">{block.description}</span>
      </div>
      {children}
    </div>
  );
}
