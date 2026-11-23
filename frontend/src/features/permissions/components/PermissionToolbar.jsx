import { Buscador } from '@/components/Buscador';
import { ChoiceChip } from '@/components/ChoiceChip';
import { Button } from '@/components/ui/button';
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select';
import { moduleLabel } from '@/features/permissions/lib/permissionGrid';

export function PermissionToolbar({ modules, filterableRoles, query, onQueryChange, moduleFilter, onModuleFilterChange, roleFilter, onToggleRole, onClearRoles, allOpen, onToggleAll, shown, total }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2.5">
        <Buscador value={query} onChange={onQueryChange} placeholder="Buscar un permiso, por ejemplo “reembolsos”" ariaLabel="Buscar permisos" className="min-w-60 flex-1 md:max-w-none" />
        <NativeSelect value={moduleFilter} onChange={(event) => onModuleFilterChange(event.target.value)} aria-label="Filtrar por módulo" className="w-auto min-w-48">
          <NativeSelectOption value="">Todos los módulos</NativeSelectOption>
          {modules.map((module) => (
            <NativeSelectOption key={module.module} value={module.module}>
              {moduleLabel(module.module)}
            </NativeSelectOption>
          ))}
        </NativeSelect>
        <Button className="min-h-11 md:min-h-8" type="button" variant="ghost" onClick={onToggleAll}>
          {allOpen ? 'Contraer todo' : 'Expandir todo'}
        </Button>
      </div>
      <div role="group" aria-label="Filtrar por rol" className="flex flex-wrap items-center gap-1.5">
        <span className="text-size-12 text-ink-faint">Mostrar solo:</span>
        {filterableRoles.map((role) => (
          <ChoiceChip key={role.id} selected={roleFilter.includes(role.id)} onClick={() => onToggleRole(role.id)} aria-pressed={roleFilter.includes(role.id)} className="min-h-10">
            {role.label}
          </ChoiceChip>
        ))}
        {roleFilter.length > 0 && (
          <Button className="min-h-11 md:min-h-8" type="button" variant="ghost" size="sm" onClick={onClearRoles}>
            Limpiar
          </Button>
        )}
        <span className="ml-auto text-size-12 tabular-nums text-ink-faint" aria-live="polite">
          {shown} de {total} permisos
        </span>
      </div>
    </div>
  );
}
