import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { ColumnFilter } from '@/components/table/ColumnFilter';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { NativeSelect } from '@/components/ui/native-select';
import {
  AUDIT_ACTION_CODES,
  AUDIT_ENTITY_CODES,
  AUDIT_ROLE_CODES,
  auditActionLabel,
  auditEntityLabel,
  auditRoleLabel,
} from '@/lib/auditLabels';
import { hasAuditFilters, isAuditRangeInvalid } from '@/features/audit/lib/auditFilters';

const actionOptions = AUDIT_ACTION_CODES.map((value) => ({ value, label: auditActionLabel(value) }));
const roleOptions = AUDIT_ROLE_CODES.map((value) => ({ value, label: auditRoleLabel(value) }));

function MultiFilter({ label, options, selected, onChange }) {
  return (
    <span className="inline-flex items-center gap-1 text-size-13 font-medium text-ink-soft">
      {label}
      <ColumnFilter
        label={label.toLowerCase()}
        options={options}
        selected={selected ?? undefined}
        onChange={(next) => onChange(next ?? null)}
      />
      {selected && <span className="rounded-full bg-primary px-1.5 text-size-11 text-primary-foreground">{selected.length}</span>}
    </span>
  );
}

export function AuditFilters({ filters, onChange, onClear }) {
  const [open, setOpen] = useState(false);
  const set = (patch) => onChange({ ...filters, ...patch });
  const active = hasAuditFilters(filters);

  return (
    <section aria-label="Filtros de auditoría" className="flex flex-col gap-2">
      <Button type="button" variant="outline" className="self-start md:hidden" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        Filtros
        <ChevronDown className="size-4" aria-hidden="true" />
      </Button>
      <div className={`${open ? 'flex' : 'hidden'} flex-col gap-3 md:flex md:flex-row md:flex-wrap md:items-center`}>
        <MultiFilter label="Acción" options={actionOptions} selected={filters.actions} onChange={(actions) => set({ actions })} />
        <MultiFilter label="Rol" options={roleOptions} selected={filters.roles} onChange={(roles) => set({ roles })} />
        <NativeSelect
          className="md:w-56"
          aria-label="Tipo de entidad"
          value={filters.entityType}
          onChange={(event) => set({ entityType: event.target.value, entityId: '' })}
        >
          <option value="">Entidad: todas</option>
          {AUDIT_ENTITY_CODES.map((code) => (
            <option key={code} value={code}>{auditEntityLabel(code)}</option>
          ))}
        </NativeSelect>
        <Input
          className="md:w-32"
          aria-label="ID de entidad"
          placeholder="ID de entidad"
          inputMode="numeric"
          disabled={!filters.entityType}
          value={filters.entityId}
          onChange={(event) => set({ entityId: event.target.value.replace(/\D/g, '') })}
        />
        <Input
          className="md:w-36"
          aria-label="ID de actor"
          placeholder="ID de actor"
          inputMode="numeric"
          value={filters.actorPersonId}
          onChange={(event) => set({ actorPersonId: event.target.value.replace(/\D/g, '') })}
        />
        <div className="flex items-center gap-2 text-ink-faint">
          <Input type="date" aria-label="Desde" className="w-fit" value={filters.from} max={filters.to || undefined} onChange={(event) => set({ from: event.target.value })} />
          a
          <Input type="date" aria-label="Hasta" className="w-fit" value={filters.to} min={filters.from || undefined} onChange={(event) => set({ to: event.target.value })} />
        </div>
        {active && (
          <Button type="button" className="md:ml-auto" onClick={onClear}>
            Limpiar filtros
          </Button>
        )}
      </div>
      {isAuditRangeInvalid(filters) && (
        <p role="alert" className="text-sm font-semibold text-danger">La fecha «Desde» no puede ser posterior a «Hasta».</p>
      )}
    </section>
  );
}
