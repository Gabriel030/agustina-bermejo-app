import { Check, Minus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { DIRECTOR_ROLE, roleLabel } from '@/features/permissions/lib/permissionGrid';

export function PermissionCell({ permission, role, granted, changed, editable, onToggle }) {
  const label = `${permission.description} — ${roleLabel(role)}`;
  let control;
  if (role === DIRECTOR_ROLE) {
    control = (
      <span role="img" aria-label={`${label}: el Director siempre tiene este permiso`} title="El Director siempre tiene todos los permisos" className="grid min-h-11 min-w-11 place-items-center text-ink-soft">
        <Check className="size-4" aria-hidden="true" />
      </span>
    );
  } else if (!editable) {
    control = (
      <span role="img" aria-label={`${label}: ${granted ? 'tiene el permiso' : 'no lo tiene'}`} className={cn('grid min-h-11 min-w-11 place-items-center', granted ? 'text-success' : 'text-ink-faint')}>
        {granted ? <Check className="size-4" aria-hidden="true" /> : <Minus className="size-4" aria-hidden="true" />}
      </span>
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
