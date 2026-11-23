import { useState } from 'react';
import { DataTable } from '@/components/table/DataTable';
import { PageContainer } from '@/components/PageContainer';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { LoadErrorState } from '@/components/LoadErrorState';
import { Paginador } from '@/components/Paginador';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { auditActionLabel, auditActionTone, auditEntityLabel, auditRoleLabel, auditRoleTone } from '@/features/audit/lib/auditLabels';
import { formatDate } from '@/lib/billing';
import { auditActorName } from '@/features/audit/lib/auditPayload';
import { AuditDetailSheet } from '@/features/audit/components/AuditDetailSheet';
import { AuditFilters } from '@/features/audit/components/AuditFilters';
import { actorSearchFor, actorSearchInput, EMPTY_AUDIT_FILTERS, hasAuditFilters, isAuditRangeInvalid } from '@/features/audit/lib/auditFilters';
import { useAuditLog } from '@/features/audit/hooks/useAuditLog';

const ACTOR_SEARCH_DEBOUNCE_MS = 300;

const timeOf = (value) => (value?.includes('T') ? `${value.split('T')[1].slice(0, 5)} h` : '');

const COLUMNS = [
  {
    id: 'occurredAt',
    header: 'Fecha y hora',
    accessorFn: (event) => event.occurredAt,
    cell: ({ row }) => (
      <span className="whitespace-nowrap tabular-nums">
        {formatDate(row.original.occurredAt)}
        <span className="block text-size-12 text-ink-faint">{timeOf(row.original.occurredAt)}</span>
      </span>
    ),
    enableSorting: false,
  },
  {
    id: 'actor',
    header: 'Actor',
    accessorFn: auditActorName,
    cell: ({ row }) => (
      <span>
        {auditActorName(row.original)}
        {row.original.actorPersonId && <span className="block text-size-12 text-ink-faint tabular-nums">#{row.original.actorPersonId}</span>}
      </span>
    ),
    enableSorting: false,
  },
  {
    id: 'actorRole',
    header: 'Rol',
    accessorFn: (event) => event.actorRole,
    cell: ({ row }) => <StatusPill tone={auditRoleTone(row.original.actorRole)}>{auditRoleLabel(row.original.actorRole)}</StatusPill>,
    enableSorting: false,
  },
  {
    id: 'action',
    header: 'Acción',
    accessorFn: (event) => event.action,
    cell: ({ row }) => <StatusPill tone={auditActionTone(row.original.action)}>{auditActionLabel(row.original.action)}</StatusPill>,
    meta: { cardTitle: true },
    enableSorting: false,
  },
  {
    id: 'entity',
    header: 'Entidad',
    accessorFn: (event) => event.entityType,
    cell: ({ row }) => (
      <span>
        {auditEntityLabel(row.original.entityType)}
        <span className="block text-size-12 text-ink-faint tabular-nums">#{row.original.entityId}</span>
      </span>
    ),
    enableSorting: false,
  },
  {
    id: 'reason',
    header: 'Motivo',
    accessorFn: (event) => event.reason,
    cell: ({ row }) => <span className="block max-w-60 truncate text-ink-soft md:max-w-60">{row.original.reason || '—'}</span>,
    enableSorting: false,
  },
];

export function AuditLogPage({ endpoint, deniedAudience }) {
  const [filters, setFilters] = useState(EMPTY_AUDIT_FILTERS);
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState(null);
  const rangeInvalid = isAuditRangeInvalid(filters);
  const debouncedActor = useDebouncedValue(filters.actor, ACTOR_SEARCH_DEBOUNCE_MS);
  const actorSearch = actorSearchFor(actorSearchInput(filters.actor, debouncedActor));
  const queryFilters = {
    actions: filters.actions,
    roles: filters.roles,
    entityType: filters.entityType,
    entityId: filters.entityId,
    from: rangeInvalid ? '' : filters.from,
    to: rangeInvalid ? '' : filters.to,
    ...actorSearch,
  };
  const query = useAuditLog(endpoint, queryFilters, page);

  const changeFilters = (next) => {
    setFilters(next);
    setPage(1);
  };
  const clearFilters = () => changeFilters(EMPTY_AUDIT_FILTERS);

  if (query.isError && query.error?.status === 403) {
    return (
      <PageContainer title="Auditoría">
        <GlassCard className="flex flex-1 flex-col p-12" role="alert">
          <EmptyState icon="🔒" title="No tenés acceso a esta pantalla" description={`La auditoría está disponible solo para ${deniedAudience}.`} />
        </GlassCard>
      </PageContainer>
    );
  }

  const totalPages = query.data ? Math.max(1, Math.ceil(query.data.totalElements / query.data.size)) : 1;
  const events = query.data?.items ?? [];
  const filtered = hasAuditFilters(filters);
  let body;
  if (query.isLoading) {
    body = (
      <div role="status" aria-busy="true" aria-label="Cargando auditoría" className="flex flex-col gap-2 p-4">
        {Array.from({ length: 7 }, (_, index) => (
          <Skeleton key={index} className="h-12 w-full" />
        ))}
      </div>
    );
  } else if (query.isError) {
    body = <LoadErrorState bare error={query.error} onRetry={query.refetch} resource="la auditoría" />;
  } else if (!events.length) {
    body = filtered ? (
      <EmptyState
        icon="🔎"
        title="Ningún evento coincide"
        description="Probá con otros filtros o quitá alguno para ver más resultados."
        action={<Button className="min-h-11 md:min-h-8" type="button" variant="outline" onClick={clearFilters}>Limpiar filtros</Button>}
      />
    ) : (
      <EmptyState
        icon="🗂️"
        title="Todavía no hay actividad registrada"
        description="Las bajas, cambios de comisión, reseteos de clave e importaciones van a aparecer acá."
      />
    );
  } else {
    body = (
      <>
        <DataTable columns={COLUMNS} data={events} getRowId={(event) => String(event.id)} onRowClick={setSelected} minWidth={880} />
        <p className="px-4 pb-2 text-size-12 text-ink-faint">
          {query.data.totalElements} {query.data.totalElements === 1 ? 'evento' : 'eventos'}
        </p>
        <Paginador page={page} totalPages={totalPages} onChange={setPage} />
      </>
    );
  }

  return (
    <PageContainer title="Auditoría">
      <AuditFilters filters={filters} onChange={changeFilters} onClear={clearFilters} />
      <GlassCard className="p-0">{body}</GlassCard>
      <AuditDetailSheet event={selected} onClose={() => setSelected(null)} />
    </PageContainer>
  );
}
