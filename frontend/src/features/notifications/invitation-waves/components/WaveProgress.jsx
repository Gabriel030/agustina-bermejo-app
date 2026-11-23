import { ProgressBar } from '@/components/ProgressBar';
import { formatDecimal } from '@/lib/formatNumber';
import { invitedCount, progressPercent } from '../lib/invitationWaveMath';

export function WaveProgress({ wave, label = 'Avance de la oleada', className }) {
  return (
    <div className={className}>
      <ProgressBar value={progressPercent(wave)} label={label} showValue={false} />
      <p className="mt-1 text-size-12-5 tabular-nums text-ink-soft">
        {formatDecimal(invitedCount(wave))} de {formatDecimal(wave.invitableCount ?? 0)} invitados
      </p>
    </div>
  );
}
