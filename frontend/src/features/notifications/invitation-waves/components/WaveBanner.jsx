import { Check, Mail, Pause, Pencil, Play, X } from 'lucide-react';
import { Notice } from '@/components/Notice';
import { bannerText } from '../lib/invitationWaveBanner';

const ICONS = { DRAFT: Pencil, RUNNING: Play, PAUSED: Pause, COMPLETED: Check, CANCELLED: X };
const TONES = { DRAFT: 'neutral', RUNNING: 'accent', PAUSED: 'warning', COMPLETED: 'success', CANCELLED: 'neutral' };

export function WaveBanner({ wave, sendWindow }) {
  const Icon = ICONS[wave.status] ?? Mail;
  return (
    <Notice tone={TONES[wave.status] ?? 'neutral'} role="status">
      <span className="flex items-start gap-2">
        <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <span>{bannerText(wave, sendWindow)}</span>
      </span>
    </Notice>
  );
}
