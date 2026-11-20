import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useActiveRole } from '@/hooks/useActiveRole';
import { roleLabel, rolePath } from '@/lib/activeRole';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

const ITEM_BASE =
  'flex min-h-11 items-center justify-between gap-2 rounded-control px-3 text-sm font-medium text-ink-soft transition-colors hover:bg-neutral-bg hover:text-ink';

function RoleLinks({ choices, activeRole, onNavigate, asMenuItem }) {
  return choices.map((role) => {
    const active = role === activeRole;
    const link = (
      <Link
        to={rolePath(role)}
        aria-current={active ? 'true' : undefined}
        onClick={onNavigate}
        className={cn(!asMenuItem && ITEM_BASE, asMenuItem && 'flex w-full items-center justify-between gap-2', active && 'font-bold text-ink')}
      >
        <span>{roleLabel(role)}</span>
        {active && <Check aria-hidden="true" className="h-4 w-4 shrink-0" />}
      </Link>
    );
    return asMenuItem ? (
      <DropdownMenuItem key={role} asChild className="min-h-10">
        {link}
      </DropdownMenuItem>
    ) : (
      <li key={role}>{link}</li>
    );
  });
}

function MenuSwitcher({ choices, activeRole }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={`Rol activo: ${roleLabel(activeRole)}. Cambiar de rol`}
          className="mt-1 inline-flex min-h-8 items-center gap-1.5 rounded-control border border-border px-2.5 text-size-13 font-semibold text-ink-soft transition-colors hover:bg-neutral-bg hover:text-ink focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <span>{roleLabel(activeRole)}</span>
          <ChevronDown aria-hidden="true" className="h-3.5 w-3.5" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="min-w-44">
        <DropdownMenuLabel>Cambiar de rol</DropdownMenuLabel>
        <RoleLinks choices={choices} activeRole={activeRole} asMenuItem />
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function InlineSwitcher({ choices, activeRole, onNavigate }) {
  const [open, setOpen] = useState(true);
  return (
    <div className="shrink-0">
      <button
        type="button"
        aria-expanded={open}
        aria-controls="role-switcher-list"
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-11 w-full items-center justify-between gap-2 rounded-control px-3 text-sm font-semibold text-ink-soft hover:bg-neutral-bg hover:text-ink"
      >
        <span>Rol activo: {roleLabel(activeRole)}</span>
        <ChevronDown aria-hidden="true" className={cn('h-4 w-4 transition-transform', open && 'rotate-180')} />
      </button>
      {open && (
        <ul id="role-switcher-list" aria-label="Cambiar de rol" className="mt-1 flex flex-col gap-1">
          <RoleLinks choices={choices} activeRole={activeRole} onNavigate={onNavigate} />
        </ul>
      )}
    </div>
  );
}

export function RoleSwitcher({ variant = 'menu', onNavigate }) {
  const { activeRole, choices, canSwitch } = useActiveRole();
  if (!canSwitch) return null;
  if (variant === 'inline') return <InlineSwitcher choices={choices} activeRole={activeRole} onNavigate={onNavigate} />;
  return <MenuSwitcher choices={choices} activeRole={activeRole} />;
}
