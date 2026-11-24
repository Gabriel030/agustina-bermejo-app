import { Lock } from 'lucide-react';
import { Notice } from '@/components/Notice';
import { SEND_REASON_ID } from '../lib/invitationWaveActions';

export function WaveAccessNotice({ access }) {
  if (!access.manage) {
    return (
      <Notice>
        <span className="flex items-start gap-2">
          <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>Tenés acceso de solo lectura: podés ver el avance y exportar el reporte.</span>
        </span>
      </Notice>
    );
  }
  if (!access.send) {
    return (
      <Notice tone="warning" id={SEND_REASON_ID}>
        <span className="flex items-start gap-2">
          <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            Para iniciar, reanudar, cancelar o reintentar necesitás el permiso <code className="font-semibold">notification.send_invitation_wave</code>. Pedíselo al Director.
          </span>
        </span>
      </Notice>
    );
  }
  return null;
}
