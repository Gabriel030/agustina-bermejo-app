import ConfirmActionDialog from '@/components/ConfirmActionDialog';
import { buildConfirmation } from '../lib/invitationWaveConfirmations';

export function WaveActionDialog({ controller, settings }) {
  const { wave, confirming, pending } = controller;
  const confirmation = confirming ? buildConfirmation(confirming, wave, settings) : null;
  if (!confirmation) return null;
  return (
    <ConfirmActionDialog
      open
      onOpenChange={(next) => !next && controller.dismiss()}
      title={confirmation.title}
      description={confirmation.description}
      confirmLabel={confirmation.confirmLabel}
      confirmVariant={confirmation.confirmVariant}
      cancelLabel={confirmation.cancelLabel}
      pending={pending}
      onConfirm={controller.confirm}
      buttonClassName="min-h-11 md:min-h-8"
    >
      <ul className="flex flex-col gap-1.5" aria-label="Resumen de la acción">
        {confirmation.items.map(([label, value]) => (
          <li key={label} className="flex justify-between gap-3 border-b border-border pb-1.5 text-size-13-5">
            <span className="text-ink-soft">{label}</span>
            <b className="text-right tabular-nums text-ink">{value}</b>
          </li>
        ))}
      </ul>
      {confirmation.extra && <p className="text-size-13 text-ink-soft">{confirmation.extra}</p>}
    </ConfirmActionDialog>
  );
}
