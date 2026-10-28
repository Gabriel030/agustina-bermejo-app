import { Ban, Lock, MoreHorizontal, Undo2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { GROUP_LABELS, menuEntriesOf } from './paymentReversals';

const ICONS = { chargeback: Undo2, reversal: Undo2, revocation: Ban };

export function PaymentActionsMenu({ payment, isDirector, onSelect }) {
  const entries = menuEntriesOf(payment, isDirector);
  if (entries.length === 0) {
    return (
      <span className="text-ink-faint" aria-label="Sin acciones disponibles">
        —
      </span>
    );
  }
  const groups = [...new Set(entries.map((entry) => entry.group))];

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="icon" aria-label={`Acciones del pago ${payment.id}`}>
          <MoreHorizontal aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72 bg-dialog-bg backdrop-blur-[28px] backdrop-saturate-[1.6]">
        {groups.map((group, index) => (
          <DropdownMenuGroup key={group}>
            {index > 0 && <DropdownMenuSeparator />}
            <DropdownMenuLabel>{GROUP_LABELS[group]}</DropdownMenuLabel>
            {entries
              .filter((entry) => entry.group === group)
              .map((entry) => {
                const Icon = entry.refundBlocked ? Lock : ICONS[entry.kind];
                return (
                  <DropdownMenuItem
                    key={entry.kind}
                    disabled={entry.disabled}
                    variant={entry.kind === 'revocation' ? 'destructive' : 'default'}
                    className="flex-col items-start gap-0.5 py-1.5"
                    onSelect={() => onSelect(entry.kind)}
                  >
                    <span className="flex items-center gap-2">
                      <Icon aria-hidden="true" />
                      {entry.label}
                    </span>
                    {entry.blockedBy && (
                      <span className="pl-6 text-xs font-normal text-ink-faint">
                        {entry.refundBlocked ? 'Primero hay que cerrar los reembolsos pendientes. Tocá para ver cuáles.' : entry.blockedBy.message}
                      </span>
                    )}
                  </DropdownMenuItem>
                );
              })}
          </DropdownMenuGroup>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
