import { Check, Lock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { cn } from '@/lib/utils';
import { DIRECTOR_ROLE, roleLabel } from '@/features/permissions/lib/permissionGrid';

export function PermissionCell({ permission, role, granted, changed, onToggle }) {
  const label = `${permission.description} — ${roleLabel(role)}`;
  let control;
  if (role === DIRECTOR_ROLE) {
    control = (
      <span role="img" aria-label={`${label}: el Director siempre tiene este permiso`} title="El Director siempre tiene todos los permisos" className="grid min-h-11 min-w-11 place-items-center text-ink-soft">
        <Check className="size-4" aria-hidden="true" />
      </span>
    );
  } else if (permission.fixedToDirector) {
    control = (
      <Popover>
        <PopoverTrigger asChild>
          <Button type="button" variant="subtle" aria-label={`${label}: bloqueado, solo el Director`} className="size-11">
            <Lock className="size-4" aria-hidden="true" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="text-size-12 text-ink-soft">
          <b className="text-ink">Acción que mueve plata</b>
          Queda fija al Director y no se puede delegar. Si hace falta, el Director la autoriza en cada operación.
        </PopoverContent>
      </Popover>
    );
  } else {
    control = (
      <Button
        type="button"
        variant="ghost"
        role="checkbox"
        aria-checked={granted}
        aria-label={label}
        onClick={() => onToggle(role, permission.code)}
        className="size-11"
      >
        <span
          className={cn(
            'grid size-5.5 place-items-center rounded-md border-[1.5px] transition-colors motion-reduce:transition-none',
            granted ? 'border-brand bg-brand text-on-accent' : 'border-ink-faint bg-surface text-transparent',
          )}
        >
          <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />
        </span>
      </Button>
    );
  }

  return (
    <div className={cn('flex items-center justify-between rounded-control border border-border pl-3 pr-1 md:justify-center md:rounded-none md:border-0 md:px-0 md:self-stretch', changed && 'border-brand bg-accent-soft md:border-0')}>
      <span className="text-size-13 font-semibold text-ink-soft md:hidden">{roleLabel(role)}</span>
      {control}
    </div>
  );
}
