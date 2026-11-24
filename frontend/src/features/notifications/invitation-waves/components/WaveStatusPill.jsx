import { StatusPill } from '@/components/StatusPill';
import { RECIPIENT_STATUS, WAVE_STATUS } from '../lib/invitationWaveLabels';

export function WaveStatusPill({ status }) {
  const entry = WAVE_STATUS[status];
  return (
    <StatusPill tone={entry?.tone} icon={entry?.icon}>
      {entry?.label ?? status}
    </StatusPill>
  );
}

export function RecipientStatusPill({ status }) {
  const entry = RECIPIENT_STATUS[status];
  return (
    <StatusPill tone={entry?.tone} icon={entry?.icon}>
      {entry?.label ?? status}
    </StatusPill>
  );
}
