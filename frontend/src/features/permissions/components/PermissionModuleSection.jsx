import { ChevronRight, Lock, Shield } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/StatusPill';
import { cn } from '@/lib/utils';
import { PermissionCell } from '@/features/permissions/components/PermissionCell';
import { DIRECTOR_ROLE, PERMISSION_MODES, modeFor, moduleLabel, roleLabel } from '@/features/permissions/lib/permissionGrid';

export const GRID_COLUMNS = 'md:grid md:grid-cols-[minmax(260px,1fr)_repeat(var(--role-count),108px)] md:items-center';

function ModeIndicator({ mode, role, module }) {
  const config = PERMISSION_MODES[mode];
  return (
    <span title={`${config.description} Cambiarlo estará disponible próximamente.`}>
      <StatusPill tone={config.tone} dot>
        <span className="sr-only">{`Modo de ${roleLabel(role)} en ${moduleLabel(module)}: `}</span>
        {config.label}
      </StatusPill>
    </span>
  );
}

export function PermissionModuleSection({ module, grid, roles, permissions, open, onToggleOpen, draft }) {
  const total = module.permissions.length;
  const fixedCount = module.permissions.filter((permission) => permission.fixedToDirector).length;
  const panelId = `permission-module-${module.module}`;

  return (
    <section className="border-b border-border last:border-b-0" aria-label={moduleLabel(module.module)}>
      <div className={cn(GRID_COLUMNS, open && 'border-b border-border md:sticky md:top-0 md:z-1 md:bg-surface')}>
        <Button
          type="button"
          variant="ghost"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={onToggleOpen}
          className="h-auto min-h-14 w-full justify-start gap-2.5 rounded-none px-3 py-2 text-left whitespace-normal aria-expanded:bg-transparent hover:bg-row-hover"
        >
          <ChevronRight className={cn('size-4 shrink-0 text-ink-faint transition-transform motion-reduce:transition-none', open && 'rotate-90')} aria-hidden="true" />
          <span className="flex min-w-0 flex-col gap-0.5">
            <b className="text-size-14 text-ink">{moduleLabel(module.module)}</b>
            <span className="text-size-12 text-ink-faint">
              {total} permisos
              {permissions.length !== total && ` · ${permissions.length} coinciden`}
              {fixedCount > 0 && ` · ${fixedCount} solo Director`}
            </span>
          </span>
        </Button>
        <div className="flex flex-wrap gap-x-4 gap-y-1.5 pb-3 pl-12 pr-3 md:contents">
          {roles.map((role) => {
            if (role.id === DIRECTOR_ROLE) {
              return (
                <div key={role.id} className="flex items-center gap-1.5 text-size-12 text-ink-faint md:justify-center">
                  <span className="md:hidden">{role.label}</span>
                  <Shield className="size-3.5" aria-hidden="true" /> Todo
                </div>
              );
            }
            const granted = module.permissions.filter((permission) => draft.isGranted(role.id, permission.code)).length;
            const changed = module.permissions.some((permission) => draft.hasChanged(role.id, permission.code));
            return (
              <div key={role.id} className="flex items-center gap-1.5 md:flex-col md:gap-1 md:py-1.5">
                <span className="text-size-12 text-ink-soft md:hidden">{role.label}</span>
                <span className={cn('text-size-13 font-bold tabular-nums', changed && 'text-brand')}>
                  {granted}
                  <small className="font-medium text-ink-faint"> de {total}</small>
                </span>
                <ModeIndicator mode={modeFor(grid, role.id, module.module)} role={role.id} module={module.module} />
              </div>
            );
          })}
        </div>
      </div>
      {open && (
        <div id={panelId}>
          {fixedCount > 0 && (
            <div className="flex items-start gap-2.5 bg-warning-bg px-3.5 py-3 text-size-13 text-warning-ink">
              <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <p>
                Los permisos marcados <b>Solo Director</b> mueven plata: quedan fijos al Director y no se pueden delegar.
              </p>
            </div>
          )}
          {permissions.map((permission) => (
            <div key={permission.code} className={cn('border-t border-border px-3 py-2.5 hover:bg-row-hover md:px-0 md:py-0', GRID_COLUMNS)}>
              <div className="flex min-w-0 flex-col gap-0.5 pb-2 md:py-2 md:pl-11 md:pr-2">
                <span className="flex flex-wrap items-center gap-2 text-size-13 font-semibold text-ink">
                  {permission.description}
                  {permission.fixedToDirector && (
                    <StatusPill tone="warning">
                      <Lock className="size-3" aria-hidden="true" /> Solo Director
                    </StatusPill>
                  )}
                </span>
                <code className="text-size-11 text-ink-faint">{permission.code}</code>
              </div>
              <div className="grid grid-cols-2 gap-1.5 md:contents">
                {roles.map((role) => (
                  <PermissionCell
                    key={role.id}
                    permission={permission}
                    role={role.id}
                    granted={draft.isGranted(role.id, permission.code)}
                    changed={draft.hasChanged(role.id, permission.code)}
                    onToggle={draft.toggle}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
