import { StickyActionBar } from '@/components/StickyActionBar';
import { formatDecimal } from '@/lib/formatNumber';
import { invitedCount } from '../lib/invitationWaveMath';
import { actionButtonProps } from '../lib/invitationWaveActions';

function barHint(wave) {
  if (wave.failedTotal > 0) return `${formatDecimal(wave.failedTotal)} fallidos`;
  return wave.status === 'COMPLETED' ? 'Completada' : 'Sin fallidos';
}

export function WaveMobileBar({ controller }) {
  const { wave, actions, menuItems } = controller;
  const primary = actions.primary ?? actions.csv;
  if (!primary) return null;
  const menu = actions.primary ? menuItems : menuItems.filter((item) => item.key !== 'csv');
  const { onClick, loading, ...primaryProps } = actionButtonProps(primary, controller);
  return (
    <StickyActionBar
      compact="menu"
      className="md:hidden"
      aria-label="Acciones de la oleada"
      summary={`${formatDecimal(invitedCount(wave))} de ${formatDecimal(wave.invitableCount ?? 0)}`}
      hint={barHint(wave)}
      primary={{
        ...primaryProps,
        label: primary.label,
        loadingLabel: primary.label,
        loading,
        onClick,
        className: 'aria-disabled:cursor-not-allowed aria-disabled:opacity-50',
      }}
      secondary={menu.map((item) => ({
        key: item.key,
        label: item.label,
        icon: item.icon,
        destructive: item.destructive,
        disabled: item.disabled,
        onSelect: () => controller.request(item),
      }))}
    />
  );
}
