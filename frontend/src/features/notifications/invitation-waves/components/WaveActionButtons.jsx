import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { actionButtonProps } from '../lib/invitationWaveActions';

const TOUCH_TARGET = 'min-h-11 md:min-h-8';
const BLOCKED = 'aria-disabled:cursor-not-allowed aria-disabled:border-(--border) aria-disabled:bg-(--disabled-bg) aria-disabled:text-(--disabled-fg)';

function ActionButton({ item, controller, variant }) {
  const Icon = item.icon;
  return (
    <Button
      {...actionButtonProps(item, controller)}
      variant={variant}
      className={cn(TOUCH_TARGET, BLOCKED, item.destructive && 'text-danger')}
    >
      <Icon aria-hidden="true" />
      {item.label}
    </Button>
  );
}

export function WaveActionButtons({ controller }) {
  const { actions, menuItems } = controller;
  if (!actions.primary && menuItems.length === 0) return null;
  return (
    <div className="hidden flex-wrap items-center gap-2 md:flex" role="group" aria-label="Acciones de la oleada">
      {actions.primary && <ActionButton item={actions.primary} controller={controller} variant="primary" />}
      {menuItems.map((item) => (
        <ActionButton key={item.key} item={item} controller={controller} variant="outline" />
      ))}
    </div>
  );
}
