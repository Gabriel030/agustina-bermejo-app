import { Check, Clock, Minus, TriangleAlert } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { cn } from '@/lib/utils';

const TONES = {
  success: { icon: Check, tile: 'bg-success-bg text-pill-success', card: '', action: '' },
  warning: { icon: Clock, tile: 'bg-warning-bg text-pill-warning', card: 'border-warning/45', action: 'text-pill-warning' },
  danger: { icon: TriangleAlert, tile: 'bg-danger-bg text-pill-danger', card: 'border-danger/45', action: 'text-pill-danger' },
  neutral: { icon: Minus, tile: 'bg-neutral-bg text-pill-neutral', card: '', action: '' },
};

export function BackupSummaryCard({ tone, lead, text, needsSupport }) {
  const config = TONES[tone];
  const Icon = config.icon;
  return (
    <GlassCard className={cn('flex items-start gap-4 p-5', config.card)} aria-label="Resumen">
      <span className={cn('grid size-11 shrink-0 place-items-center rounded-control', config.tile)}>
        <Icon className="size-5.5" aria-hidden="true" />
      </span>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="font-heading text-size-17 leading-snug font-semibold text-pretty text-ink">
          <b className="font-bold">{lead}</b> {text}
        </p>
        {needsSupport && <p className={cn('text-size-14 font-bold', config.action)}>Avisá a soporte técnico.</p>}
      </div>
    </GlassCard>
  );
}
