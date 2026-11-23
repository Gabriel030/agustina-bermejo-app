import { Ellipsis } from 'lucide-react';
import { StickyActionBar } from '@/components/StickyActionBar';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
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
  const PrimaryIcon = primary.icon;
  return (
    <StickyActionBar
      className="md:hidden"
      role="region"
      aria-label="Acciones de la oleada"
      summary={`${formatDecimal(invitedCount(wave))} de ${formatDecimal(wave.invitableCount ?? 0)}`}
      hint={barHint(wave)}
    >
      <div className="flex items-center gap-2">
        {menu.length > 0 && (
          <DropdownMenu modal={false}>
            <DropdownMenuTrigger asChild>
              <Button type="button" variant="outline" size="icon" className="size-11 shrink-0" aria-label="Más acciones">
                <Ellipsis aria-hidden="true" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" side="top" className="w-auto min-w-56">
              {menu.map((item) => {
                const Icon = item.icon;
                return (
                  <DropdownMenuItem
                    key={item.key}
                    disabled={item.disabled}
                    variant={item.destructive ? 'destructive' : 'default'}
                    onSelect={() => controller.request(item)}
                  >
                    <Icon aria-hidden="true" />
                    {item.label}
                  </DropdownMenuItem>
                );
              })}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
        <Button
          {...actionButtonProps(primary, controller)}
          variant="primary"
          className="min-h-11 aria-disabled:cursor-not-allowed aria-disabled:opacity-50"
        >
          <PrimaryIcon aria-hidden="true" />
          {primary.label}
        </Button>
      </div>
    </StickyActionBar>
  );
}
