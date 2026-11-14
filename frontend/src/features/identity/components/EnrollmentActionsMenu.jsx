import { ArrowLeftRight, Lock, MoreHorizontal, UserMinus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

const ICONS = { 'change-section': ArrowLeftRight };

export function EnrollmentActionsMenu({ actions, label, onSelect }) {
  if (actions.length === 0) return null;
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="ghost" size="icon" aria-label={label}>
          <MoreHorizontal aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72 bg-dialog-bg backdrop-blur-[28px] backdrop-saturate-[1.6]">
        {actions.map((action) => {
          const Icon = action.disabledReason ? Lock : (ICONS[action.kind] ?? UserMinus);
          return (
            <DropdownMenuItem
              key={action.kind}
              disabled={!!action.disabledReason}
              variant={action.destructive ? 'destructive' : 'default'}
              className="flex-col items-start gap-0.5 py-1.5"
              onSelect={() => onSelect(action.kind)}
            >
              <span className="flex items-center gap-2">
                <Icon aria-hidden="true" />
                {action.label}
              </span>
              {action.disabledReason && <span className="pl-6 text-xs font-normal text-ink-faint">{action.disabledReason}</span>}
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
